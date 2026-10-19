package io.github.tarunipriya.gitcontext.retrieval.rerank;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.scoring.ScoringModel;
import io.github.tarunipriya.gitcontext.indexing.SearchHit;
import io.github.tarunipriya.gitcontext.model.ChunkType;
import io.github.tarunipriya.gitcontext.model.SourceType;
import io.github.tarunipriya.gitcontext.retrieval.HybridSearchHit;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CrossEncoderRerankerTest {

	private SearchHit createHit(String chunkId, String path, String content) {
		return new SearchHit(
				"id-" + chunkId,
				0.5,
				"demo/repo",
				"run1",
				chunkId,
				"doc-" + chunkId,
				0,
				SourceType.CODE,
				ChunkType.FUNCTION,
				path,
				"java",
				"fn",
				null,
				null,
				1,
				20,
				30,
				"https://github.com/repo/demo/blob/main/" + path,
				"sha1",
				content,
				content
		);
	}

	@Test
	@DisplayName("Reorders candidate hits in descending order of rerank score while preserving original fused rank")
	void testRerankerOrderingAndFusedRank() {
		ScoringModel fakeModel = mock(ScoringModel.class);
		CrossEncoderReranker reranker = new CrossEncoderReranker(fakeModel, 100);

		// Candidate 1: fused rank 1
		HybridSearchHit candidate1 = new HybridSearchHit(createHit("c1", "Foo.java", "first content"), 0.9, null, null, null, null);
		// Candidate 2: fused rank 2
		HybridSearchHit candidate2 = new HybridSearchHit(createHit("c2", "Bar.java", "second content"), 0.8, null, null, null, null);
		// Candidate 3: fused rank 3
		HybridSearchHit candidate3 = new HybridSearchHit(createHit("c3", "Baz.java", "third content"), 0.7, null, null, null, null);

		List<HybridSearchHit> candidates = List.of(candidate1, candidate2, candidate3);

		// Fake model scores: candidate 1 -> 0.1, candidate 2 -> 0.95, candidate 3 -> 0.4
		when(fakeModel.scoreAll(anyList(), eq("test query")))
				.thenReturn(Response.from(List.of(0.1, 0.95, 0.4)));

		List<RerankedHit> results = reranker.rerank("test query", candidates);

		assertThat(results).hasSize(3);

		// 1st result should be candidate 2 (score 0.95, original fusedRank 2)
		assertThat(results.get(0).candidate().hit().chunkId()).isEqualTo("c2");
		assertThat(results.get(0).rerankScore()).isEqualTo(0.95);
		assertThat(results.get(0).fusedRank()).isEqualTo(2);

		// 2nd result should be candidate 3 (score 0.4, original fusedRank 3)
		assertThat(results.get(1).candidate().hit().chunkId()).isEqualTo("c3");
		assertThat(results.get(1).rerankScore()).isEqualTo(0.4);
		assertThat(results.get(1).fusedRank()).isEqualTo(3);

		// 3rd result should be candidate 1 (score 0.1, original fusedRank 1)
		assertThat(results.get(2).candidate().hit().chunkId()).isEqualTo("c1");
		assertThat(results.get(2).rerankScore()).isEqualTo(0.1);
		assertThat(results.get(2).fusedRank()).isEqualTo(1);
	}

	@Test
	@DisplayName("Truncates chunk text exceeding maxTextChars before scoring in one batch")
	@SuppressWarnings("unchecked")
	void testTextTruncationToMaxChars() {
		ScoringModel fakeModel = mock(ScoringModel.class);
		int maxChars = 20;
		CrossEncoderReranker reranker = new CrossEncoderReranker(fakeModel, maxChars);

		String longContent = "12345678901234567890EXTRA_TEXT_EXCEEDING_LIMIT";
		String shortContent = "short";

		HybridSearchHit candidate1 = new HybridSearchHit(createHit("c1", "A.java", longContent), 0.5, null, null, null, null);
		HybridSearchHit candidate2 = new HybridSearchHit(createHit("c2", "B.java", shortContent), 0.4, null, null, null, null);

		when(fakeModel.scoreAll(anyList(), eq("query")))
				.thenReturn(Response.from(List.of(0.8, 0.6)));

		reranker.rerank("query", List.of(candidate1, candidate2));

		ArgumentCaptor<List<TextSegment>> captor = ArgumentCaptor.forClass(List.class);
		verify(fakeModel).scoreAll(captor.capture(), eq("query"));

		List<TextSegment> segmentsPassed = captor.getValue();
		assertThat(segmentsPassed).hasSize(2);

		// Candidate 1 should be truncated to exactly maxChars (20)
		assertThat(segmentsPassed.get(0).text()).hasSize(maxChars);
		assertThat(segmentsPassed.get(0).text()).isEqualTo("12345678901234567890");

		// Candidate 2 should remain untouched
		assertThat(segmentsPassed.get(1).text()).isEqualTo("short");
	}

	@Test
	@DisplayName("Returns empty list when input candidates list is empty or null")
	void testEmptyCandidates() {
		ScoringModel fakeModel = mock(ScoringModel.class);
		CrossEncoderReranker reranker = new CrossEncoderReranker(fakeModel, 100);

		assertThat(reranker.rerank("query", List.of())).isEmpty();
		assertThat(reranker.rerank("query", null)).isEmpty();
	}
}
