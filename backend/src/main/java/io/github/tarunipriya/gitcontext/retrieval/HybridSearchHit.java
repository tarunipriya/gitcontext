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
 * @param rerankScore cross-encoder relevance score (null if reranking was not performed)
 * @param fusedRank 1-based rank position in the fused candidate list prior to reranking (null if not reranked)
 */
public record HybridSearchHit(
		SearchHit hit,
		Double fusedScore,
		Double vectorScore,
		Integer vectorRank,
		Double keywordScore,
		Integer keywordRank,
		Double rerankScore,
		Integer fusedRank
) {
	public HybridSearchHit {
		Objects.requireNonNull(hit, "hit must not be null");
	}

	/**
	 * Backwards-compatible constructor without reranking fields.
	 */
	public HybridSearchHit(
			SearchHit hit,
			Double fusedScore,
			Double vectorScore,
			Integer vectorRank,
			Double keywordScore,
			Integer keywordRank
	) {
		this(hit, fusedScore, vectorScore, vectorRank, keywordScore, keywordRank, null, null);
	}

	/**
	 * Returns a copy of this hit carrying cross-encoder reranking metadata.
	 *
	 * @param score cross-encoder relevance score
	 * @param rank 1-based fused candidate rank before reranking
	 * @return updated hybrid search hit
	 */
	public HybridSearchHit withRerank(double score, int rank) {
		return new HybridSearchHit(
				hit,
				fusedScore,
				vectorScore,
				vectorRank,
				keywordScore,
				keywordRank,
				score,
				rank
		);
	}

	/**
	 * Returns the primary score to display, preferring rerankScore, then fusedScore, then vectorScore,
	 * then keywordScore, then hit score.
	 *
	 * @return primary relevance score
	 */
	public double primaryScore() {
		if (rerankScore != null) {
			return rerankScore;
		}
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
