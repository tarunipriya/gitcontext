package io.github.tarunipriya.gitcontext.generation.gemini;

import io.github.tarunipriya.gitcontext.config.GitContextProperties;
import io.github.tarunipriya.gitcontext.generation.LlmClient;
import io.github.tarunipriya.gitcontext.generation.LlmRateLimitedException;
import io.github.tarunipriya.gitcontext.generation.LlmRequest;
import io.github.tarunipriya.gitcontext.generation.LlmResponse;
import io.github.tarunipriya.gitcontext.generation.LlmUnavailableException;
import io.github.tarunipriya.gitcontext.generation.gemini.dto.GeminiRequestDto;
import io.github.tarunipriya.gitcontext.generation.gemini.dto.GeminiResponseDto;
import java.io.InterruptedIOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Native REST client for Google Gemini LLM using Spring {@link RestClient}.
 *
 * <p>Dispatches generateContent requests to the Gemini REST API without third-party SDKs,
 * transmitting the API key exclusively via the {@code x-goog-api-key} HTTP header.
 * Implements exponential backoff on HTTP 429 and 5xx responses, honours {@code Retry-After},
 * and automatically fails over to the configured fallback model once if needed.
 */
@Component
public class GeminiClient implements LlmClient {

	private static final Logger log = LoggerFactory.getLogger(GeminiClient.class);
	private static final String API_KEY_HEADER = "x-goog-api-key";

	@FunctionalInterface
	public interface Sleeper {
		void sleep(long millis) throws InterruptedException;
	}

	private final GitContextProperties properties;
	private final RestClient restClient;
	private final Sleeper sleeper;

	@Autowired
	public GeminiClient(GitContextProperties properties, RestClient.Builder restClientBuilder) {
		this(properties, configureTimeouts(restClientBuilder, properties), Thread::sleep);
	}

	public GeminiClient(GitContextProperties properties, RestClient.Builder restClientBuilder, Sleeper sleeper) {
		this.properties = Objects.requireNonNull(properties, "properties must not be null");
		Objects.requireNonNull(restClientBuilder, "restClientBuilder must not be null");
		this.sleeper = Objects.requireNonNull(sleeper, "sleeper must not be null");
		this.restClient = restClientBuilder.build();
	}

	private static RestClient.Builder configureTimeouts(RestClient.Builder builder, GitContextProperties properties) {
		int timeout = properties.llm().timeoutSeconds();
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(Duration.ofSeconds(Math.min(timeout, 30)));
		requestFactory.setReadTimeout(Duration.ofSeconds(timeout));
		return builder.requestFactory(requestFactory);
	}

