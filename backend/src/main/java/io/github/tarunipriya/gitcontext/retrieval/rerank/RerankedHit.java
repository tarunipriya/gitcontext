package io.github.tarunipriya.gitcontext.retrieval.rerank;

import io.github.tarunipriya.gitcontext.retrieval.HybridSearchHit;
import java.util.Objects;

/**
 * Result hit scored and ranked by a cross-encoder model, preserving the original fused rank.
 *
 * @param candidate the underlying hybrid search hit
 * @param rerankScore relevance score computed by the cross-encoder model
 * @param fusedRank 1-based rank position in the candidate list before reranking
 */
public record RerankedHit(
		HybridSearchHit candidate,
		double rerankScore,
		int fusedRank
) {
	public RerankedHit {
		Objects.requireNonNull(candidate, "candidate must not be null");
	}

	/**
	 * Convenience accessor returning the underlying {@link HybridSearchHit}.
	 *
	 * @return underlying candidate hit
	 */
	public HybridSearchHit hit() {
		return candidate;
	}
}
