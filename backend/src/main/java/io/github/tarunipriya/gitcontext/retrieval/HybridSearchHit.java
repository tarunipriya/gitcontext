package io.github.tarunipriya.gitcontext.retrieval;

import io.github.tarunipriya.gitcontext.indexing.SearchHit;
import java.util.Objects;

/**
 * Result hit produced by hybrid search or individual retrievers, carrying vector and keyword scores and ranks.
 *
 * @param hit underlying search hit containing chunk metadata and content
 * @param fusedScore reciprocal rank fusion score (null if retrieved in non-hybrid mode without fusion)
 * @param vectorScore dense vector similarity score from Qdrant (null if absent from vector results)
 * @param vectorRank 1-based rank in vector search candidate list (null if absent from vector results)
 * @param keywordScore sparse lexical BM25 score from Lucene (null if absent from keyword results)
 * @param keywordRank 1-based rank in keyword search candidate list (null if absent from keyword results)
 */
public record HybridSearchHit(
		SearchHit hit,
		Double fusedScore,
		Double vectorScore,
		Integer vectorRank,
		Double keywordScore,
		Integer keywordRank
) {
	public HybridSearchHit {
		Objects.requireNonNull(hit, "hit must not be null");
	}

	/**
	 * Returns the primary score to display, preferring fusedScore, then vectorScore, then keywordScore, then hit score.
	 *
	 * @return primary relevance score
	 */
	public double primaryScore() {
		if (fusedScore != null) {
			return fusedScore;
		}
		if (vectorScore != null) {
			return vectorScore;
		}
		if (keywordScore != null) {
			return keywordScore;
		}
		return hit.score();
	}
}
