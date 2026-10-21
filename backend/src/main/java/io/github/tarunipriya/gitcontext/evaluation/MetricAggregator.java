package io.github.tarunipriya.gitcontext.evaluation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Stateless aggregator that computes mean retrieval evaluation metrics across multiple queries.
 * <p>
 * Evaluates queries and aggregates values for Hit Rate @ K, Recall @ K, Precision @ K, Mean Reciprocal Rank (MRR),
 * and nDCG @ K into an immutable {@link MetricSummary}.
 */
public final class MetricAggregator {

	private MetricAggregator() {
		// Utility class; prevent instantiation
	}

	/**
	 * Represents an individual query evaluation input consisting of ranked retrieved paths and expected relevant paths.
	 *
	 * @param retrieved ranked list of retrieved document/file paths (best first)
	 * @param relevant expected relevant document/file paths
	 */
	public record QueryEvaluation(
			List<String> retrieved,
			Set<String> relevant
	) {
		public QueryEvaluation {
			Objects.requireNonNull(retrieved, "retrieved must not be null");
			Objects.requireNonNull(relevant, "relevant must not be null");
		}
	}

	/**
	 * Computes and aggregates retrieval metrics for a collection of {@link QueryEvaluation} instances.
	 * <p>
	 * <b>Formula:</b> Evaluates each query via {@link RetrievalMetrics} at cutoff {@code k}, then computes
	 * the arithmetic mean of all metrics across queries.
	 * <p>
	 * <b>When useful:</b> Evaluates an end-to-end benchmark dataset given raw retrieved lists and gold-standard sets.
	 *
	 * @param k rank cutoff {@code k} (must be &gt; 0)
	 * @param queries collection of query evaluation pairs (must not be empty)
	 * @return immutable summary of the evaluated dataset
	 * @throws NullPointerException if {@code queries} is null
	 * @throws IllegalArgumentException if {@code k <= 0} or {@code queries} is empty
	 */
	public static MetricSummary aggregateQueries(int k, Collection<QueryEvaluation> queries) {
		Objects.requireNonNull(queries, "queries collection must not be null");
		if (k <= 0) {
			throw new IllegalArgumentException("k must be greater than 0, got: " + k);
		}
		if (queries.isEmpty()) {
			throw new IllegalArgumentException("queries collection must not be empty");
		}

		List<QueryMetrics> metricsList = new ArrayList<>(queries.size());
		for (QueryEvaluation query : queries) {
			Objects.requireNonNull(query, "query evaluation element must not be null");
			metricsList.add(QueryMetrics.compute(query.retrieved(), query.relevant(), k));
		}

		return MetricSummary.of(k, metricsList);
	}
}
