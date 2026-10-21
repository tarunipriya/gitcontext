package io.github.tarunipriya.gitcontext.retrieval;

import io.github.tarunipriya.gitcontext.indexing.SearchHit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Utility for fusing ranked result sets from dense vector and sparse lexical retrievers
 * using the Reciprocal Rank Fusion (RRF) algorithm.
 *
 * <p>Weighted RRF calculates the combined score as:
 * {@code score = vectorWeight / (rrfK + vectorRank) + keywordWeight / (rrfK + keywordRank)}
 * where ranks begin at 1. When weights are 1.0 and 1.0, this reduces to standard unweighted RRF.
 * Merges results by {@link SearchHit#chunkId()}.
 */
public final class ReciprocalRankFusion {

	private ReciprocalRankFusion() {
	}

	/**
	 * Fuses two ranked lists of search hits using unweighted Reciprocal Rank Fusion (weights 1.0, 1.0).
	 *
	 * @param vectorHits candidate hits retrieved from vector search
	 * @param keywordHits candidate hits retrieved from keyword search
	 * @param k maximum number of top fused hits to return
	 * @param rrfK smoothing constant k (e.g. 60)
	 * @return top k fused hits sorted in descending order of fused score
	 */
	public static List<HybridSearchHit> fuse(
			List<SearchHit> vectorHits,
			List<SearchHit> keywordHits,
			int k,
			int rrfK
	) {
		return fuse(vectorHits, keywordHits, k, rrfK, 1.0, 1.0);
	}

	/**
	 * Fuses two ranked lists of search hits using weighted Reciprocal Rank Fusion.
	 *
	 * <p>The fused score for each chunk is calculated as:
	 * {@code score = vectorWeight / (rrfK + vectorRank) + keywordWeight / (rrfK + keywordRank)}
	 * where ranks start at 1 for each retriever list.
	 *
	 * <p>Weights must be finite and &gt;= 0, and not both 0. A weight of 0 means that list
	 * contributes nothing to the fused score (contribution = 0.0), but its hits may still appear
	 * in the fused candidate set. Ordering remains deterministic by sorting in descending order
	 * of fused score and breaking ties by chunk ID.
	 *
	 * @param vectorHits candidate hits retrieved from vector search
	 * @param keywordHits candidate hits retrieved from keyword search
	 * @param k maximum number of top fused hits to return
	 * @param rrfK smoothing constant k (e.g. 60)
	 * @param vectorWeight weight multiplier applied to vector reciprocal rank contributions (&gt;= 0)
	 * @param keywordWeight weight multiplier applied to keyword reciprocal rank contributions (&gt;= 0)
	 * @return top k fused hits sorted in descending order of fused score
	 * @throws IllegalArgumentException if rrfK &lt;= 0, or if weights are negative, non-finite, or both 0
	 */
	public static List<HybridSearchHit> fuse(
			List<SearchHit> vectorHits,
			List<SearchHit> keywordHits,
			int k,
			int rrfK,
			double vectorWeight,
			double keywordWeight
	) {
		if (k <= 0) {
			return List.of();
		}
		if (rrfK <= 0) {
			throw new IllegalArgumentException("rrfK must be positive, got " + rrfK);
		}
		if (!Double.isFinite(vectorWeight) || vectorWeight < 0) {
			throw new IllegalArgumentException("vectorWeight must be finite and >= 0, got " + vectorWeight);
		}
		if (!Double.isFinite(keywordWeight) || keywordWeight < 0) {
			throw new IllegalArgumentException("keywordWeight must be finite and >= 0, got " + keywordWeight);
		}
		if (vectorWeight == 0.0 && keywordWeight == 0.0) {
			throw new IllegalArgumentException("vectorWeight and keywordWeight cannot both be 0");
		}

		List<SearchHit> safeVector = vectorHits != null ? vectorHits : List.of();
		List<SearchHit> safeKeyword = keywordHits != null ? keywordHits : List.of();

		Map<String, Builder> candidates = new LinkedHashMap<>();

		// Process vector hits (rank starting at 1)
		for (int i = 0; i < safeVector.size(); i++) {
			SearchHit hit = safeVector.get(i);
			int rank = i + 1;
			double contribution = vectorWeight / (rrfK + rank);
			String chunkId = hit.chunkId();
			Builder builder = candidates.computeIfAbsent(chunkId, id -> new Builder(hit));
			builder.vectorScore = hit.score();
			builder.vectorRank = rank;
			builder.fusedScore += contribution;
		}

		// Process keyword hits (rank starting at 1)
		for (int j = 0; j < safeKeyword.size(); j++) {
			SearchHit hit = safeKeyword.get(j);
			int rank = j + 1;
			double contribution = keywordWeight / (rrfK + rank);
			String chunkId = hit.chunkId();
			Builder builder = candidates.computeIfAbsent(chunkId, id -> new Builder(hit));
			builder.keywordScore = hit.score();
			builder.keywordRank = rank;
			builder.fusedScore += contribution;
		}

		return candidates.values().stream()
				.map(Builder::build)
				.sorted(Comparator.comparingDouble(HybridSearchHit::fusedScore).reversed()
						.thenComparing(h -> h.hit().chunkId()))
				.limit(k)
				.toList();
	}

	private static final class Builder {
		private final SearchHit hit;
		private double fusedScore = 0.0;
		private Double vectorScore = null;
		private Integer vectorRank = null;
		private Double keywordScore = null;
		private Integer keywordRank = null;

		private Builder(SearchHit hit) {
			this.hit = Objects.requireNonNull(hit);
		}

		private HybridSearchHit build() {
			return new HybridSearchHit(
					hit,
					fusedScore,
					vectorScore,
					vectorRank,
					keywordScore,
					keywordRank
			);
		}
	}
}
