package io.github.tarunipriya.gitcontext.retrieval;

import java.util.List;

/**
 * Outcome of a search execution containing ranked hits, active mode, warnings, and latency breakdown.
 *
 * @param hits ranked search hits
 * @param mode effective search mode utilized
 * @param warnings non-fatal degradation warnings emitted during retrieval
 * @param vectorMillis elapsed time in milliseconds for vector retrieval
 * @param keywordMillis elapsed time in milliseconds for keyword retrieval
 * @param fusionMillis elapsed time in milliseconds for Reciprocal Rank Fusion
 * @param tookMillis total duration in milliseconds
 */
public record HybridSearchResult(
		List<HybridSearchHit> hits,
		SearchMode mode,
		List<String> warnings,
		long vectorMillis,
		long keywordMillis,
		long fusionMillis,
		long tookMillis
) {
	public HybridSearchResult {
		hits = hits != null ? List.copyOf(hits) : List.of();
		warnings = warnings != null ? List.copyOf(warnings) : List.of();
	}
}
