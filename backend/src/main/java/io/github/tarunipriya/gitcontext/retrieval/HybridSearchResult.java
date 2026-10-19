package io.github.tarunipriya.gitcontext.retrieval;

import java.util.List;

/**
 * Outcome of a search execution containing ranked hits, active mode, warnings, and latency breakdown.
 *
 * @param hits ranked search hits
 * @param mode effective search mode utilized
 * @param reranked true if cross-encoder reranking was successfully applied
 * @param warnings non-fatal degradation warnings emitted during retrieval
 * @param vectorMillis elapsed time in milliseconds for vector retrieval
 * @param keywordMillis elapsed time in milliseconds for keyword retrieval
 * @param fusionMillis elapsed time in milliseconds for Reciprocal Rank Fusion
 * @param rerankMillis elapsed time in milliseconds for cross-encoder reranking
 * @param tookMillis total duration in milliseconds
 */
public record HybridSearchResult(
		List<HybridSearchHit> hits,
		SearchMode mode,
		boolean reranked,
		List<String> warnings,
		long vectorMillis,
		long keywordMillis,
		long fusionMillis,
		long rerankMillis,
		long tookMillis
) {
	public HybridSearchResult {
		hits = hits != null ? List.copyOf(hits) : List.of();
		warnings = warnings != null ? List.copyOf(warnings) : List.of();
	}

	/**
	 * Backwards-compatible constructor without reranking metrics.
	 */
	public HybridSearchResult(
			List<HybridSearchHit> hits,
			SearchMode mode,
			List<String> warnings,
			long vectorMillis,
			long keywordMillis,
			long fusionMillis,
			long tookMillis
	) {
		this(hits, mode, false, warnings, vectorMillis, keywordMillis, fusionMillis, 0L, tookMillis);
	}
}
