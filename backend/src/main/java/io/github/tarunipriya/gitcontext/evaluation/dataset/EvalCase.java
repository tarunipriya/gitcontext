package io.github.tarunipriya.gitcontext.evaluation.dataset;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.tarunipriya.gitcontext.evaluation.RetrievalMetrics;
import java.util.List;
import java.util.Objects;

/**
 * Immutable evaluation test case representing a developer question, relevant ground-truth files,
 * and key facts required for correctness evaluation.
 *
 * @param id unique identifier for this evaluation case (e.g. {@code case-001})
 * @param question natural language developer question
 * @param expectedPaths repository-relative canonical file paths required to answer the question
 * @param expectedFacts concise atomic facts that must be present in the generated answer
 * @param category evaluation category (defaults to {@code "general"} if blank)
 * @param answerable whether the question is answerable from the codebase repository (defaults to {@code true} if omitted)
 */
public record EvalCase(
		String id,
		String question,
		List<String> expectedPaths,
		List<String> expectedFacts,
		String category,
		boolean answerable
) {

	public EvalCase {
		category = (category != null && !category.isBlank()) ? category.trim() : "general";
		expectedPaths = expectedPaths != null
				? expectedPaths.stream()
						.filter(Objects::nonNull)
						.map(RetrievalMetrics::normalizePath)
						.toList()
				: List.of();
		expectedFacts = expectedFacts != null
				? List.copyOf(expectedFacts)
				: List.of();
	}

	/**
	 * JSON creator constructor mapping a nullable {@link Boolean} answerable flag to default {@code true}
	 * when omitted from the JSON payload.
	 *
	 * @param id unique identifier
	 * @param question natural language question
	 * @param expectedPaths expected file paths
	 * @param expectedFacts expected facts
	 * @param category category (defaults to general)
	 * @param answerable nullable answerable flag (defaults to true if null)
	 */
	@JsonCreator
	public EvalCase(
			@JsonProperty("id") String id,
			@JsonProperty("question") String question,
			@JsonProperty("expectedPaths") List<String> expectedPaths,
			@JsonProperty("expectedFacts") List<String> expectedFacts,
			@JsonProperty("category") String category,
			@JsonProperty("answerable") Boolean answerable
	) {
		this(id, question, expectedPaths, expectedFacts, category, answerable != null ? answerable : true);
	}
}
