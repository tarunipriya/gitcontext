package io.github.tarunipriya.gitcontext.evaluation.runner;

import io.github.tarunipriya.gitcontext.evaluation.LatencyStats;
import io.github.tarunipriya.gitcontext.evaluation.MetricSummary;
import io.github.tarunipriya.gitcontext.evaluation.QueryMetrics;
import io.github.tarunipriya.gitcontext.evaluation.RetrievalMetrics;
import io.github.tarunipriya.gitcontext.evaluation.SourceKeys;
import io.github.tarunipriya.gitcontext.evaluation.dataset.EvalCase;
import io.github.tarunipriya.gitcontext.evaluation.dataset.EvalDataset;
import io.github.tarunipriya.gitcontext.model.RepoRef;
import io.github.tarunipriya.gitcontext.retrieval.HybridSearchResult;
import io.github.tarunipriya.gitcontext.retrieval.HybridSearchService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Service that evaluates and compares retrieval strategies across benchmark evaluation datasets.
 * <p>
 * Supports comparing vector, keyword, hybrid RRF, and neural cross-encoder reranked search
 * across multiple cutoff ranks ({@code k}), computing Hit Rate, Recall, Precision, MRR,
 * nDCG, and tail latency statistics.
 */
@Service
public class RetrievalEvaluator {

	private static final Logger log = LoggerFactory.getLogger(RetrievalEvaluator.class);

	private final HybridSearchService searchService;

	/**
	 * Creates a new {@link RetrievalEvaluator}.
	 *
	 * @param searchService hybrid search service facade
	 */
	public RetrievalEvaluator(HybridSearchService searchService) {
		this.searchService = Objects.requireNonNull(searchService, "searchService must not be null");
	}

