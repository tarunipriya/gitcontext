package io.github.tarunipriya.gitcontext.retrieval;

import io.github.tarunipriya.gitcontext.indexing.SearchHit;
import io.github.tarunipriya.gitcontext.model.ChunkType;
import io.github.tarunipriya.gitcontext.model.SourceType;
import java.util.List;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReciprocalRankFusionTest {

	private SearchHit createHit(String chunkId, double score) {
		return new SearchHit(
				"id-" + chunkId,
				score,
				"owner/repo",
				"run1",
				chunkId,
				"doc-" + chunkId,
				0,
				SourceType.CODE,
				ChunkType.METHOD,
				"src/" + chunkId + ".java",
				"java",
				"symbol" + chunkId,
				null,
				null,
				1,
				10,
				20,
				"url",
				"sha",
				"text",
				"content"
		);
	}

	@Test
	@DisplayName("Hand-computed RRF example produces exact expected fused scores and ranked order")
	void fuse_handComputedExample_matchesExactScoresAndOrder() {
		// rrfK = 60
		// Vector: A (rank 1), B (rank 2), C (rank 3)
		SearchHit hitA = createHit("A", 0.95);
		SearchHit hitB = createHit("B", 0.85);
		SearchHit hitC = createHit("C", 0.75);
		List<SearchHit> vectorHits = List.of(hitA, hitB, hitC);

		// Keyword: B (rank 1), D (rank 2), A (rank 3)
		SearchHit hitBKeyword = createHit("B", 15.0);
		SearchHit hitD = createHit("D", 12.0);
		SearchHit hitAKeyword = createHit("A", 10.0);
		List<SearchHit> keywordHits = List.of(hitBKeyword, hitD, hitAKeyword);

		int rrfK = 60;
		List<HybridSearchHit> fused = ReciprocalRankFusion.fuse(vectorHits, keywordHits, 10, rrfK);

		assertThat(fused).hasSize(4);

		// 1. Chunk B: 1/(60+2) + 1/(60+1) = 1/62 + 1/61 = 123 / 3782 ≈ 0.03252247488099418
		double expectedB = (1.0 / 62.0) + (1.0 / 61.0);
		assertThat(fused.get(0).hit().chunkId()).isEqualTo("B");
		assertThat(fused.get(0).fusedScore()).isCloseTo(expectedB, Offset.offset(1e-9));
		assertThat(fused.get(0).vectorRank()).isEqualTo(2);
		assertThat(fused.get(0).vectorScore()).isEqualTo(0.85);
		assertThat(fused.get(0).keywordRank()).isEqualTo(1);
		assertThat(fused.get(0).keywordScore()).isEqualTo(15.0);

		// 2. Chunk A: 1/(60+1) + 1/(60+3) = 1/61 + 1/63 = 124 / 3843 ≈ 0.03226645849596669
		double expectedA = (1.0 / 61.0) + (1.0 / 63.0);
		assertThat(fused.get(1).hit().chunkId()).isEqualTo("A");
		assertThat(fused.get(1).fusedScore()).isCloseTo(expectedA, Offset.offset(1e-9));
		assertThat(fused.get(1).vectorRank()).isEqualTo(1);
		assertThat(fused.get(1).vectorScore()).isEqualTo(0.95);
		assertThat(fused.get(1).keywordRank()).isEqualTo(3);
		assertThat(fused.get(1).keywordScore()).isEqualTo(10.0);

		// 3. Chunk D: 1/(60+2) = 1/62 ≈ 0.016129032258064516 (only in keyword)
		double expectedD = 1.0 / 62.0;
		assertThat(fused.get(2).hit().chunkId()).isEqualTo("D");
		assertThat(fused.get(2).fusedScore()).isCloseTo(expectedD, Offset.offset(1e-9));
		assertThat(fused.get(2).vectorRank()).isNull();
		assertThat(fused.get(2).vectorScore()).isNull();
		assertThat(fused.get(2).keywordRank()).isEqualTo(2);
		assertThat(fused.get(2).keywordScore()).isEqualTo(12.0);

		// 4. Chunk C: 1/(60+3) = 1/63 ≈ 0.015873015873015872 (only in vector)
		double expectedC = 1.0 / 63.0;
		assertThat(fused.get(3).hit().chunkId()).isEqualTo("C");
		assertThat(fused.get(3).fusedScore()).isCloseTo(expectedC, Offset.offset(1e-9));
		assertThat(fused.get(3).vectorRank()).isEqualTo(3);
		assertThat(fused.get(3).vectorScore()).isEqualTo(0.75);
		assertThat(fused.get(3).keywordRank()).isNull();
		assertThat(fused.get(3).keywordScore()).isNull();
	}

	@Test
	@DisplayName("A chunk appearing in both lists outranks chunks appearing in only one list")
	void fuse_chunkInBothLists_outranksChunkInOnlyOne() {
		// Vector: X (rank 1), Y (rank 2)
		SearchHit hitX = createHit("X", 0.90);
		SearchHit hitY = createHit("Y", 0.80);
		List<SearchHit> vectorHits = List.of(hitX, hitY);

		// Keyword: Z (rank 1), X (rank 2)
		SearchHit hitZ = createHit("Z", 20.0);
		SearchHit hitXKeyword = createHit("X", 10.0);
		List<SearchHit> keywordHits = List.of(hitZ, hitXKeyword);

		List<HybridSearchHit> fused = ReciprocalRankFusion.fuse(vectorHits, keywordHits, 5, 60);

		// X is rank 1 in vector, rank 2 in keyword -> 1/61 + 1/62 ≈ 0.0325
		// Z is rank 1 in keyword -> 1/61 ≈ 0.01639
		// Y is rank 2 in vector -> 1/62 ≈ 0.01612
		assertThat(fused.get(0).hit().chunkId()).isEqualTo("X");
		assertThat(fused.get(0).fusedScore()).isGreaterThan(fused.get(1).fusedScore());
		assertThat(fused.get(0).fusedScore()).isGreaterThan(fused.get(2).fusedScore());
	}
}
