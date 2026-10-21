package io.github.tarunipriya.gitcontext.retrieval;

import io.github.tarunipriya.gitcontext.config.GitContextProperties;
import io.github.tarunipriya.gitcontext.indexing.SearchHit;
import io.github.tarunipriya.gitcontext.indexing.VectorSearchResult;
import io.github.tarunipriya.gitcontext.indexing.VectorStore;
import io.github.tarunipriya.gitcontext.indexing.qdrant.QdrantUnavailableException;
import io.github.tarunipriya.gitcontext.model.ChunkType;
import io.github.tarunipriya.gitcontext.model.RepoRef;
import io.github.tarunipriya.gitcontext.model.SourceType;
import io.github.tarunipriya.gitcontext.retrieval.keyword.KeywordIndex;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HybridSearchServiceTest {

	@Mock
	private VectorStore vectorStore;

	@Mock
	private KeywordIndex keywordIndex;

	@Mock
	private io.github.tarunipriya.gitcontext.retrieval.rerank.Reranker reranker;

	private HybridSearchService hybridSearchService;
	private final RepoRef repo = new RepoRef("testowner", "testrepo");
	private GitContextProperties props;

	@BeforeEach
	void setUp() {
		GitContextProperties.HybridProperties hybridProps = new GitContextProperties.HybridProperties(
				50,
				60,
				SearchMode.HYBRID,
				1.0,
				1.0
		);
		GitContextProperties.RerankProperties rerankProps = new GitContextProperties.RerankProperties(
				false,
				"ms-marco-MiniLM-L-6-v2",
				"http://localhost/model.onnx",
				"http://localhost/tokenizer.json",
				30,
				1000
		);
		props = new GitContextProperties(
				null,
				Path.of("./data/repos"),
				"http://localhost:6333",
				null,
				null,
				null,
				null,
				null,
				null,
				null,
				hybridProps,
				rerankProps
		);
		hybridSearchService = new HybridSearchService(vectorStore, keywordIndex, reranker, props);
	}

	private SearchHit createHit(String chunkId, double score, String symbolName) {
		return new SearchHit(
				"id-" + chunkId,
				score,
				repo.normalizedKey(),
				"run1",
				chunkId,
				"doc-" + chunkId,
				0,
				SourceType.CODE,
				ChunkType.METHOD,
				"src/main/java/" + chunkId + ".java",
				"java",
				symbolName,
				null,
				null,
				1,
				20,
				30,
				"url",
				"sha",
				"text",
				"content"
		);
	}

	@Test
	@DisplayName("VECTOR mode executes vectorStore search and returns vector scores with timings")
	void search_vectorMode_returnsVectorResults() {
		SearchHit hit = createHit("c1", 0.88, "handleRateLimit");
		VectorSearchResult vResult = new VectorSearchResult(List.of(hit), 5L, 10L, 18L);
		when(vectorStore.searchWithTimings(eq(repo), eq("rate limit"), eq(5), any(), any()))
				.thenReturn(vResult);

		HybridSearchResult result = hybridSearchService.search(repo, "rate limit", 5, null, null, SearchMode.VECTOR);

		assertThat(result.mode()).isEqualTo(SearchMode.VECTOR);
		assertThat(result.hits()).hasSize(1);
		assertThat(result.hits().get(0).vectorScore()).isEqualTo(0.88);
		assertThat(result.hits().get(0).vectorRank()).isEqualTo(1);
		assertThat(result.hits().get(0).keywordScore()).isNull();
		assertThat(result.hits().get(0).keywordRank()).isNull();
		assertThat(result.hits().get(0).fusedScore()).isNull();
		assertThat(result.warnings()).isEmpty();
		assertThat(result.vectorMillis()).isEqualTo(15L);

		verify(keywordIndex, never()).search(any(), any(), anyInt(), any(), any());
	}

	@Test
	@DisplayName("KEYWORD mode executes keywordIndex search and returns keyword scores with timings")
	void search_keywordMode_returnsKeywordResults() {
		SearchHit hit = createHit("c2", 14.5, "checkRateLimit");
		when(keywordIndex.search(eq(repo), eq("rate limit"), eq(5), any(), any()))
				.thenReturn(List.of(hit));

		HybridSearchResult result = hybridSearchService.search(repo, "rate limit", 5, null, null, SearchMode.KEYWORD);

		assertThat(result.mode()).isEqualTo(SearchMode.KEYWORD);
		assertThat(result.hits()).hasSize(1);
		assertThat(result.hits().get(0).keywordScore()).isEqualTo(14.5);
		assertThat(result.hits().get(0).keywordRank()).isEqualTo(1);
		assertThat(result.hits().get(0).vectorScore()).isNull();
		assertThat(result.hits().get(0).vectorRank()).isNull();
		assertThat(result.hits().get(0).fusedScore()).isNull();
		assertThat(result.warnings()).isEmpty();
		assertThat(result.keywordMillis()).isGreaterThanOrEqualTo(0L);

		verify(vectorStore, never()).search(any(), any(), anyInt(), any(), any());
	}

	@Test
	@DisplayName("HYBRID mode executes both retrievers and fuses results with RRF")
	void search_hybridMode_fusesResults() {
		SearchHit hit1 = createHit("c1", 0.90, "sym1");
		SearchHit hit2 = createHit("c2", 0.80, "sym2");
		when(vectorStore.search(eq(repo), eq("query"), eq(50), any(), any()))
				.thenReturn(List.of(hit1, hit2));

		when(keywordIndex.exists(repo)).thenReturn(true);
		SearchHit hit2Keyword = createHit("c2", 20.0, "sym2");
		SearchHit hit3 = createHit("c3", 10.0, "sym3");
		when(keywordIndex.search(eq(repo), eq("query"), eq(50), any(), any()))
				.thenReturn(List.of(hit2Keyword, hit3));

		HybridSearchResult result = hybridSearchService.search(repo, "query", 5, null, null, SearchMode.HYBRID);

		assertThat(result.mode()).isEqualTo(SearchMode.HYBRID);
		assertThat(result.warnings()).isEmpty();
		assertThat(result.hits()).hasSize(3);

		// c2 is in both vector and keyword -> ranks first
		assertThat(result.hits().get(0).hit().chunkId()).isEqualTo("c2");
		assertThat(result.hits().get(0).fusedScore()).isNotNull();
		assertThat(result.hits().get(0).vectorScore()).isEqualTo(0.80);
		assertThat(result.hits().get(0).vectorRank()).isEqualTo(2);
		assertThat(result.hits().get(0).keywordScore()).isEqualTo(20.0);
		assertThat(result.hits().get(0).keywordRank()).isEqualTo(1);
	}

	@Test
	@DisplayName("Fallback to keyword-only when VectorStore throws QdrantUnavailableException in HYBRID mode")
	void search_hybridMode_qdrantUnavailable_fallbackToKeywordOnly() {
		when(vectorStore.search(eq(repo), eq("rate limit"), eq(50), any(), any()))
				.thenThrow(new QdrantUnavailableException("http://localhost:6333", new RuntimeException("connection refused")));

		when(keywordIndex.exists(repo)).thenReturn(true);
		SearchHit hit = createHit("c1", 12.0, "checkRateLimit");
		when(keywordIndex.search(eq(repo), eq("rate limit"), eq(50), any(), any()))
				.thenReturn(List.of(hit));

		HybridSearchResult result = hybridSearchService.search(repo, "rate limit", 5, null, null, SearchMode.HYBRID);

		assertThat(result.hits()).hasSize(1);
		assertThat(result.hits().get(0).keywordScore()).isEqualTo(12.0);
		assertThat(result.hits().get(0).vectorScore()).isNull();
		assertThat(result.warnings()).anyMatch(w -> w.contains("Vector search unavailable") && w.contains("Qdrant error"));
	}

	@Test
	@DisplayName("Fallback to vector-only when KeywordIndex does not exist in HYBRID mode")
	void search_hybridMode_keywordIndexMissing_fallbackToVectorOnly() {
		SearchHit hit = createHit("c1", 0.92, "checkRateLimit");
		when(vectorStore.search(eq(repo), eq("rate limit"), eq(50), any(), any()))
				.thenReturn(List.of(hit));

		when(keywordIndex.exists(repo)).thenReturn(false);

		HybridSearchResult result = hybridSearchService.search(repo, "rate limit", 5, null, null, SearchMode.HYBRID);

		assertThat(result.hits()).hasSize(1);
		assertThat(result.hits().get(0).vectorScore()).isEqualTo(0.92);
		assertThat(result.hits().get(0).keywordScore()).isNull();
		assertThat(result.warnings()).anyMatch(w -> w.contains("Keyword index missing") && w.contains("testowner/testrepo"));
	}

	@Test
	@DisplayName("Rerank ON invokes Reranker and orders top-k results by rerank score with metadata")
	void search_hybridMode_rerankOn_invokesRerankerAndReturnsTopK() {
		SearchHit hit1 = createHit("c1", 0.90, "sym1");
		SearchHit hit2 = createHit("c2", 0.80, "sym2");
		when(vectorStore.search(eq(repo), eq("query"), eq(50), any(), any()))
				.thenReturn(List.of(hit1, hit2));

		when(keywordIndex.exists(repo)).thenReturn(true);
		when(keywordIndex.search(eq(repo), eq("query"), eq(50), any(), any()))
				.thenReturn(List.of(hit2));

		// In fusion, c2 has higher score than c1.
		// When reranking, c1 gets 0.99 (fused rank 2), c2 gets 0.40 (fused rank 1).
		when(reranker.rerank(eq("query"), any()))
				.thenAnswer(inv -> {
					List<HybridSearchHit> candidates = inv.getArgument(1);
					return List.of(
							new io.github.tarunipriya.gitcontext.retrieval.rerank.RerankedHit(candidates.get(1), 0.99, 2),
							new io.github.tarunipriya.gitcontext.retrieval.rerank.RerankedHit(candidates.get(0), 0.40, 1)
					);
				});

		HybridSearchResult result = hybridSearchService.search(
				repo, "query", 5, null, null, SearchMode.HYBRID, true, false);

		assertThat(result.reranked()).isTrue();
		assertThat(result.rerankMillis()).isGreaterThanOrEqualTo(0L);
		assertThat(result.hits()).hasSize(2);

		// c1 was reranked to #1
		assertThat(result.hits().get(0).hit().chunkId()).isEqualTo("c1");
		assertThat(result.hits().get(0).rerankScore()).isEqualTo(0.99);
		assertThat(result.hits().get(0).fusedRank()).isEqualTo(2);

		// c2 was reranked to #2
		assertThat(result.hits().get(1).hit().chunkId()).isEqualTo("c2");
		assertThat(result.hits().get(1).rerankScore()).isEqualTo(0.40);
		assertThat(result.hits().get(1).fusedRank()).isEqualTo(1);
	}

	@Test
	@DisplayName("Rerank OFF leaves results un-reranked and does not call Reranker")
	void search_hybridMode_rerankOff_skipsReranker() {
		SearchHit hit1 = createHit("c1", 0.90, "sym1");
		when(vectorStore.search(eq(repo), eq("query"), eq(50), any(), any()))
				.thenReturn(List.of(hit1));
		when(keywordIndex.exists(repo)).thenReturn(true);
		when(keywordIndex.search(eq(repo), eq("query"), eq(50), any(), any()))
				.thenReturn(List.of());

		HybridSearchResult result = hybridSearchService.search(
				repo, "query", 5, null, null, SearchMode.HYBRID, false, false);

		assertThat(result.reranked()).isFalse();
		assertThat(result.rerankMillis()).isEqualTo(0L);
		assertThat(result.hits()).hasSize(1);
		assertThat(result.hits().get(0).rerankScore()).isNull();
		assertThat(result.hits().get(0).fusedRank()).isNull();

		verify(reranker, never()).rerank(any(), any());
	}

	@Test
	@DisplayName("Fallback with warning when the reranker throws an exception")
	void search_hybridMode_rerankerThrows_fallbackWithWarning() {
		SearchHit hit1 = createHit("c1", 0.90, "sym1");
		when(vectorStore.search(eq(repo), eq("query"), eq(50), any(), any()))
				.thenReturn(List.of(hit1));
		when(keywordIndex.exists(repo)).thenReturn(true);
		when(keywordIndex.search(eq(repo), eq("query"), eq(50), any(), any()))
				.thenReturn(List.of());

		when(reranker.rerank(eq("query"), any()))
				.thenThrow(new RuntimeException("ONNX native runtime error"));

		HybridSearchResult result = hybridSearchService.search(
				repo, "query", 5, null, null, SearchMode.HYBRID, true, false);

		assertThat(result.reranked()).isFalse();
		assertThat(result.hits()).hasSize(1);
		assertThat(result.hits().get(0).hit().chunkId()).isEqualTo("c1");
		assertThat(result.warnings()).anyMatch(w -> w.contains("Reranking skipped: ONNX native runtime error"));
	}

	@Test
	@DisplayName("Only candidates hits are sent to the reranker")
	void search_hybridMode_onlyCandidatesHitsSentToReranker() {
		// Create 40 hits from vector and keyword
		List<SearchHit> manyHits = new java.util.ArrayList<>();
		for (int i = 0; i < 40; i++) {
			manyHits.add(createHit("chunk-" + i, 0.99 - (i * 0.01), "sym" + i));
		}
		when(vectorStore.search(eq(repo), eq("query"), eq(50), any(), any()))
				.thenReturn(manyHits);
		when(keywordIndex.exists(repo)).thenReturn(true);
		when(keywordIndex.search(eq(repo), eq("query"), eq(50), any(), any()))
				.thenReturn(List.of());

		when(reranker.rerank(eq("query"), any()))
				.thenAnswer(inv -> {
					List<HybridSearchHit> candidates = inv.getArgument(1);
					// Verify that exactly 30 candidates (from rerank.candidates config) are sent
					assertThat(candidates).hasSize(30);
					return List.of(new io.github.tarunipriya.gitcontext.retrieval.rerank.RerankedHit(candidates.get(0), 0.9, 1));
				});

		hybridSearchService.search(repo, "query", 5, null, null, SearchMode.HYBRID, true, false);

		verify(reranker).rerank(eq("query"), any());
	}

	@Test
	@DisplayName("excludeTests filters test chunks and returns k results when enough non-test hits exist")
	void search_hybridMode_excludeTests_filtersTestChunksAndReturnsKResults() {
		List<SearchHit> hits = List.of(
				createHitWithPath("c1", "src/test/java/ServiceTest.java"),
				createHitWithPath("c2", "src/main/java/Service.java"),
				createHitWithPath("c3", "src/tests/test_util.py"),
				createHitWithPath("c4", "src/main/java/Util.java"),
				createHitWithPath("c5", "src/main/java/RepoTest.java"),
				createHitWithPath("c6", "src/main/java/Repo.java"),
				createHitWithPath("c7", "frontend/App.test.ts"),
				createHitWithPath("c8", "frontend/App.tsx"),
				createHitWithPath("c9", "src/main/java/Config.java"),
				createHitWithPath("c10", "src/main/java/Client.java")
		);

		when(vectorStore.search(eq(repo), eq("query"), eq(50), any(), any()))
				.thenReturn(hits);
		when(keywordIndex.exists(repo)).thenReturn(true);
		when(keywordIndex.search(eq(repo), eq("query"), eq(50), any(), any()))
				.thenReturn(List.of());

		// Request top 5 with excludeTests=true (rerank=false)
		HybridSearchResult result = hybridSearchService.search(
				repo, "query", 5, null, null, SearchMode.HYBRID, false, true);

		// Exactly 5 results should be returned
		assertThat(result.hits()).hasSize(5);

		// None of the results should be test paths
		for (HybridSearchHit hit : result.hits()) {
			assertThat(TestFileFilter.isTestPath(hit.hit().path())).isFalse();
		}

		// The non-test paths in order: c2, c4, c6, c8, c9
		List<String> chunkIds = result.hits().stream().map(h -> h.hit().chunkId()).toList();
		assertThat(chunkIds).containsExactly("c2", "c4", "c6", "c8", "c9");
	}

	@Test
	@DisplayName("search overload with FusionWeights passes weights through to change fused order")
	void search_hybridMode_withCustomFusionWeights_passesWeightsThrough() {
		// Vector: c1 (rank 1), c2 (rank 2)
		SearchHit hit1 = createHit("c1", 0.95, "sym1");
		SearchHit hit2 = createHit("c2", 0.85, "sym2");
		when(vectorStore.search(eq(repo), eq("weightQuery"), eq(50), any(), any()))
				.thenReturn(List.of(hit1, hit2));

		// Keyword: c2 (rank 1), c3 (rank 2), c1 (rank 3)
		when(keywordIndex.exists(repo)).thenReturn(true);
		SearchHit hit2Key = createHit("c2", 25.0, "sym2");
		SearchHit hit3Key = createHit("c3", 20.0, "sym3");
		SearchHit hit1Key = createHit("c1", 10.0, "sym1");
		when(keywordIndex.search(eq(repo), eq("weightQuery"), eq(50), any(), any()))
				.thenReturn(List.of(hit2Key, hit3Key, hit1Key));

		// 1. With default weights (null weights -> 1.0, 1.0), c2 outranks c1:
		// c2: 1/62 + 1/61 ≈ 0.03252
		// c1: 1/61 + 1/63 ≈ 0.03227
		HybridSearchResult defaultResult = hybridSearchService.search(
				repo, "weightQuery", 5, null, null, SearchMode.HYBRID, false, false, null);
		List<String> defaultOrder = defaultResult.hits().stream().map(h -> h.hit().chunkId()).toList();
		assertThat(defaultOrder).containsExactly("c2", "c1", "c3");

		// 2. With vectorWeight=1.0 and keywordWeight=0.0, vector ranks dominate:
		// c1: 1/61 + 0 = 0.01639
		// c2: 1/62 + 0 = 0.01613
		// c3: 0 + 0 = 0.0
		FusionWeights customWeights = new FusionWeights(1.0, 0.0);
		HybridSearchResult weightedResult = hybridSearchService.search(
				repo, "weightQuery", 5, null, null, SearchMode.HYBRID, false, false, customWeights);
		List<String> weightedOrder = weightedResult.hits().stream().map(h -> h.hit().chunkId()).toList();
		assertThat(weightedOrder).containsExactly("c1", "c2", "c3");
	}

	private SearchHit createHitWithPath(String chunkId, String path) {
		return new SearchHit(
				"id-" + chunkId,
				0.8,
				repo.normalizedKey(),
				"run1",
				chunkId,
				"doc-" + chunkId,
				0,
				SourceType.CODE,
				ChunkType.METHOD,
				path,
				"java",
				"sym",
				null,
				null,
				1,
				20,
				30,
				"url",
				"sha",
				"text",
				"content"
		);
	}
}
