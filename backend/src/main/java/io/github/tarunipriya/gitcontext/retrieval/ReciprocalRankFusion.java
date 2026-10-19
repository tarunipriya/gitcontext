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
 * <p>RRF calculates score as {@code sum(1.0 / (rrfK + rank))} where rank begins at 1.
 * Merges results by {@link SearchHit#chunkId()}.
 */
public final class ReciprocalRankFusion {

	private ReciprocalRankFusion() {
	}

	/**
	 * Fuses two ranked lists of search hits using Reciprocal Rank Fusion.
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
		if (k <= 0) {
			return List.of();
		}
		if (rrfK <= 0) {
			throw new IllegalArgumentException("rrfK must be positive, got " + rrfK);
		}

		List<SearchHit> safeVector = vectorHits != null ? vectorHits : List.of();
		List<SearchHit> safeKeyword = keywordHits != null ? keywordHits : List.of();

		Map<String, Builder> candidates = new LinkedHashMap<>();

		// Process vector hits (rank starting at 1)
		for (int i = 0; i < safeVector.size(); i++) {
			SearchHit hit = safeVector.get(i);
			int rank = i + 1;
			double contribution = 1.0 / (rrfK + rank);
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
			double contribution = 1.0 / (rrfK + rank);
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
