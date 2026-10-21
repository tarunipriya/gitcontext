package io.github.tarunipriya.gitcontext.evaluation.answer;

import io.github.tarunipriya.gitcontext.evaluation.SourceKeys;
import io.github.tarunipriya.gitcontext.generation.AnswerService;
import io.github.tarunipriya.gitcontext.generation.Source;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Detailed evaluation outcome for a single evaluation case.
 *
 * @param caseId evaluation case identifier
 * @param question natural language developer question
 * @param answerable whether the question is expected to be answerable
 * @param answer generated answer text
 * @param refused whether the response was a refusal
 * @param citedKeys distinct canonical source keys cited in the answer
 * @param expectedKeys normalized ground truth source keys expected for the answer
 * @param citedExpectedRecall fraction of expected source keys cited (0 for unanswerable)
 * @param citedAnyExpected whether at least one expected source key was cited
 * @param factCoverage fraction of expected atomic facts stated in the answer (null if not judged)
 * @param grounded whether all claims in the answer are grounded in cited sources (null if not judged)
 * @param unsupportedClaims claims made in the answer not supported by cited sources
 * @param retrievalMillis time spent in hybrid retrieval
 * @param generationMillis time spent generating the answer via LLM
 * @param model LLM model name that generated the answer (null if generation failed)
 * @param totalMillis total end-to-end execution time in milliseconds
 * @param warnings accumulated warnings or error messages during evaluation
 */
public record AnswerCaseResult(
		String caseId,
		String question,
		boolean answerable,
		String answer,
		boolean refused,
		String model,
		List<String> citedKeys,
		List<String> expectedKeys,
		double citedExpectedRecall,
		boolean citedAnyExpected,
		Double factCoverage,
		Boolean grounded,
		List<String> unsupportedClaims,
		long retrievalMillis,
		long generationMillis,
		long totalMillis,
		List<String> warnings
) {
	public AnswerCaseResult {
		citedKeys = citedKeys != null ? List.copyOf(citedKeys) : List.of();
		expectedKeys = expectedKeys != null ? List.copyOf(expectedKeys) : List.of();
		unsupportedClaims = unsupportedClaims != null ? List.copyOf(unsupportedClaims) : List.of();
		warnings = warnings != null ? List.copyOf(warnings) : List.of();
	}

	/**
	 * Determines whether an answer is a refusal according to standard refusal patterns.
	 *
	 * @param answer textual response from the model
	 * @return true if answer matches {@link AnswerService#NOT_FOUND_ANSWER} or contains "couldn't find this in the indexed repository"
	 */
	public static boolean isRefusal(String answer) {
		if (answer == null) {
			return false;
		}
		String trimmed = answer.trim();
		if (trimmed.equals(AnswerService.NOT_FOUND_ANSWER)) {
			return true;
		}
		String lower = trimmed.toLowerCase(Locale.ROOT);
		return lower.contains("couldn't find this in the indexed repository")
				|| lower.contains("couldn’t find this in the indexed repository");
	}

	/**
	 * Extracts canonical, distinct source keys for cited sources preserving original order.
	 *
	 * @param sources numbered sources from answer result
	 * @return ordered list of distinct non-null source keys
	 */
	public static List<String> extractCitedKeys(List<Source> sources) {
		if (sources == null) {
			return List.of();
		}
		List<String> result = new ArrayList<>();
		for (Source s : sources) {
			if (s != null && s.cited() && s.hit() != null) {
				String key = SourceKeys.of(s.hit());
				if (key != null && !result.contains(key)) {
					result.add(key);
				}
			}
		}
		return List.copyOf(result);
	}

	/**
	 * Computes the cited expected recall: |citedKeys ∩ expectedKeys| / |expectedKeys|.
	 * Returns 0 for unanswerable cases or when expectedKeys is empty.
	 *
	 * @param citedKeys canonical source keys cited in answer
	 * @param expectedKeys normalized expected source keys
	 * @param answerable whether the question is answerable
	 * @return recall fraction between 0.0 and 1.0
	 */
	public static double calculateCitedExpectedRecall(List<String> citedKeys, List<String> expectedKeys, boolean answerable) {
		if (!answerable || expectedKeys == null || expectedKeys.isEmpty() || citedKeys == null || citedKeys.isEmpty()) {
			return 0.0;
		}
		Set<String> expectedSet = new HashSet<>(expectedKeys);
		Set<String> citedSet = new HashSet<>(citedKeys);
		citedSet.retainAll(expectedSet);
		return (double) citedSet.size() / expectedSet.size();
	}

	/**
	 * Returns true if any expected source key was cited.
	 *
	 * @param citedKeys canonical source keys cited in answer
	 * @param expectedKeys normalized expected source keys
	 * @param answerable whether the question is answerable
	 * @return true if intersection is non-empty
	 */
	public static boolean calculateCitedAnyExpected(List<String> citedKeys, List<String> expectedKeys, boolean answerable) {
		if (!answerable || expectedKeys == null || expectedKeys.isEmpty() || citedKeys == null || citedKeys.isEmpty()) {
			return false;
		}
		Set<String> expectedSet = new HashSet<>(expectedKeys);
		return citedKeys.stream().anyMatch(expectedSet::contains);
	}
}
