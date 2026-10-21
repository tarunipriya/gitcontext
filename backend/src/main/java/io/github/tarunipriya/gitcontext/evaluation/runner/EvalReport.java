package io.github.tarunipriya.gitcontext.evaluation.runner;

import java.time.Instant;
import java.util.List;

/**
 * Top-level evaluation report comparing multiple retrieval variants on a benchmark dataset.
 *
 * @param datasetName name of the evaluation dataset (derived from file name)
 * @param repo target repository in "owner/name" format
 * @param indexedCommitSha Git commit SHA of the indexed codebase revision
 * @param startedAt timestamp when evaluation commenced
 * @param answerableCases total count of answerable cases evaluated
 * @param skippedUnanswerable count of negative/unanswerable cases skipped during retrieval evaluation
 * @param missingExpectedPaths ground-truth expected paths absent from the repository index
 * @param variants evaluation outcomes for each compared variant
 */
public record EvalReport(
		String datasetName,
		String repo,
		String indexedCommitSha,
		Instant startedAt,
		int answerableCases,
		int skippedUnanswerable,
		List<String> missingExpectedPaths,
		List<VariantReport> variants
) {

	public EvalReport {
		datasetName = (datasetName != null) ? datasetName : "";
		repo = (repo != null) ? repo : "";
		indexedCommitSha = (indexedCommitSha != null) ? indexedCommitSha : "";
		startedAt = (startedAt != null) ? startedAt : Instant.now();
		missingExpectedPaths = (missingExpectedPaths != null) ? List.copyOf(missingExpectedPaths) : List.of();
		variants = (variants != null) ? List.copyOf(variants) : List.of();
	}
}