	@Override
	public LlmResponse generate(LlmRequest request) {
		Objects.requireNonNull(request, "request must not be null");

		if (!properties.hasLlmApiKey()) {
			throw new LlmUnavailableException("Gemini API key not set. Add LLM_API_KEY to the .env file at the repository root.");
		}

		String primaryModel = (request.model() != null && !request.model().isBlank())
				? request.model()
				: properties.llm().model();
		boolean allowFallback = (request.model() == null || request.model().isBlank());
		String fallbackModel = properties.llm().fallbackModel();
		int maxRetries = properties.llm().maxRetries();

		GeminiRequestDto primaryRequestDto = buildRequestDto(primaryModel, request.systemInstruction(), request.userPrompt(), request.temperature());

		if (log.isDebugEnabled()) {
			String promptPreview = request.userPrompt();
			if (promptPreview.length() > 200) {
				promptPreview = promptPreview.substring(0, 200) + "... [truncated]";
			}
			log.debug("Dispatching Gemini generateContent request to model '{}' (prompt preview: '{}')", primaryModel, promptPreview);
		}

		long start = System.currentTimeMillis();

		// Try primary model with retries
		try {
			GeminiResponseDto responseDto = executeWithRetries(primaryModel, primaryRequestDto, maxRetries);
			long latency = System.currentTimeMillis() - start;
			if (latency > 20_000L) {
				log.warn("Gemini generation took {} ms (> 20 s) for model '{}'", latency, primaryModel);
			}
			return buildResponse(responseDto, primaryModel, latency);
		} catch (LlmRateLimitedException | ModelExecutionException ex) {
			// If primary model failed with 429, 5xx, or 404, attempt fallback model once if fallback is allowed
			if (allowFallback && fallbackModel != null && !fallbackModel.isBlank() && !fallbackModel.equalsIgnoreCase(primaryModel)) {
				log.warn("Primary model '{}' execution failed ({}); attempting fallback model '{}'...",
						primaryModel, ex.getMessage(), fallbackModel);
				try {
					GeminiRequestDto fallbackRequestDto = buildRequestDto(fallbackModel, request.systemInstruction(), request.userPrompt(), request.temperature());
					GeminiResponseDto fallbackDto = executeWithRetries(fallbackModel, fallbackRequestDto, 1);
					long latency = System.currentTimeMillis() - start;
					if (latency > 20_000L) {
						log.warn("Gemini generation took {} ms (> 20 s) for fallback model '{}'", latency, fallbackModel);
					}
					return buildResponse(fallbackDto, fallbackModel, latency);
				} catch (Exception fallbackEx) {
					log.error("Fallback model '{}' call also failed: {}", fallbackModel, fallbackEx.getMessage());
					if (isNotFound(fallbackEx)) {
						throw new LlmUnavailableException(
								"Gemini fallback model '" + fallbackModel + "' is not available (HTTP 404). Set a different LLM fallback in config (gitcontext.llm.fallback-model).",
								fallbackEx
						);
					}
					mapAndRethrow(fallbackEx, fallbackModel, maxRetries);
				}
			}
			mapAndRethrow(ex, primaryModel, maxRetries);
			throw new IllegalStateException("Unreachable code after error mapping");
		} catch (Exception ex) {
			mapAndRethrow(ex, primaryModel, maxRetries);
			throw new IllegalStateException("Unreachable code after error mapping");
		}
	}

	private GeminiRequestDto buildRequestDto(String model, String systemInstruction, String userPrompt, Double temperatureOverride) {
		double temperature = (temperatureOverride != null) ? temperatureOverride : properties.llm().temperature();
		int maxOutputTokens = properties.llm().maxOutputTokens();
		GeminiRequestDto.GeminiThinkingConfigDto thinkingConfig = determineThinkingConfig(model);
		return GeminiRequestDto.of(systemInstruction, userPrompt, temperature, maxOutputTokens, thinkingConfig);
	}

	private GeminiRequestDto.GeminiThinkingConfigDto determineThinkingConfig(String model) {
		String level = properties.llm().thinkingLevel();
		if (level == null || level.isBlank() || level.equalsIgnoreCase("none") || level.equalsIgnoreCase("off")) {
			return null;
		}
		String lowerModel = model.toLowerCase();
		if (lowerModel.contains("gemini-2.5")) {
			return GeminiRequestDto.GeminiThinkingConfigDto.ofBudget(mapLevelToBudget(level));
		} else {
			return GeminiRequestDto.GeminiThinkingConfigDto.ofLevel(level.toLowerCase());
		}
	}

	private int mapLevelToBudget(String level) {
		return switch (level.toLowerCase()) {
			case "minimal" -> 512;
			case "low" -> 1024;
			case "medium" -> 2048;
			case "high" -> 4096;
			default -> 1024;
		};
	}

