package io.github.tarunipriya.gitcontext.retrieval;

import io.github.tarunipriya.gitcontext.config.GitContextProperties;

/**
 * Encapsulates weight multipliers applied to dense vector and sparse keyword reciprocal rank scores during RRF fusion.
 *
 * @param vectorWeight weight multiplier applied to vector retriever hits (finite, &gt;= 0)
 * @param keywordWeight weight multiplier applied to keyword retriever hits (finite, &gt;= 0)
 */
public record FusionWeights(double vectorWeight, double keywordWeight) {

	public FusionWeights {
		if (!Double.isFinite(vectorWeight) || vectorWeight < 0) {
			throw new IllegalArgumentException("vectorWeight must be finite and >= 0, got " + vectorWeight);
		}
		if (!Double.isFinite(keywordWeight) || keywordWeight < 0) {
			throw new IllegalArgumentException("keywordWeight must be finite and >= 0, got " + keywordWeight);
		}
		if (vectorWeight == 0.0 && keywordWeight == 0.0) {
			throw new IllegalArgumentException("vectorWeight and keywordWeight cannot both be 0");
		}
	}

	/**
	 * Default fusion weights with unweighted 1.0 / 1.0 multiplier ratio.
	 *
	 * @return default fusion weights (1.0, 1.0)
	 */
	public static FusionWeights defaults() {
		return new FusionWeights(1.0, 1.0);
	}

	/**
	 * Extracts fusion weights from configured {@link GitContextProperties}, or falls back to defaults (1.0, 1.0).
	 *
	 * @param properties application configuration properties
	 * @return configured or default fusion weights
	 */
	public static FusionWeights defaults(GitContextProperties properties) {
		if (properties == null || properties.hybrid() == null) {
			return defaults();
		}
		return new FusionWeights(properties.hybrid().vectorWeight(), properties.hybrid().keywordWeight());
	}
}
