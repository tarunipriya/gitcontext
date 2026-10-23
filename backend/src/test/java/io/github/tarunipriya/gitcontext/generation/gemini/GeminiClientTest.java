package io.github.tarunipriya.gitcontext.generation.gemini;

import io.github.tarunipriya.gitcontext.config.GitContextProperties;
import io.github.tarunipriya.gitcontext.generation.LlmRateLimitedException;
import io.github.tarunipriya.gitcontext.generation.LlmRequest;
import io.github.tarunipriya.gitcontext.generation.LlmResponse;
import io.github.tarunipriya.gitcontext.generation.LlmUnavailableException;
import java.net.SocketTimeoutException;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GeminiClientTest {

	private MockRestServiceServer mockServer;
	private GeminiClient geminiClient;
	private GitContextProperties properties;
	private AtomicInteger sleepCallCount;

	@BeforeEach
	void setUp() {
		GitContextProperties.LlmProperties llmProps = new GitContextProperties.LlmProperties(
				"gemini",
				"gemini-3.8-flash",
				"gemini-3.5-flash",
				"https://generativelanguage.googleapis.com/v1beta",
				0.2,
				2048,
				60,
				2,
				"low"
		);
		properties = new GitContextProperties(
				null,
				Path.of("./data/repos"),
				"http://localhost:6333",
				"test-gemini-key",
				null,
				null,
				null,
				null,
				null,
				null,
				null,
				null,
				llmProps
		);

		RestClient.Builder builder = RestClient.builder();
		mockServer = MockRestServiceServer.bindTo(builder).build();
		sleepCallCount = new AtomicInteger();
		geminiClient = new GeminiClient(properties, builder, millis -> sleepCallCount.incrementAndGet());
	}

	@Test
	@DisplayName("generate sends valid JSON shape, key in header only, and parses usage")
	void generate_validRequest_sendsJsonBodyAndKeyHeaderNotUrl() {
		String responseJson = """
				{
				  "candidates": [
				    {
				      "content": {
				        "parts": [
				          { "text": "This is a grounded answer." }
				        ],
				        "role": "model"
				      },
				      "finishReason": "STOP",
				      "index": 0
				    }
				  ],
				  "usageMetadata": {
				    "promptTokenCount": 85,
				    "candidatesTokenCount": 18,
				    "totalTokenCount": 103,
				    "thoughtsTokenCount": 15
				  },
				  "modelVersion": "gemini-3.8-flash-001"
				}
				""";

		mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent"))
				.andExpect(method(HttpMethod.POST))
				.andExpect(header("x-goog-api-key", "test-gemini-key"))
				.andExpect(jsonPath("$.systemInstruction.parts[0].text").value("system prompt"))
				.andExpect(jsonPath("$.contents[0].parts[0].text").value("user question"))
				.andExpect(jsonPath("$.generationConfig.temperature").value(0.2))
				.andExpect(jsonPath("$.generationConfig.maxOutputTokens").value(2048))
				.andExpect(jsonPath("$.generationConfig.thinkingConfig.thinkingLevel").value("low"))
				.andRespond(withSuccess(responseJson, MediaType.APPLICATION_JSON));

		LlmResponse response = geminiClient.generate(new LlmRequest("system prompt", "user question"));

		mockServer.verify();
		assertThat(response.text()).isEqualTo("This is a grounded answer.");
		assertThat(response.model()).isEqualTo("gemini-3.8-flash");
		assertThat(response.promptTokens()).isEqualTo(85);
		assertThat(response.outputTokens()).isEqualTo(18);
		assertThat(response.thoughtsTokens()).isEqualTo(15);
		assertThat(response.finishReason()).isEqualTo("STOP");
		assertThat(response.latencyMillis()).isGreaterThanOrEqualTo(0L);
	}

	@Test
	@DisplayName("generate uses temperature override from request when set, and config value when null")
	void generate_temperatureOverride_honorsRequestWhenSetAndConfigWhenNull() {
		String responseJson = """
				{
				  "candidates": [
				    {
				      "content": {
				        "parts": [
				          { "text": "Response text." }
				        ],
				        "role": "model"
				      },
				      "finishReason": "STOP",
				      "index": 0
				    }
				  ]
				}
				""";

		// 1. Explicit temperature override (e.g. 0.0 for judge)
		mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent"))
				.andExpect(method(HttpMethod.POST))
				.andExpect(jsonPath("$.generationConfig.temperature").value(0.0))
				.andRespond(withSuccess(responseJson, MediaType.APPLICATION_JSON));

		geminiClient.generate(new LlmRequest("judge system", "judge prompt", 0.0));
		mockServer.verify();

		mockServer.reset();

		// 2. Null temperature uses config value (0.2)
		mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent"))
				.andExpect(method(HttpMethod.POST))
				.andExpect(jsonPath("$.generationConfig.temperature").value(0.2))
				.andRespond(withSuccess(responseJson, MediaType.APPLICATION_JSON));

		geminiClient.generate(new LlmRequest("system prompt", "user question"));
		mockServer.verify();
	}

	@Test
	@DisplayName("generate throws LlmUnavailableException with clear message when key is missing")
	void generate_missingApiKey_throwsLlmUnavailableException() {
		GitContextProperties noKeyProps = new GitContextProperties(
				null,
				Path.of("./data/repos"),
				"http://localhost:6333",
				null,
				null,
				null,
				null,
				null,
				null,
				null,
				null,
				null,
				properties.llm()
		);
		GeminiClient clientWithoutKey = new GeminiClient(noKeyProps, RestClient.builder(), millis -> {});

		assertThatThrownBy(() -> clientWithoutKey.generate(new LlmRequest("sys", "user")))
				.isInstanceOf(LlmUnavailableException.class)
				.hasMessageContaining("Gemini API key not set. Add LLM_API_KEY to the .env file at the repository root.");
	}

	@Test
	@DisplayName("generate retries on 429 rate limit with backoff and succeeds on subsequent try")
	void generate_rateLimit429_retriesAndSucceeds() {
		String successJson = """
				{
				  "candidates": [
				    {
				      "content": {
				        "parts": [
				          { "text": "Success after retry" }
				        ]
				      },
				      "finishReason": "STOP"
				    }
				  ]
				}
				""";

		HttpHeaders retryHeaders = new HttpHeaders();
		retryHeaders.set(HttpHeaders.RETRY_AFTER, "2");

		// 1st request -> 429
		mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent"))
				.andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).headers(retryHeaders).body("Rate limited"));

		// 2nd request -> 200 OK
		mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent"))
				.andRespond(withSuccess(successJson, MediaType.APPLICATION_JSON));

		LlmResponse response = geminiClient.generate(new LlmRequest(null, "hello"));

		mockServer.verify();
		assertThat(response.text()).isEqualTo("Success after retry");
		assertThat(sleepCallCount.get()).isEqualTo(1);
	}

	@Test
	@DisplayName("generate falls back to fallbackModel once if primary model repeatedly fails with 429/5xx/404")
	void generate_primaryFails_attemptsFallbackModel() {
		String fallbackSuccess = """
				{
				  "candidates": [
				    {
				      "content": {
				        "parts": [
				          { "text": "Answer from fallback model" }
				        ]
				      },
				      "finishReason": "STOP"
				    }
				  ]
				}
				""";

		// Primary model fails with 404 (not found)
		mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent"))
				.andRespond(withStatus(HttpStatus.NOT_FOUND).body("Model not found"));

		// Fallback model gemini-3.5-flash succeeds and uses thinkingLevel low
		mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent"))
				.andExpect(jsonPath("$.generationConfig.thinkingConfig.thinkingLevel").value("low"))
				.andRespond(withSuccess(fallbackSuccess, MediaType.APPLICATION_JSON));

		LlmResponse response = geminiClient.generate(new LlmRequest(null, "query"));

		mockServer.verify();
		assertThat(response.text()).isEqualTo("Answer from fallback model");
		assertThat(response.model()).isEqualTo("gemini-3.5-flash");
	}

	@Test
	@DisplayName("generate throws clear LlmUnavailableException when fallback model returns 404")
	void generate_fallbackReturns404_throwsClearLlmUnavailableException() {
		// Primary model fails (HTTP 503 x 3 attempts: initial + 2 retries)
		mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent"))
				.andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE).body("Service unavailable"));
		mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent"))
				.andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE).body("Service unavailable"));
		mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent"))
				.andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE).body("Service unavailable"));

		// Fallback model returns 404 Not Found
		mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent"))
				.andRespond(withStatus(HttpStatus.NOT_FOUND).body("Model not found: gemini-3.5-flash"));

		assertThatThrownBy(() -> geminiClient.generate(new LlmRequest(null, "query")))
				.isInstanceOf(LlmUnavailableException.class)
				.hasMessageContaining("gemini-3.5-flash")
				.hasMessageContaining("404")
				.hasMessageContaining("fallback")
				.hasMessageContaining("gitcontext.llm.fallback-model");
	}

	@Test
	@DisplayName("generate sends thinkingConfig with thinkingLevel low for gemini-3.8-flash")
	void generate_thinkingConfigPresentInRequest_forGemini38Flash() {
		String responseJson = """
				{
				  "candidates": [
				    {
				      "content": {
				        "parts": [{ "text": "Answer with thinking" }]
				      },
				      "finishReason": "STOP"
				    }
				  ],
				  "usageMetadata": {
				    "promptTokenCount": 50,
				    "candidatesTokenCount": 20,
				    "totalTokenCount": 70,
				    "thoughtsTokenCount": 10
				  }
				}
				""";

		mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent"))
				.andExpect(method(HttpMethod.POST))
				.andExpect(jsonPath("$.generationConfig.thinkingConfig").exists())
				.andExpect(jsonPath("$.generationConfig.thinkingConfig.thinkingLevel").value("low"))
				.andExpect(jsonPath("$.generationConfig.thinkingConfig.thinkingBudget").doesNotExist())
				.andRespond(withSuccess(responseJson, MediaType.APPLICATION_JSON));

		LlmResponse response = geminiClient.generate(new LlmRequest("sys", "user question"));

		mockServer.verify();
		assertThat(response.thoughtsTokens()).isEqualTo(10);
	}

	@Test
	@DisplayName("generate retries without thinkingConfig if model rejects thinking with HTTP 400")
	void generate_thinkingRejectedWith400_retriesWithoutThinkingConfig() {
		String successJson = """
				{
				  "candidates": [
				    {
				      "content": {
				        "parts": [
				          { "text": "Success without thinking" }
				        ]
				      },
				      "finishReason": "STOP"
				    }
				  ]
				}
				""";

		// 1st request with thinkingConfig rejected with 400
		mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent"))
				.andExpect(jsonPath("$.generationConfig.thinkingConfig").exists())
				.andRespond(withStatus(HttpStatus.BAD_REQUEST).body("Unknown field thinkingConfig"));

		// 2nd request without thinkingConfig succeeds
		mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent"))
				.andExpect(jsonPath("$.generationConfig.thinkingConfig").doesNotExist())
				.andRespond(withSuccess(successJson, MediaType.APPLICATION_JSON));

		LlmResponse response = geminiClient.generate(new LlmRequest(null, "query"));

		mockServer.verify();
		assertThat(response.text()).isEqualTo("Success without thinking");
	}

	@Test
	@DisplayName("generate maps 400 Bad Request to LlmUnavailableException with details when persistent")
	void generate_clientError400_mappedToLlmUnavailableException() {
		// Both with and without thinkingConfig fail with 400
		mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent"))
				.andRespond(withStatus(HttpStatus.BAD_REQUEST).body("Invalid argument in request"));
		mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent"))
				.andRespond(withStatus(HttpStatus.BAD_REQUEST).body("Invalid argument in request"));

		assertThatThrownBy(() -> geminiClient.generate(new LlmRequest(null, "query")))
				.isInstanceOf(LlmUnavailableException.class)
				.hasMessageContaining("Gemini API request rejected (HTTP 400)")
				.hasMessageContaining("Invalid argument");
	}

	@Test
	@DisplayName("generate maps 403 Forbidden to LlmUnavailableException with details")
	void generate_clientError403_mappedToLlmUnavailableException() {
		mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent"))
				.andRespond(withStatus(HttpStatus.FORBIDDEN).body("API_KEY_INVALID"));

		assertThatThrownBy(() -> geminiClient.generate(new LlmRequest(null, "query")))
				.isInstanceOf(LlmUnavailableException.class)
				.hasMessageContaining("Gemini API request rejected (HTTP 403)")
				.hasMessageContaining("API_KEY_INVALID");
	}

	@Test
	@DisplayName("generate handles SAFETY blocked candidate response without NullPointerException")
	void generate_safetyBlocked_handledGracefully() {
		String safetyJson = """
				{
				  "candidates": [
				    {
				      "finishReason": "SAFETY",
				      "index": 0
				    }
				  ],
				  "usageMetadata": {
				    "promptTokenCount": 50,
				    "candidatesTokenCount": 0
				  }
				}
				""";

		mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent"))
				.andRespond(withSuccess(safetyJson, MediaType.APPLICATION_JSON));

		LlmResponse response = geminiClient.generate(new LlmRequest(null, "unsafe query"));

		mockServer.verify();
		assertThat(response.finishReason()).isEqualTo("SAFETY");
		assertThat(response.text()).contains("safety filters");
		assertThat(response.outputTokens()).isEqualTo(0);
	}

	@Test
	@DisplayName("generate handles empty candidates response without throwing NPE")
	void generate_emptyCandidates_handledGracefully() {
		String emptyJson = """
				{
				  "candidates": []
				}
				""";

		mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent"))
				.andRespond(withSuccess(emptyJson, MediaType.APPLICATION_JSON));

		LlmResponse response = geminiClient.generate(new LlmRequest(null, "query"));

		mockServer.verify();
		assertThat(response.text()).isEmpty();
	}

	@Test
	@DisplayName("generate retries on SocketTimeoutException with backoff and succeeds on subsequent try")
	void generate_timeout_retriesAndSucceeds() {
		String successJson = """
				{
				  "candidates": [
				    {
				      "content": {
				        "parts": [
				          { "text": "Success after timeout" }
				        ]
				      },
				      "finishReason": "STOP"
				    }
				  ]
				}
				""";

		// 1st request -> SocketTimeoutException
		mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent"))
				.andRespond(withException(new SocketTimeoutException("Read timed out")));

		// 2nd request -> 200 OK
		mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent"))
				.andRespond(withSuccess(successJson, MediaType.APPLICATION_JSON));

		LlmResponse response = geminiClient.generate(new LlmRequest(null, "hello"));

		mockServer.verify();
		assertThat(response.text()).isEqualTo("Success after timeout");
		assertThat(sleepCallCount.get()).isEqualTo(1);
	}

	@Test
	@DisplayName("generate falls back to fallbackModel when primary model repeatedly times out")
	void generate_primaryTimesOutRepeatedly_attemptsFallbackModel() {
		String fallbackSuccess = """
				{
				  "candidates": [
				    {
				      "content": {
				        "parts": [
				          { "text": "Answer from fallback model after timeout" }
				        ]
				      },
				      "finishReason": "STOP"
				    }
				  ]
				}
				""";

		// Primary model times out 3 times (initial + 2 retries)
		mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent"))
				.andRespond(withException(new SocketTimeoutException("Read timed out")));
		mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent"))
				.andRespond(withException(new SocketTimeoutException("Read timed out")));
		mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent"))
				.andRespond(withException(new SocketTimeoutException("Read timed out")));

		// Fallback model succeeds
		mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent"))
				.andRespond(withSuccess(fallbackSuccess, MediaType.APPLICATION_JSON));

		LlmResponse response = geminiClient.generate(new LlmRequest(null, "query"));

		mockServer.verify();
		assertThat(response.text()).isEqualTo("Answer from fallback model after timeout");
		assertThat(response.model()).isEqualTo("gemini-3.5-flash");
		assertThat(sleepCallCount.get()).isEqualTo(2);
	}

	@Test
	@DisplayName("generate does not attempt fallback when explicit model override is provided in request")
	void generate_withExplicitModel_doesNotFallBackOnFailure() {
		mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent"))
				.andRespond(withException(new SocketTimeoutException("Read timed out")));
		mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent"))
				.andRespond(withException(new SocketTimeoutException("Read timed out")));
		mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent"))
				.andRespond(withException(new SocketTimeoutException("Read timed out")));

		LlmRequest request = new LlmRequest(null, "query", null, "gemini-3.8-flash");

		assertThatThrownBy(() -> geminiClient.generate(request))
				.isInstanceOf(LlmUnavailableException.class)
				.hasMessageContaining("Gemini API request timed out");

		mockServer.verify();
		assertThat(sleepCallCount.get()).isEqualTo(2);
	}

	@Test
	@DisplayName("generate does not attempt fallback when explicit model fails with 429")
	void generate_withExplicitModel_doesNotFallBackOn429() {
		mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent"))
				.andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).body("Rate limited").contentType(MediaType.APPLICATION_JSON));
		mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent"))
				.andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).body("Rate limited").contentType(MediaType.APPLICATION_JSON));
		mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent"))
				.andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).body("Rate limited").contentType(MediaType.APPLICATION_JSON));

		LlmRequest request = new LlmRequest(null, "query", null, "gemini-3.8-flash");

		assertThatThrownBy(() -> geminiClient.generate(request))
				.isInstanceOf(LlmRateLimitedException.class)
				.hasMessageContaining("Gemini API rate limit exceeded");

		mockServer.verify();
	}
}
