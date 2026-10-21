package io.github.tarunipriya.gitcontext.evaluation.answer;

import io.github.tarunipriya.gitcontext.evaluation.LatencyStats;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Top-level evaluation report summarizing answer quality, citation accuracy, groundedness,
 * refusal correctness, and end-to-end execution latencies across a benchmark dataset.
 *
 * @param datasetName name of the evaluation dataset
 * @param repo target repository identifier
 * @param indexedCommitSha commit SHA indexed during evaluation
 * @param modelUsage mapping of model name to count of generated answers, insertion-ordered
 * @param judgeModel model identifier used by the LLM judge
 * @param startedAt timestamp when evaluation began
 * @param answerableCases total count of answerable cases in dataset
 * @param unanswerableCases total count of unanswerable cases in dataset
 * @param judgedCases number of successfully evaluated judge cases
 * @param judgeFailures number of times the LLM judge failed (invalid JSON or count mismatch)
 * @param generationFailures number of cases where answer generation failed
 * @param meanFactCoverage average fraction of expected atomic facts stated in judged answers
 * @param fullyCorrectRate fraction of judged answers where all expected facts were supported
 * @param groundedRate fraction of judged answers where all claims were supported by cited sources
 * @param citedAnyExpectedRate fraction of successful answerable cases citing at least one expected source
 * @param meanCitedExpectedRecall mean recall of expected sources across successful answerable cases
 * @param refusalAccuracy fraction of unanswerable questions correctly refused
 * @param falseRefusalRate fraction of answerable questions incorrectly refused
 * @param retrievalLatency latency statistics for hybrid retrieval across successful queries
 * @param generationLatency latency statistics for answer generation across successful queries
 * @param totalLatency latency statistics for end-to-end question answering across successful queries
 * @param cases individual evaluation case results
 */
public record AnswerEvalReport(
		String datasetName,
		String repo,
		String indexedCommitSha,
		Map<String, Integer> modelUsage,
		String judgeModel,
		Instant startedAt,
		int answerableCases,
		int unanswerableCases,
		int judgedCases,
		int judgeFailures,
		int generationFailures,
		double meanFactCoverage,
		double fullyCorrectRate,
		double groundedRate,
		double citedAnyExpectedRate,
		double meanCitedExpectedRecall,
		double refusalAccuracy,
		double falseRefusalRate,
		LatencyStats retrievalLatency,
		LatencyStats generationLatency,
		LatencyStats totalLatency,
		List<AnswerCaseResult> cases
) {
	public AnswerEvalReport {
		modelUsage = modelUsage != null ? Collections.unmodifiableMap(new LinkedHashMap<>(modelUsage)) : Map.of();
		judgeModel = (judgeModel != null && !judgeModel.isBlank()) ? judgeModel : "n/a";
		cases = cases != null ? List.copyOf(cases) : List.of();
	}

	public AnswerEvalReport(
			String datasetName,
			String repo,
			String indexedCommitSha,
			Map<String, Integer> modelUsage,
			Instant startedAt,
			int answerableCases,
			int unanswerableCases,
			int judgedCases,
			int judgeFailures,
			int generationFailures,
			double meanFactCoverage,
			double fullyCorrectRate,
			double groundedRate,
			double citedAnyExpectedRate,
			double meanCitedExpectedRecall,
			double refusalAccuracy,
			double falseRefusalRate,
			LatencyStats retrievalLatency,
			LatencyStats generationLatency,
			LatencyStats totalLatency,
			List<AnswerCaseResult> cases
	) {
		this(
				datasetName,
				repo,
				indexedCommitSha,
				modelUsage,
				"n/a",
				startedAt,
				answerableCases,
				unanswerableCases,
				judgedCases,
				judgeFailures,
				generationFailures,
				meanFactCoverage,
				fullyCorrectRate,
				groundedRate,
				citedAnyExpectedRate,
				meanCitedExpectedRecall,
				refusalAccuracy,
				falseRefusalRate,
				retrievalLatency,
				generationLatency,
				totalLatency,
				cases
		);
	}
}

