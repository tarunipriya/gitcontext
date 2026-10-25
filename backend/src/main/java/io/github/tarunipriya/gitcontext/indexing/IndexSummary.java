package io.github.tarunipriya.gitcontext.indexing;

/**
 * Summary metrics of an indexing operation into the vector store.
 *
 * @param count total number of chunks indexed into the vector store
 * @param embeddedChunks number of chunks whose embeddings were computed in this run
 * @param reusedChunks number of chunks whose existing embeddings were reused
 * @param embedMillis duration in milliseconds spent computing embeddings
 * @param upsertMillis duration in milliseconds spent upserting points into Qdrant
 */
public record IndexSummary(
		int count,
		int embeddedChunks,
		int reusedChunks,
		long embedMillis,
		long upsertMillis
) {

	/**
	 * Backwards-compatible convenience constructor defaulting embeddedChunks to count and reusedChunks to 0.
	 */
	public IndexSummary(int count, long embedMillis, long upsertMillis) {
		this(count, count, 0, embedMillis, upsertMillis);
	}
}
