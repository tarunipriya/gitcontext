package io.github.tarunipriya.gitcontext.evaluation;

import java.util.Collection;
import java.util.Objects;

/**
 * Immutable aggregated summary of retrieval evaluation metrics across a dataset of queries at cutoff {@code k}.
 * <p>
 * Contains the query count, cutoff {@code k}, and arithmetic means for Hit Rate, Recall, Precision,
 * Mean Reciprocal Rank (MRR), and Normalized Discounted Cumulative Gain (nDCG).
 *
 * @param queryCount total number of evaluated queries
 * @param k rank cutoff {@code k} used during evaluation
 * @param meanHitRateAtK arithmetic mean of HitRate@k across all queries
 * @param meanRecallAtK arithmetic mean of Recall@k across all queries
 * @param meanPrecisionAtK arithmetic mean of Precision@k across all queries
 * @param mrr Mean Reciprocal Rank (mean of Reciprocal Rank across all queries)
 * @param meanNdcgAtK arithmetic mean of nDCG@k across all queries
 */
public record MetricSummary(
		int queryCount,
		int k,
		double meanHitRateAtK,
		double meanRecallAtK,
		double meanPrecisionAtK,
		double mrr,
		double meanNdcgAtK
) {

	/**
	 * Aggregates a collection of per-query metric results into a {@link MetricSummary}.
	 * <p>
	 * <b>Formula:</b>
	 * <pre>
	 * Mean(Metric) = (1 / |Q|) * &sum;_{q &isin; Q} Metric_q
	 * MRR          = (1 / |Q|) * &sum;_{q &isin; Q} RR_q
	 * </pre>
	 * where {@code Q} is the collection of evaluated queries.
	 * <p>
	 * <b>When useful:</b> Computes overall retrieval benchmark scores across an entire test suite
	 * to measure system-wide retrieval quality and regression impact.
	 *
	 * @param k rank cutoff {@code k} (must be &gt; 0)
	 * @param perQueryMetrics collection of per-query metric results (must not be empty)
	 * @return aggregated metric summary
	 * @throws NullPointerException if {@code perQueryMetrics} is null or contains null elements
	 * @throws IllegalArgumentException if {@code k <= 0} or {@code perQueryMetrics} is empty
	 */
	public static MetricSummary of(int k, Collection<QueryMetrics> perQueryMetrics) {
		Objects.requireNonNull(perQueryMetrics, "perQueryMetrics must not be null");
		if (k <= 0) {
			throw new IllegalArgumentException("k must be greater than 0, got: " + k);
		}
		if (perQueryMetrics.isEmpty()) {
			throw new IllegalArgumentException("perQueryMetrics must not be empty");
		}

		int count = perQueryMetrics.size();
		double sumHitRate = 0.0;
		double sumRecall = 0.0;
		double sumPrecision = 0.0;
		double sumRr = 0.0;
		double sumNdcg = 0.0;

		for (QueryMetrics qm : perQueryMetrics) {
			Objects.requireNonNull(qm, "QueryMetrics entry must not be null");
			sumHitRate += qm.hitRateAtK();
			sumRecall += qm.recallAtK();
			sumPrecision += qm.precisionAtK();
			sumRr += qm.reciprocalRank();
			sumNdcg += qm.ndcgAtK();
		}

		return new MetricSummary(
				count,
				k,
				sumHitRate / count,
				sumRecall / count,
				sumPrecision / count,
				sumRr / count,
				sumNdcg / count
		);
	}
}