	private GeminiResponseDto executeWithRetries(String model, GeminiRequestDto requestDto, int maxRetries) {
		int attempts = 0;
		long backoffMs = 1000L;

		while (true) {
			attempts++;
			try {
				return executeSingleCall(model, requestDto);
			} catch (HttpClientErrorException ex) {
				HttpStatusCode status = ex.getStatusCode();
				if (status.value() == 429) {
					Long retryAfter = extractRetryAfterSeconds(ex.getResponseHeaders());
					if (attempts <= maxRetries) {
						long sleepTime = (retryAfter != null && retryAfter > 0) ? (retryAfter * 1000L) : backoffMs;
						log.warn("Gemini API rate limited (429) for model '{}' on attempt {}/{}. Backing off for {} ms...",
								model, attempts, maxRetries, sleepTime);
						sleep(sleepTime);
						backoffMs *= 2;
						continue;
					}
					throw new LlmRateLimitedException(
							"Gemini API rate limit exceeded (HTTP 429) after " + maxRetries + " retries for model '" + model + "'.",
							retryAfter,
							ex
					);
				}
				if (status.value() == 404) {
					throw new ModelExecutionException("Model not found: " + model, 404, ex);
				}
				if (status.value() == 400) {
					// If model rejects thinking configuration, omit it and retry
					if (requestDto.generationConfig() != null && requestDto.generationConfig().thinkingConfig() != null) {
						log.warn("Gemini model '{}' rejected thinking configuration (HTTP 400); retrying without thinkingConfig...", model);
						requestDto = new GeminiRequestDto(
								requestDto.systemInstruction(),
								requestDto.contents(),
								new GeminiRequestDto.GeminiGenerationConfigDto(
										requestDto.generationConfig().temperature(),
										requestDto.generationConfig().maxOutputTokens(),
										null
								)
						);
						continue;
					}
					throw new LlmUnavailableException("Gemini API request rejected (HTTP 400): " + ex.getResponseBodyAsString()
							+ ". Verify LLM_API_KEY and model configuration.", ex);
				}
				if (status.value() == 401 || status.value() == 403) {
					throw new LlmUnavailableException("Gemini API request rejected (HTTP " + status.value()
							+ "): " + ex.getResponseBodyAsString() + ". Verify LLM_API_KEY and model configuration.", ex);
				}
				throw ex;
			} catch (HttpServerErrorException ex) {
				if (attempts <= maxRetries) {
					log.warn("Gemini API server error ({}) on attempt {}/{}. Backing off for {} ms...",
							ex.getStatusCode(), attempts, maxRetries, backoffMs);
					sleep(backoffMs);
					backoffMs *= 2;
					continue;
				}
				throw new ModelExecutionException("Gemini API server error (" + ex.getStatusCode() + ") after "
						+ maxRetries + " retries.", ex.getStatusCode().value(), ex);
			} catch (RestClientException ex) {
				if (isTimeout(ex)) {
					if (attempts <= maxRetries) {
						log.warn("Gemini API request timed out for model '{}' on attempt {}/{}. Backing off for {} ms...",
								model, attempts, maxRetries, backoffMs);
						sleep(backoffMs);
						backoffMs *= 2;
						continue;
					}
					throw new ModelExecutionException("Gemini API request timed out after "
							+ maxRetries + " retries.", 503, ex);
				}
				throw ex;
			}
		}
	}

	private GeminiResponseDto executeSingleCall(String model, GeminiRequestDto requestDto) {
		String baseUrl = properties.llm().baseUrl();
		URI uri = URI.create(baseUrl + "/models/" + model + ":generateContent");

		return restClient.post()
				.uri(uri)
				.contentType(MediaType.APPLICATION_JSON)
				.header(API_KEY_HEADER, properties.llmApiKey())
				.body(requestDto)
				.retrieve()
				.body(GeminiResponseDto.class);
	}

	private LlmResponse buildResponse(GeminiResponseDto responseDto, String model, long latency) {
		if (responseDto == null) {
			return new LlmResponse("", model, 0, 0, null, "EMPTY", latency);
		}

		String text = responseDto.firstCandidateText();
		String finishReason = responseDto.firstFinishReason();
		if (finishReason != null && finishReason.equalsIgnoreCase("SAFETY") && (text == null || text.isBlank())) {
			text = "I cannot complete this request because it was blocked by safety filters.";
		}

		return new LlmResponse(
				text != null ? text : "",
				model,
				responseDto.promptTokens(),
				responseDto.outputTokens(),
				responseDto.thoughtsTokens(),
				finishReason != null ? finishReason : "STOP",
				latency
		);
	}

