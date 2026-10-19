package io.github.tarunipriya.gitcontext.indexing;

import java.util.List;

/**
 * Service for computing dense vector representations for documents and search queries.
 */
public interface EmbeddingService {

	/**
	 * Returns the output dimensionality of the embedding vectors produced by this service.
	 *
	 * @return embedding dimension size (e.g. 384 for bge-small-en-v1.5)
	 */
	int dimension();

	/**
	 * Embeds a list of document texts into dense vector representations.
	 *
	 * <p>Document texts are embedded without query instruction prefixes.
	 *
	 * @param texts list of text segments to embed
	 * @return list of embedding vectors matching the input order
	 */
	List<float[]> embedDocuments(List<String> texts);

	/**
	 * Embeds a single search query into a dense vector representation.
	 *
	 * <p>Prepends the configured model-specific query instruction prefix prior to inference.
	 *
	 * @param text user question or search query string
	 * @return dense embedding vector
	 */
	float[] embedQuery(String text);
}
