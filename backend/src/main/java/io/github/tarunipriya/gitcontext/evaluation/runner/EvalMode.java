package io.github.tarunipriya.gitcontext.evaluation.runner;

import io.github.tarunipriya.gitcontext.retrieval.SearchMode;

/**
 * Fundamental evaluation search mode specifying the retrieval strategy and reranking behavior.
 */
public enum EvalMode {

	/**
	 * Dense semantic vector similarity search via Qdrant without reranking.
	 */
	VECTOR(SearchMode.VECTOR, false),

	/**
	 * Sparse lexical BM25 keyword search via Apache Lucene without reranking.
	 */
	KEYWORD(SearchMode.KEYWORD, false),

	/**
	 * Combined hybrid retrieval fusing vector and keyword rankings via Reciprocal Rank Fusion (RRF) without reranking.
	 */
	HYBRID(SearchMode.HYBRID, false),

	/**
	 * Combined hybrid retrieval followed by cross-encoder neural reranking.
	 */
	HYBRID_RERANK(SearchMode.HYBRID, true);

	private final SearchMode mode;
	private final boolean rerank;

	EvalMode(SearchMode mode, boolean rerank) {
		this.mode = mode;
		this.rerank = rerank;
	}

	/**
	 * Returns the underlying {@link SearchMode} executed by this mode.
	 *
	 * @return search mode
	 */
	public SearchMode mode() {
		return mode;
	}

	/**
	 * Returns whether cross-encoder reranking is requested for this mode.
	 *
	 * @return true if reranking is enabled
	 */
	public boolean rerank() {
		return rerank;
	}
}
