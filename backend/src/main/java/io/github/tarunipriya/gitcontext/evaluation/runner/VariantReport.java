package io.github.tarunipriya.gitcontext.evaluation.runner;

import io.github.tarunipriya.gitcontext.evaluation.LatencyStats;
import io.github.tarunipriya.gitcontext.evaluation.MetricSummary;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Aggregated evaluation report for a single retrieval variant across all answerable cases.
 *
 * @param variant retrieval strategy variant evaluated
 * @param summaryByK aggregated metric summaries indexed by cutoff rank k
 * @param latency execution latency distribution across successful cases
 * @param cases individual evaluation results for each test case
 * @param failedCases count of cases where search threw an exception
 */
public record VariantReport(
		EvalVariant variant,
		Map<Integer, MetricSummary> summaryByK,
		LatencyStats latency,
		List<CaseResult> cases,
		int failedCases
) {

	public VariantReport {
		Objects.requireNonNull(variant, "variant must not be null");
		summaryByK = (summaryByK != null) ? Collections.unmodifiableMap(new LinkedHashMap<>(summaryByK)) : Map.of();
		Objects.requireNonNull(latency, "latency must not be null");
		cases = (cases != null) ? List.copyOf(cases) : List.of();
	}

	/**
	 * Convenience constructor for backwards compatibility defaulting failedCases to 0.
	 */
	public VariantReport(
			EvalVariant variant,
			Map<Integer, MetricSummary> summaryByK,
			LatencyStats latency,
			List<CaseResult> cases
	) {
		this(variant, summaryByK, latency, cases, 0);
	}
}
