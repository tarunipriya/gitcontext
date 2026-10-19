package io.github.tarunipriya.gitcontext.api;

import java.util.List;

/**
 * Response payload for repository semantic vector searches.
 *
 * @param query search query string
 * @param k maximum requested hit count
 * @param tookMillis total duration in milliseconds
 * @param embedMillis query embedding computation duration in milliseconds
 * @param searchMillis Qdrant vector retrieval duration in milliseconds
 * @param hits list of matching search hit items
 */
public record SearchResponse(
		String query,
		int k,
		long tookMillis,
		long embedMillis,
		long searchMillis,
		List<SearchHitItemResponse> hits
) {
	public SearchResponse {
		hits = hits != null ? List.copyOf(hits) : List.of();
	}
}