	private void mapAndRethrow(Throwable ex, String model, int maxRetries) {
		if (ex instanceof LlmUnavailableException unavail) {
			throw unavail;
		}
		if (ex instanceof LlmRateLimitedException rateLimit) {
			throw rateLimit;
		}
		if (isNotFound(ex)) {
			String fallbackModel = properties.llm().fallbackModel();
			if (model.equalsIgnoreCase(fallbackModel)) {
				throw new LlmUnavailableException(
						"Gemini fallback model '" + model + "' is not available (HTTP 404). Set a different LLM fallback in config (gitcontext.llm.fallback-model).",
						ex
				);
			} else {
				throw new LlmUnavailableException(
						"Gemini model '" + model + "' is not available (HTTP 404). Set a different LLM model in config (gitcontext.llm.model).",
						ex
				);
			}
		}
		if (ex instanceof RestClientResponseException respEx) {
			int status = respEx.getStatusCode().value();
			if (status == 429) {
				Long retryAfter = extractRetryAfterSeconds(respEx.getResponseHeaders());
				throw new LlmRateLimitedException("Gemini API rate limit exceeded (HTTP 429) for model '" + model + "'.", retryAfter, respEx);
			}
			if (status == 400 || status == 401 || status == 403) {
				throw new LlmUnavailableException("Gemini API request rejected (HTTP " + status + "): "
						+ respEx.getResponseBodyAsString() + ". Verify LLM_API_KEY and model configuration.", respEx);
			}
		}
		throw new LlmUnavailableException("Gemini API call failed for model '" + model + "': " + ex.getMessage(), ex);
	}

	private boolean isNotFound(Throwable ex) {
		if (ex instanceof ModelExecutionException mex && mex.statusCode() == 404) {
			return true;
		}
		if (ex instanceof HttpClientErrorException.NotFound) {
			return true;
		}
		if (ex instanceof RestClientResponseException rre && rre.getStatusCode().value() == 404) {
			return true;
		}
		if (ex != null && ex.getCause() != null && isNotFound(ex.getCause())) {
			return true;
		}
		return false;
	}

	private boolean isTimeout(Throwable ex) {
		Throwable current = ex;
		while (current != null) {
			if (current instanceof SocketTimeoutException
					|| current instanceof ConnectException
					|| current instanceof HttpTimeoutException) {
				return true;
			}
			if (current instanceof InterruptedIOException) {
				String msg = current.getMessage();
				if (msg != null && (msg.toLowerCase().contains("timed out") || msg.toLowerCase().contains("timeout"))) {
					return true;
				}
			}
			String msg = current.getMessage();
			if (msg != null) {
				String lower = msg.toLowerCase();
				if (lower.contains("timed out") || lower.contains("connect timeout") || lower.contains("read timeout") || lower.contains("connection timeout")) {
					return true;
				}
			}
			current = current.getCause();
		}
		return false;
	}

	private Long extractRetryAfterSeconds(HttpHeaders headers) {
		if (headers == null) {
			return null;
		}
		String value = headers.getFirst(HttpHeaders.RETRY_AFTER);
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			return Long.parseLong(value.trim());
		} catch (NumberFormatException ignored) {
			// Handle HTTP-date format if present
			try {
				long epochSeconds = DateTimeFormatter.RFC_1123_DATE_TIME.parse(value.trim()).getLong(ChronoField.INSTANT_SECONDS);
				long nowSeconds = System.currentTimeMillis() / 1000L;
				return Math.max(0L, epochSeconds - nowSeconds);
			} catch (DateTimeParseException dateEx) {
				return null;
			}
		}
	}

	private void sleep(long millis) {
		try {
			sleeper.sleep(millis);
		} catch (InterruptedException ie) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrupted while waiting to retry Gemini API request", ie);
		}
	}

	private static final class ModelExecutionException extends RuntimeException {
		private final int statusCode;

		private ModelExecutionException(String message, int statusCode, Throwable cause) {
			super(message, cause);
			this.statusCode = statusCode;
		}

		public int statusCode() {
			return statusCode;
		}
	}
}
