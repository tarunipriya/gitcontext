package io.github.tarunipriya.gitcontext.retrieval;

/**
 * Strategy mode determining which search mechanism to execute for a query.
 */
public enum SearchMode {
	/**
	 * Dense semantic vector similarity search via Qdrant.
	 */
	VECTOR,

	/**
	 * Sparse lexical BM25 keyword search via Apache Lucene.
	 */
	KEYWORD,

	/**
	 * Combined hybrid retrieval fusing vector and keyword rankings via Reciprocal Rank Fusion (RRF).
	 */
	HYBRID
}
