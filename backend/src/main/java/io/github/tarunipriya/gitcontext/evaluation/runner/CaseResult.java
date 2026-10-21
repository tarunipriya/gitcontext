package io.github.tarunipriya.gitcontext.evaluation.runner;

import io.github.tarunipriya.gitcontext.evaluation.QueryMetrics;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Outcome of evaluating an individual test case under a specific retrieval variant.
 *
 * @param caseId unique evaluation case identifier
 * @param question natural language developer question
 * @param variant retrieval strategy variant utilized
 * @param retrievedPaths ranked list of document paths returned by retrieval
 * @param expectedPaths ground-truth relevant file paths required for the question
 * @param metricsByK retrieval evaluation metrics computed at each cutoff k
 * @param tookMillis search latency in milliseconds
 * @param warnings non-fatal warnings encountered during retrieval
 */
public record CaseResult(
		String caseId,
		String question,
		EvalVariant variant,
		List<String> retrievedPaths,
		Set<String> expectedPaths,
		Map<Integer, QueryMetrics> metricsByK,
		long tookMillis,
		List<String> warnings
) {

	public CaseResult {
		Objects.requireNonNull(caseId, "caseId must not be null");
		Objects.requireNonNull(question, "question must not be null");
		Objects.requireNonNull(variant, "variant must not be null");
		retrievedPaths = (retrievedPaths != null) ? List.copyOf(retrievedPaths) : List.of();
		expectedPaths = (expectedPaths != null) ? Set.copyOf(expectedPaths) : Set.of();
		metricsByK = (metricsByK != null) ? Map.copyOf(metricsByK) : Map.of();
		warnings = (warnings != null) ? List.copyOf(warnings) : List.of();
	}
}
