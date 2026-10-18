package io.github.tarunipriya.gitcontext.indexing;

/**
 * Summary metrics of an indexing operation into the vector store.
 *
 * @param count total number of chunks embedded and indexed
 * @param embedMillis duration in milliseconds spent computing embeddings
 * @param upsertMillis duration in milliseconds spent upserting points into Qdrant
 */
public record IndexSummary(
		int count,
		long embedMillis,
		long upsertMillis
) {
}
