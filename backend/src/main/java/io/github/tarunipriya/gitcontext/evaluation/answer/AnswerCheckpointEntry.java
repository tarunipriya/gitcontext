package io.github.tarunipriya.gitcontext.evaluation.answer;

import java.util.List;

/**
 * Checkpoint record serialized as one JSON line per evaluated case in
 * {@code answers-<dataset>.checkpoint.jsonl}.
 * <p>
 * Contains all fields of {@link AnswerCaseResult} along with execution metadata:
 * the indexed commit SHA, the target answer model, and the judge model.
 */
public record AnswerCheckpointEntry(
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
		List<String> warnings,
		String indexedCommitSha,
		String answerModel,
		String judgeModel,
		boolean judgeRequired,
		String citedSourcesText
) {
	public AnswerCheckpointEntry {
		citedKeys = citedKeys != null ? List.copyOf(citedKeys) : List.of();
		expectedKeys = expectedKeys != null ? List.copyOf(expectedKeys) : List.of();
		unsupportedClaims = unsupportedClaims != null ? List.copyOf(unsupportedClaims) : List.of();
		warnings = warnings != null ? List.copyOf(warnings) : List.of();
		citedSourcesText = citedSourcesText != null ? citedSourcesText : "";
	}

	public static AnswerCheckpointEntry from(
			AnswerCaseResult result,
			String indexedCommitSha,
			String answerModel,
			String judgeModel,
			boolean judgeRequired,
			String citedSourcesText
	) {
		return new AnswerCheckpointEntry(
				result.caseId(),
				result.question(),
				result.answerable(),
				result.answer(),
				result.refused(),
				result.model(),
				result.citedKeys(),
				result.expectedKeys(),
				result.citedExpectedRecall(),
				result.citedAnyExpected(),
				result.factCoverage(),
				result.grounded(),
				result.unsupportedClaims(),
				result.retrievalMillis(),
				result.generationMillis(),
				result.totalMillis(),
				result.warnings(),
				indexedCommitSha,
				answerModel,
				judgeModel,
				judgeRequired,
				citedSourcesText
		);
	}

	public static AnswerCheckpointEntry from(
			AnswerCaseResult result,
			String indexedCommitSha,
			String answerModel,
			String judgeModel
	) {
		return from(result, indexedCommitSha, answerModel, judgeModel, false, "");
	}

	public AnswerCaseResult toCaseResult() {
		return new AnswerCaseResult(
				caseId,
				question,
				answerable,
				answer,
				refused,
				model,
				citedKeys,
				expectedKeys,
				citedExpectedRecall,
				citedAnyExpected,
				factCoverage,
				grounded,
				unsupportedClaims,
				retrievalMillis,
				generationMillis,
				totalMillis,
				warnings
		);
	}

	public boolean isComplete() {
		return answer != null && !answer.isBlank() && (!judgeRequired || factCoverage != null);
	}

	public boolean isSuccessful() {
		return isComplete();
	}

	/**
	 * Returns a copy of this checkpoint entry with judge verdicts cleared so that it can be
	 * re-judged by a new judge model while keeping the previously generated answer and cited sources.
	 *
	 * @return entry with factCoverage and grounded cleared (null), unsupportedClaims emptied, and judge failure warnings removed
	 */
	public AnswerCheckpointEntry withClearedVerdicts() {
		List<String> cleanedWarnings = warnings.stream()
				.filter(w -> !w.startsWith("Judge evaluation failed"))
				.toList();
		return new AnswerCheckpointEntry(
				caseId,
				question,
				answerable,
				answer,
				refused,
				model,
				citedKeys,
				expectedKeys,
				citedExpectedRecall,
				citedAnyExpected,
				null,
				null,
				List.of(),
				retrievalMillis,
				generationMillis,
				totalMillis,
				cleanedWarnings,
				indexedCommitSha,
				answerModel,
				judgeModel,
				judgeRequired,
				citedSourcesText
		);
	}
}
