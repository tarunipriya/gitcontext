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

	private HybridSearchService hybridSearchService;
	private final RepoRef repo = new RepoRef("testowner", "testrepo");

	@BeforeEach
	void setUp() {
		GitContextProperties.HybridProperties hybridProps = new GitContextProperties.HybridProperties(
				50,
				60,
				SearchMode.HYBRID
		);
		GitContextProperties props = new GitContextProperties(
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
				hybridProps
		);
		hybridSearchService = new HybridSearchService(vectorStore, keywordIndex, props);
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
}