	/**
	 * Executes evaluation of the given dataset against the specified repository across search variants.
	 *
	 * @param dataset benchmark evaluation dataset
	 * @param repo target repository reference
	 * @param indexedPaths collection of all indexed file paths (null to skip missing-path checks)
	 * @param indexedCommitSha Git commit SHA corresponding to the indexed repository state
	 * @param variants search variants to evaluate and compare
	 * @param ks cutoff rank positions k (e.g. 1, 3, 5, 10)
	 * @param excludeTests whether to filter out test files during retrieval
	 * @param warmupQueries number of unrecorded warmup searches per variant
	 * @return comprehensive evaluation report
	 */
	public EvalReport evaluate(
			EvalDataset dataset,
			RepoRef repo,
			Collection<String> indexedPaths,
			String indexedCommitSha,
			List<EvalVariant> variants,
			List<Integer> ks,
			boolean excludeTests,
			int warmupQueries
	) {
		Objects.requireNonNull(dataset, "dataset must not be null");
		Objects.requireNonNull(repo, "repo must not be null");
		Objects.requireNonNull(variants, "variants must not be null");
		Objects.requireNonNull(ks, "ks must not be null");
		if (ks.isEmpty()) {
			throw new IllegalArgumentException("Cutoff ranks ks must not be empty");
		}
		for (Integer k : ks) {
			if (k == null || k <= 0) {
				throw new IllegalArgumentException("Cutoff rank k must be greater than 0, got: " + k);
			}
		}

		Instant startedAt = Instant.now();
		List<Integer> sortedKs = ks.stream().distinct().sorted().toList();
		int maxK = Collections.max(sortedKs);

		List<EvalCase> answerableCases = dataset.answerableCases();
		int answerableCount = answerableCases.size();
		int skippedUnanswerable = dataset.unanswerableCases().size();

		// Detect missing expected paths
		List<String> missingExpectedPaths = new ArrayList<>();
		Set<String> normalizedIndexed = null;
		if (indexedPaths != null) {
			normalizedIndexed = indexedPaths.stream()
					.filter(Objects::nonNull)
					.map(SourceKeys::normalizeExpected)
					.collect(Collectors.toSet());

			Set<String> missingSet = new LinkedHashSet<>();
			for (EvalCase evalCase : answerableCases) {
				for (String expected : evalCase.expectedPaths()) {
					String normalized = SourceKeys.normalizeExpected(expected);
					if (!normalizedIndexed.contains(normalized)) {
						missingSet.add(normalized);
					}
				}
			}
			missingExpectedPaths.addAll(missingSet);
			for (String missing : missingExpectedPaths) {
				log.warn("Expected path not present in indexed files: {}", missing);
			}
		}

		List<VariantReport> variantReports = new ArrayList<>();

		for (EvalVariant variant : variants) {
			log.info("Evaluating variant: {} (mode={}, rerank={})", variant, variant.mode(), variant.rerank());

			// Warmup searches
			if (!answerableCases.isEmpty() && warmupQueries > 0) {
				String warmupQuestion = answerableCases.get(0).question();
				for (int w = 0; w < warmupQueries; w++) {
					try {
						searchService.search(
								repo,
								warmupQuestion,
								maxK,
								null,
								null,
								variant.mode(),
								variant.rerank(),
								excludeTests
						);
					} catch (Exception e) {
						log.warn("Warmup query {} failed for variant {}: {}", w + 1, variant, e.getMessage());
					}
				}
			}

			boolean loggedRerankWarning = false;
			int failedCases = 0;
			List<CaseResult> caseResults = new ArrayList<>();
			List<Long> latencies = new ArrayList<>();

			for (EvalCase evalCase : answerableCases) {
				List<String> caseWarnings = new ArrayList<>();

				// Check if all expected paths for this case are missing
				if (normalizedIndexed != null && !evalCase.expectedPaths().isEmpty()) {
					boolean allMissing = evalCase.expectedPaths().stream()
							.map(SourceKeys::normalizeExpected)
							.noneMatch(normalizedIndexed::contains);
					if (allMissing) {
						caseWarnings.add("All expected paths are missing from indexed documents: " + evalCase.expectedPaths());
					}
				}

				CaseResult caseResult;
				try {
					HybridSearchResult result = searchService.search(
							repo,
							evalCase.question(),
							maxK,
							null,
							null,
							variant.mode(),
							variant.rerank(),
							excludeTests
					);

					if (result.warnings() != null) {
						caseWarnings.addAll(result.warnings());
					}

					if (variant == EvalVariant.HYBRID_RERANK && !result.reranked()) {
						caseWarnings.add("Reranker was requested for HYBRID_RERANK but was not applied by search service");
						if (!loggedRerankWarning) {
							log.warn("Reranker was requested for HYBRID_RERANK but reranked=false was returned (reranker disabled in config)");
							loggedRerankWarning = true;
						}
					}

					List<String> retrievedPaths = result.hits().stream()
							.map(h -> SourceKeys.of(h.hit()))
							.filter(Objects::nonNull)
							.toList();

					Set<String> expectedSet = Set.copyOf(evalCase.expectedPaths());
					Map<Integer, QueryMetrics> metricsByK = new LinkedHashMap<>();
					for (Integer k : sortedKs) {
						QueryMetrics qm = expectedSet.isEmpty()
								? new QueryMetrics(0.0, 0.0, 0.0, 0.0, 0.0)
								: QueryMetrics.compute(retrievedPaths, expectedSet, k);
						metricsByK.put(k, qm);
					}

					long tookMillis = result.tookMillis();
					latencies.add(tookMillis);

					caseResult = new CaseResult(
							evalCase.id(),
							evalCase.question(),
							variant,
							retrievedPaths,
							expectedSet,
							metricsByK,
							tookMillis,
							caseWarnings
					);
				} catch (Exception e) {
					log.warn("Search exception for case '{}' under variant {}: {}", evalCase.id(), variant, e.getMessage());
					caseWarnings.add("Search exception: " + e.getMessage());
					failedCases++;

					Map<Integer, QueryMetrics> emptyMetrics = new LinkedHashMap<>();
					for (Integer k : sortedKs) {
						emptyMetrics.put(k, new QueryMetrics(0.0, 0.0, 0.0, 0.0, 0.0));
					}

					caseResult = new CaseResult(
							evalCase.id(),
							evalCase.question(),
							variant,
							List.of(),
							Set.copyOf(evalCase.expectedPaths()),
							emptyMetrics,
							0L,
							caseWarnings
					);
				}

				caseResults.add(caseResult);
			}

			Map<Integer, MetricSummary> summaryByK = new LinkedHashMap<>();
			for (Integer k : sortedKs) {
				List<QueryMetrics> metricsForK = caseResults.stream()
						.map(c -> c.metricsByK().get(k))
						.filter(Objects::nonNull)
						.toList();
				if (!metricsForK.isEmpty()) {
					summaryByK.put(k, MetricSummary.of(k, metricsForK));
				}
			}

			LatencyStats latencyStats = latencies.isEmpty()
					? new LatencyStats(0, 0L, 0.0, 0L, 0L, 0L, 0L)
					: LatencyStats.of(latencies);

			variantReports.add(new VariantReport(variant, summaryByK, latencyStats, caseResults, failedCases));
		}

		return new EvalReport(
				dataset.name(),
				repo.fullName(),
				indexedCommitSha,
				startedAt,
				answerableCount,
				skippedUnanswerable,
				missingExpectedPaths,
				variantReports
		);
	}
}
