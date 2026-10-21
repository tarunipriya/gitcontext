package io.github.tarunipriya.gitcontext.evaluation.runner;

import io.github.tarunipriya.gitcontext.retrieval.SearchMode;
import java.util.Arrays;
import java.util.Objects;

/**
 * Search evaluation variants comparing different retrieval strategies and reranking configurations.
 */
public enum EvalVariant {

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

	EvalVariant(SearchMode mode, boolean rerank) {
		this.mode = mode;
		this.rerank = rerank;
	}

	/**
	 * Returns the underlying {@link SearchMode} executed by this variant.
	 *
	 * @return search mode
	 */
	public SearchMode mode() {
		return mode;
	}

	/**
	 * Returns whether cross-encoder reranking is requested for this variant.
	 *
	 * @return true if reranking is enabled
	 */
	public boolean rerank() {
		return rerank;
	}

	/**
	 * Parses an evaluation variant name case-insensitively.
	 *
	 * @param value raw variant name string
	 * @return matching {@link EvalVariant}
	 * @throws IllegalArgumentException if null, blank, or an unknown variant name
	 */
	public static EvalVariant parse(String value) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(
					"Evaluation variant must not be null or blank. Valid values: " + Arrays.toString(values()));
		}
		String normalized = value.trim().toUpperCase();
		for (EvalVariant variant : values()) {
			if (variant.name().equals(normalized)) {
				return variant;
			}
		}
		throw new IllegalArgumentException(
				"Unknown evaluation variant: '" + value + "'. Valid values are: " + Arrays.toString(values()));
	}

	/**
	 * Alias for {@link #parse(String)} providing standard Java naming convention.
	 *
	 * @param value raw variant name string
	 * @return matching {@link EvalVariant}
	 * @throws IllegalArgumentException if null, blank, or an unknown variant name
	 */
	public static EvalVariant fromString(String value) {
		return parse(value);
	}
}
