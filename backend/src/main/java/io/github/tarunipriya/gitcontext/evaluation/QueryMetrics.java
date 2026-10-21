package io.github.tarunipriya.gitcontext.evaluation;

import java.util.List;
import java.util.Set;

/**
 * Immutable per-query retrieval evaluation metrics computed at cutoff {@code k}.
 * <p>
 * Contains individual query results for Hit Rate, Recall, Precision, Reciprocal Rank,
 * and Normalized Discounted Cumulative Gain (nDCG).
 *
 * @param hitRateAtK 1.0 if any relevant path is in top {@code k}, else 0.0
 * @param recallAtK fraction of relevant documents retrieved in top {@code k}
 * @param precisionAtK proportion of top {@code k} slots filled by relevant documents
 * @param reciprocalRank reciprocal of the 1-based rank of the first relevant document
 * @param ndcgAtK normalized discounted cumulative gain at rank {@code k}
 */
public record QueryMetrics(
		double hitRateAtK,
		double recallAtK,
		double precisionAtK,
		double reciprocalRank,
		double ndcgAtK
) {

	/**
	 * Computes all retrieval metrics for a single query at cutoff {@code k}.
	 * <p>
	 * <b>Formula:</b> Delegated to individual metric calculations in {@link RetrievalMetrics}.
	 * <p>
	 * <b>When useful:</b> Convenient one-stop computation of all retrieval metrics for an individual test case.
	 *
	 * @param retrieved ranked list of retrieved document/file paths (best first)
	 * @param relevant expected relevant document/file paths
	 * @param k rank cutoff (must be &gt; 0)
	 * @return computed per-query metrics
	 */
	public static QueryMetrics compute(List<String> retrieved, Set<String> relevant, int k) {
		return new QueryMetrics(
				RetrievalMetrics.hitRateAtK(retrieved, relevant, k),
				RetrievalMetrics.recallAtK(retrieved, relevant, k),
				RetrievalMetrics.precisionAtK(retrieved, relevant, k),
				RetrievalMetrics.reciprocalRank(retrieved, relevant),
				RetrievalMetrics.ndcgAtK(retrieved, relevant, k)
		);
	}
}
