package io.github.tarunipriya.gitcontext.evaluation.answer;

import io.github.tarunipriya.gitcontext.config.GitContextProperties;
import io.github.tarunipriya.gitcontext.generation.LlmClient;
import io.github.tarunipriya.gitcontext.generation.LlmRequest;
import io.github.tarunipriya.gitcontext.generation.LlmResponse;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Impartial LLM judge that evaluates generated answers against expected ground-truth facts
 * and verifies groundedness against cited source excerpts.
 * <p>
 * Uses temperature 0 and enforces strict JSON output. Discards invalid JSON or mismatched fact counts
 * with a warning log, returning {@link Optional#empty()} without throwing runtime exceptions.
 */
@Component
public class AnswerJudge {

	private static final Logger log = LoggerFactory.getLogger(AnswerJudge.class);
	private static final String JUDGE_PROMPT_PATH = "prompts/judge-system.txt";
	private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

	private final LlmClient llmClient;
	private final GitContextProperties properties;
	private final String systemInstruction;

	@Autowired
	public AnswerJudge(LlmClient llmClient, GitContextProperties properties) {
		this.llmClient = Objects.requireNonNull(llmClient, "llmClient must not be null");
		this.properties = properties;
		this.systemInstruction = loadJudgePrompt();
	}

	public AnswerJudge(LlmClient llmClient) {
		this(llmClient, null);
	}

	/**
	 * Evaluates the generated answer against expected facts and cited sources using configured judge model.
	 *
	 * @param question natural language developer question
	 * @param expectedFacts list of atomic ground truth facts expected in the answer
	 * @param answer generated answer text to evaluate
	 * @param citedSourcesText text excerpts of the sources cited by the answer
	 * @return verdict if evaluation and parsing succeed, or empty if JSON is invalid or fact count mismatches
	 */
	public Optional<JudgeVerdict> judge(
			String question,
			List<String> expectedFacts,
			String answer,
			String citedSourcesText
	) {
		return judge(question, expectedFacts, answer, citedSourcesText, resolveJudgeModel());
	}

	/**
	 * Evaluates the generated answer against expected facts and cited sources with an optional judge model override.
	 *
	 * @param question natural language developer question
	 * @param expectedFacts list of atomic ground truth facts expected in the answer
	 * @param answer generated answer text to evaluate
	 * @param citedSourcesText text excerpts of the sources cited by the answer
	 * @param judgeModelOverride optional model override for judge LLM call
	 * @return verdict if evaluation and parsing succeed, or empty if JSON is invalid or fact count mismatches
	 */
	public Optional<JudgeVerdict> judge(
			String question,
			List<String> expectedFacts,
			String answer,
			String citedSourcesText,
			String judgeModelOverride
	) {
		if (expectedFacts == null || expectedFacts.isEmpty()) {
			log.warn("Cannot judge answer: expectedFacts list is null or empty");
			return Optional.empty();
		}

		String userPrompt = buildUserPrompt(
				question != null ? question : "",
				expectedFacts,
				answer != null ? answer : "",
				citedSourcesText != null ? citedSourcesText : ""
		);

		String model = (judgeModelOverride != null && !judgeModelOverride.isBlank())
				? judgeModelOverride
				: resolveJudgeModel();
		LlmRequest request = new LlmRequest(systemInstruction, userPrompt, 0.0, model);

		LlmResponse response;
		try {
			response = llmClient.generate(request);
		} catch (Exception ex) {
			log.warn("LLM judge call failed with exception: {}", ex.getMessage());
			return Optional.empty();
		}

		if (response == null || response.text() == null || response.text().isBlank()) {
			log.warn("LLM judge returned empty response");
			return Optional.empty();
		}

		return parseVerdict(response.text(), expectedFacts.size());
	}

	private String resolveJudgeModel() {
		if (properties == null) {
			return null;
		}
		if (properties.answerEval() != null && properties.answerEval().judgeModel() != null && !properties.answerEval().judgeModel().isBlank()) {
			return properties.answerEval().judgeModel();
		}
		return (properties.llm() != null) ? properties.llm().model() : null;
	}

	private String buildUserPrompt(
			String question,
			List<String> expectedFacts,
			String answer,
			String citedSourcesText
	) {
		StringBuilder sb = new StringBuilder();
		sb.append("Question:\n").append(question.trim()).append("\n\n");

		sb.append("Expected Facts:\n");
		for (int i = 0; i < expectedFacts.size(); i++) {
			sb.append(i + 1).append(". ").append(expectedFacts.get(i).trim()).append("\n");
		}
		sb.append("\n");

		sb.append("Answer:\n").append(answer.trim()).append("\n\n");

		sb.append("Cited Source Excerpts:\n");
		if (citedSourcesText.isBlank()) {
			sb.append("(No sources cited)\n");
		} else {
			sb.append(citedSourcesText.trim()).append("\n");
		}

		return sb.toString();
	}

	private Optional<JudgeVerdict> parseVerdict(String rawText, int expectedFactCount) {
		String jsonText = extractJson(rawText);
		if (jsonText.isEmpty()) {
			log.warn("Failed to extract JSON from LLM judge response: '{}'", rawText);
			return Optional.empty();
		}

		try {
			JsonNode root = JSON_MAPPER.readTree(jsonText);
			if (root == null || !root.isObject()) {
				log.warn("LLM judge response is not a JSON object: '{}'", jsonText);
				return Optional.empty();
			}

			JsonNode factsNode = root.get("facts");
			if (factsNode == null || !factsNode.isArray()) {
				log.warn("LLM judge response missing 'facts' array: '{}'", jsonText);
				return Optional.empty();
			}

			if (factsNode.size() != expectedFactCount) {
				log.warn("LLM judge response fact count ({}) does not match expected count ({}): '{}'",
						factsNode.size(), expectedFactCount, jsonText);
				return Optional.empty();
			}

			List<Boolean> factsSupported = new ArrayList<>(expectedFactCount);
			for (int i = 0; i < factsNode.size(); i++) {
				JsonNode factItem = factsNode.get(i);
				if (factItem.isObject() && factItem.has("supported")) {
					factsSupported.add(factItem.get("supported").asBoolean(false));
				} else if (factItem.isBoolean()) {
					factsSupported.add(factItem.asBoolean(false));
				} else {
					factsSupported.add(factItem.path("supported").asBoolean(false));
				}
			}

			boolean grounded = root.has("grounded") && root.get("grounded").asBoolean(false);

			List<String> unsupportedClaims = new ArrayList<>();
			JsonNode claimsNode = root.get("unsupportedClaims");
			if (claimsNode != null && claimsNode.isArray()) {
				for (JsonNode claimNode : claimsNode) {
					if (claimNode != null && !claimNode.asText().isBlank()) {
						unsupportedClaims.add(claimNode.asText().trim());
					}
				}
			}

			return Optional.of(new JudgeVerdict(factsSupported, grounded, unsupportedClaims));
		} catch (Exception e) {
			log.warn("Failed to parse LLM judge response as JSON: '{}'. Error: {}", jsonText, e.getMessage());
			return Optional.empty();
		}
	}

	static String extractJson(String raw) {
		if (raw == null) {
			return "";
		}
		String trimmed = raw.trim();
		int fenceStart = trimmed.indexOf("```");
		if (fenceStart != -1) {
			int contentStart = trimmed.indexOf('\n', fenceStart);
			if (contentStart != -1) {
				int fenceEnd = trimmed.indexOf("```", contentStart);
				if (fenceEnd != -1) {
					return trimmed.substring(contentStart + 1, fenceEnd).trim();
				}
			}
		}

		int firstBrace = trimmed.indexOf('{');
		int lastBrace = trimmed.lastIndexOf('}');
		if (firstBrace != -1 && lastBrace > firstBrace) {
			return trimmed.substring(firstBrace, lastBrace + 1).trim();
		}

		return trimmed;
	}

	private String loadJudgePrompt() {
		ClassPathResource resource = new ClassPathResource(JUDGE_PROMPT_PATH);
		try (InputStream is = resource.getInputStream()) {
			return new String(is.readAllBytes(), StandardCharsets.UTF_8).trim();
		} catch (IOException e) {
			throw new IllegalStateException("Failed to load prompt template from classpath: " + JUDGE_PROMPT_PATH, e);
		}
	}
}
