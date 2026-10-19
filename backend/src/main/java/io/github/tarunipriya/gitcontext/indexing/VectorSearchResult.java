package io.github.tarunipriya.gitcontext.indexing;

import java.util.List;

/**
 * Outcome of a vector store similarity search containing retrieved hits and execution timings.
 *
 * @param hits retrieved and filtered search hits
 * @param embedMillis elapsed time in milliseconds to compute query embeddings
 * @param searchMillis elapsed time in milliseconds to query Qdrant and retrieve scored points
 * @param tookMillis total end-to-end execution time in milliseconds
 */
public record VectorSearchResult(
		List<SearchHit> hits,
		long embedMillis,
		long searchMillis,
		long tookMillis
) {
	public VectorSearchResult {
		hits = hits != null ? List.copyOf(hits) : List.of();
	}
}
