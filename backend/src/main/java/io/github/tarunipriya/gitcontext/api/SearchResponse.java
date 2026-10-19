package io.github.tarunipriya.gitcontext.api;

import io.github.tarunipriya.gitcontext.retrieval.SearchMode;
import java.util.List;

/**
 * Response payload for repository searches across vector, keyword, or hybrid modes.
 *
 * @param query search query string
 * @param k maximum requested hit count
 * @param mode effective search mode utilized
 * @param reranked whether cross-encoder reranking was successfully applied
 * @param tookMillis total duration in milliseconds
 * @param vectorMillis vector retrieval duration in milliseconds
 * @param keywordMillis keyword retrieval duration in milliseconds
 * @param fusionMillis Reciprocal Rank Fusion duration in milliseconds
 * @param rerankMillis cross-encoder reranking duration in milliseconds
 * @param embedMillis query embedding computation duration in milliseconds
 * @param searchMillis vector index search duration in milliseconds
 * @param warnings non-fatal warnings emitted during search execution (e.g. degradation notices)
 * @param hits list of matching search hit items
 */
public record SearchResponse(
		String query,
		int k,
		SearchMode mode,
		boolean reranked,
		long tookMillis,
		long vectorMillis,
		long keywordMillis,
		long fusionMillis,
		long rerankMillis,
		long embedMillis,
		long searchMillis,
		List<String> warnings,
		List<SearchHitItemResponse> hits
) {
	public SearchResponse {
		warnings = warnings != null ? List.copyOf(warnings) : List.of();
		hits = hits != null ? List.copyOf(hits) : List.of();
	}

	/**
	 * Backwards-compatible constructor for search executions without reranking metrics.
	 */
	public SearchResponse(
			String query,
			int k,
			SearchMode mode,
			long tookMillis,
			long vectorMillis,
			long keywordMillis,
			long fusionMillis,
			long embedMillis,
			long searchMillis,
			List<String> warnings,
			List<SearchHitItemResponse> hits
	) {
		this(query, k, mode, false, tookMillis, vectorMillis, keywordMillis, fusionMillis, 0L, embedMillis, searchMillis, warnings, hits);
	}

	/**
	 * Convenience constructor for search executions without separated embedding timings.
	 */
	public SearchResponse(
			String query,
			int k,
			SearchMode mode,
			long tookMillis,
			long vectorMillis,
			long keywordMillis,
			long fusionMillis,
			List<String> warnings,
			List<SearchHitItemResponse> hits
	) {
		this(query, k, mode, false, tookMillis, vectorMillis, keywordMillis, fusionMillis, 0L, 0L, vectorMillis, warnings, hits);
	}

	/**
	 * Backwards-compatible constructor for vector-only searches.
	 */
	public SearchResponse(
			String query,
			int k,
			long tookMillis,
			long embedMillis,
			long searchMillis,
			List<SearchHitItemResponse> hits
	) {
		this(query, k, SearchMode.VECTOR, false, tookMillis, embedMillis + searchMillis, 0L, 0L, 0L, embedMillis, searchMillis, List.of(), hits);
	}
}
