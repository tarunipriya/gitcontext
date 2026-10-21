package io.github.tarunipriya.gitcontext.api;

import io.github.tarunipriya.gitcontext.config.GitContextProperties;
import io.github.tarunipriya.gitcontext.indexing.SearchHit;
import io.github.tarunipriya.gitcontext.indexing.VectorStore;
import io.github.tarunipriya.gitcontext.indexing.qdrant.QdrantUnavailableException;
import io.github.tarunipriya.gitcontext.model.ChunkType;
import io.github.tarunipriya.gitcontext.model.RepoRef;
import io.github.tarunipriya.gitcontext.model.SourceType;
import io.github.tarunipriya.gitcontext.retrieval.HybridSearchHit;
import io.github.tarunipriya.gitcontext.retrieval.HybridSearchResult;
import io.github.tarunipriya.gitcontext.retrieval.HybridSearchService;
import io.github.tarunipriya.gitcontext.retrieval.SearchMode;
import io.github.tarunipriya.gitcontext.retrieval.keyword.KeywordIndex;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SearchController.class)
class SearchControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private HybridSearchService hybridSearchService;

	@MockitoBean
	private VectorStore vectorStore;

	@MockitoBean
	private KeywordIndex keywordIndex;

	@MockitoBean
	private GitContextProperties properties;

	private final RepoRef testRepo = new RepoRef("testowner", "testrepo");

	@BeforeEach
	void setUp() {
		GitContextProperties.HybridProperties hybridConfig = new GitContextProperties.HybridProperties(
				50,
				60,
				SearchMode.HYBRID,
				1.0,
				1.0
		);
		GitContextProperties.RerankProperties rerankConfig = new GitContextProperties.RerankProperties(
				false,
				"ms-marco-MiniLM-L-6-v2",
				"http://localhost/model.onnx",
				"http://localhost/tokenizer.json",
				30,
				1000
		);
		when(properties.hybrid()).thenReturn(hybridConfig);
		when(properties.rerank()).thenReturn(rerankConfig);
	}

	@Test
	@DisplayName("GET /search happy path returns mapped response with 3-decimal score, snippet, and timings")
	void search_happyPath_returnsMappedResponse() throws Exception {
		when(vectorStore.countRepoPoints(eq(testRepo))).thenReturn(5L);
		when(keywordIndex.exists(eq(testRepo))).thenReturn(true);

		StringBuilder contentBuilder = new StringBuilder();
		for (int i = 1; i <= 25; i++) {
			contentBuilder.append("line ").append(i).append("\n");
		}
		String rawContent = contentBuilder.toString().stripTrailing();

		SearchHit hit = new SearchHit(
				"point-uuid",
				0.852345,
				testRepo.normalizedKey(),
				"run-1",
				"chunk-sha",
				"doc-1",
				0,
				SourceType.CODE,
				ChunkType.METHOD,
				"src/main/java/GitHubClient.java",
				"java",
				"handleRateLimits",
				"GitHubClient",
				"Client / Limits",
				10,
				25,
				45,
				"https://github.com/testowner/testrepo/blob/main/src/main/java/GitHubClient.java#L10-L25",
				"commitSha123",
				"// src/main/java/GitHubClient.java:10-25\n" + rawContent,
				rawContent
		);

		HybridSearchHit hybridHit = new HybridSearchHit(
				hit,
				0.0325,
				0.8523,
				1,
				15.42,
				2,
				null,
				null
		);

		HybridSearchResult searchResult = new HybridSearchResult(
				List.of(hybridHit),
				SearchMode.HYBRID,
				false,
				List.of("A minor warning"),
				15L,
				10L,
				5L,
				0L,
				40L
		);

		when(hybridSearchService.search(
				eq(testRepo),
				eq("how to handle rate limits"),
				eq(5),
				eq(Set.of(ChunkType.METHOD, ChunkType.CLASS)),
				eq("src/main"),
				eq(SearchMode.HYBRID),
				eq(false),
				eq(false)
		)).thenReturn(searchResult);

		mockMvc.perform(get("/api/repos/{owner}/{name}/search", "testowner", "testrepo")
						.param("q", "how to handle rate limits")
						.param("k", "5")
						.param("type", "METHOD,CLASS")
						.param("path", "src/main"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.query").value("how to handle rate limits"))
				.andExpect(jsonPath("$.k").value(5))
				.andExpect(jsonPath("$.mode").value("HYBRID"))
				.andExpect(jsonPath("$.reranked").value(false))
				.andExpect(jsonPath("$.tookMillis").value(40))
				.andExpect(jsonPath("$.vectorMillis").value(15))
				.andExpect(jsonPath("$.keywordMillis").value(10))
				.andExpect(jsonPath("$.fusionMillis").value(5))
				.andExpect(jsonPath("$.warnings[0]").value("A minor warning"))
				.andExpect(jsonPath("$.hits").isArray())
				.andExpect(jsonPath("$.hits[0].score").value(0.033))
				.andExpect(jsonPath("$.hits[0].fusedScore").value(0.0325))
				.andExpect(jsonPath("$.hits[0].vectorScore").value(0.852))
				.andExpect(jsonPath("$.hits[0].vectorRank").value(1))
				.andExpect(jsonPath("$.hits[0].keywordScore").value(15.42))
				.andExpect(jsonPath("$.hits[0].keywordRank").value(2))
				.andExpect(jsonPath("$.hits[0].chunkType").value("METHOD"))
				.andExpect(jsonPath("$.hits[0].symbolName").value("handleRateLimits"))
				.andExpect(jsonPath("$.hits[0].parentSymbol").value("GitHubClient"))
				.andExpect(jsonPath("$.hits[0].headingPath").value("Client / Limits"))
				.andExpect(jsonPath("$.hits[0].path").value("src/main/java/GitHubClient.java"))
				.andExpect(jsonPath("$.hits[0].language").value("java"))
				.andExpect(jsonPath("$.hits[0].startLine").value(10))
				.andExpect(jsonPath("$.hits[0].endLine").value(25))
				.andExpect(jsonPath("$.hits[0].url").value("https://github.com/testowner/testrepo/blob/main/src/main/java/GitHubClient.java#L10-L25"))
				.andExpect(jsonPath("$.hits[0].snippet").value(containsString("line 1")))
				.andExpect(jsonPath("$.hits[0].snippet").value(containsString("line 20")));
	}

	@Test
	@DisplayName("GET /search accepts valid mode parameter case-insensitively")
	void search_modeParamValid_parsesCaseInsensitively() throws Exception {
		when(vectorStore.countRepoPoints(eq(testRepo))).thenReturn(10L);
		when(keywordIndex.exists(eq(testRepo))).thenReturn(true);

		when(hybridSearchService.search(eq(testRepo), eq("test query"), eq(10), any(), any(), eq(SearchMode.KEYWORD), anyBoolean(), anyBoolean()))
				.thenReturn(new HybridSearchResult(List.of(), SearchMode.KEYWORD, List.of(), 0L, 5L, 0L, 10L));

		mockMvc.perform(get("/api/repos/{owner}/{name}/search", "testowner", "testrepo")
						.param("q", "test query")
						.param("mode", "keyword"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.mode").value("KEYWORD"));
	}

	@Test
	@DisplayName("GET /search returns 400 when mode parameter is invalid, listing allowed values")
	void search_modeParamInvalid_returns400WithAllowedValues() throws Exception {
		mockMvc.perform(get("/api/repos/{owner}/{name}/search", "testowner", "testrepo")
						.param("q", "test query")
						.param("mode", "unsupported_mode"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value(containsString("Unknown search mode: 'unsupported_mode'")))
				.andExpect(jsonPath("$.detail").value(containsString("Allowed values are: [VECTOR, KEYWORD, HYBRID]")));
	}

	@Test
	@DisplayName("GET /search returns 400 when query is blank")
	void search_blankQuery_returns400() throws Exception {
		mockMvc.perform(get("/api/repos/{owner}/{name}/search", "testowner", "testrepo")
						.param("q", "   "))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value(containsString("must not be blank")));
	}

	@Test
	@DisplayName("GET /search returns 400 when query is missing")
	void search_missingQuery_returns400() throws Exception {
		mockMvc.perform(get("/api/repos/{owner}/{name}/search", "testowner", "testrepo"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value(containsString("must not be blank")));
	}

	@Test
	@DisplayName("GET /search returns 400 when query exceeds 500 characters")
	void search_tooLongQuery_returns400() throws Exception {
		String longQuery = "a".repeat(501);
		mockMvc.perform(get("/api/repos/{owner}/{name}/search", "testowner", "testrepo")
						.param("q", longQuery))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value(containsString("must not exceed 500 characters")));
	}

	@Test
	@DisplayName("GET /search returns 400 when k is less than 1")
	void search_kLessThanOne_returns400() throws Exception {
		mockMvc.perform(get("/api/repos/{owner}/{name}/search", "testowner", "testrepo")
						.param("q", "test")
						.param("k", "0"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value(containsString("must be between 1 and 50")));
	}

	@Test
	@DisplayName("GET /search returns 400 when k exceeds 50")
	void search_kGreaterThan50_returns400() throws Exception {
		mockMvc.perform(get("/api/repos/{owner}/{name}/search", "testowner", "testrepo")
						.param("q", "test")
						.param("k", "51"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value(containsString("must be between 1 and 50")));
	}

	@Test
	@DisplayName("GET /search returns 400 listing allowed values when chunk type is unknown")
	void search_unknownType_returns400ListingAllowedValues() throws Exception {
		mockMvc.perform(get("/api/repos/{owner}/{name}/search", "testowner", "testrepo")
						.param("q", "test")
						.param("type", "INVALID_TYPE"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value(containsString("Unknown chunk type: 'INVALID_TYPE'")))
				.andExpect(jsonPath("$.detail").value(containsString("Allowed values are:")));
	}

	@Test
	@DisplayName("GET /search returns 404 ProblemDetail when repository exists in neither index")
	void search_zeroPointsInNeitherIndex_returns404() throws Exception {
		when(vectorStore.countRepoPoints(eq(testRepo))).thenReturn(0L);
		when(keywordIndex.exists(eq(testRepo))).thenReturn(false);

		mockMvc.perform(get("/api/repos/{owner}/{name}/search", "testowner", "testrepo")
						.param("q", "valid query"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.title").value("Resource Not Found"))
				.andExpect(jsonPath("$.detail").value(containsString("testowner/testrepo")));
	}

	@Test
	@DisplayName("GET /search returns 503 ProblemDetail when VECTOR mode is requested and Qdrant is unavailable")
	void search_qdrantUnavailableInVectorMode_returns503WithHelpfulMessage() throws Exception {
		when(vectorStore.countRepoPoints(eq(testRepo)))
				.thenThrow(new QdrantUnavailableException("http://localhost:6333", new RuntimeException("connection refused")));

		mockMvc.perform(get("/api/repos/{owner}/{name}/search", "testowner", "testrepo")
						.param("q", "valid query")
						.param("mode", "VECTOR"))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.title").value("Service Unavailable"))
				.andExpect(jsonPath("$.detail").value(containsString("docker compose up -d")));
	}

	@Test
	@DisplayName("GET /search succeeds with KEYWORD mode when keyword index exists even if Qdrant is unavailable")
	void search_qdrantUnavailableInKeywordMode_succeedsIfKeywordIndexExists() throws Exception {
		when(vectorStore.countRepoPoints(eq(testRepo)))
				.thenThrow(new QdrantUnavailableException("http://localhost:6333", new RuntimeException("connection refused")));
		when(keywordIndex.exists(eq(testRepo))).thenReturn(true);

		when(hybridSearchService.search(eq(testRepo), eq("valid query"), eq(10), any(), any(), eq(SearchMode.KEYWORD), anyBoolean(), anyBoolean()))
				.thenReturn(new HybridSearchResult(List.of(), SearchMode.KEYWORD, false, List.of(), 0L, 12L, 0L, 0L, 15L));

		mockMvc.perform(get("/api/repos/{owner}/{name}/search", "testowner", "testrepo")
						.param("q", "valid query")
						.param("mode", "KEYWORD"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.mode").value("KEYWORD"));
	}

	@Test
	@DisplayName("GET /search is case-insensitive for repo owner and name")
	void search_caseInsensitiveRepo_queriesNormalizedRepo() throws Exception {
		RepoRef mixedCaseRepo = new RepoRef("TestOwner", "TestRepo");
		when(vectorStore.countRepoPoints(eq(mixedCaseRepo))).thenReturn(2L);
		when(keywordIndex.exists(eq(mixedCaseRepo))).thenReturn(true);
		when(hybridSearchService.search(eq(mixedCaseRepo), eq("test query"), eq(10), eq(Set.of()), any(), eq(SearchMode.HYBRID), anyBoolean(), anyBoolean()))
				.thenReturn(new HybridSearchResult(List.of(), SearchMode.HYBRID, false, List.of(), 5L, 5L, 2L, 0L, 18L));

		mockMvc.perform(get("/api/repos/{owner}/{name}/search", "TestOwner", "TestRepo")
						.param("q", "test query"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.query").value("test query"));
	}

	@Test
	@DisplayName("GET /search forwards rerank and excludeTests params and renders rerank fields in response")
	void search_withRerankAndExcludeTests_passesParamsAndIncludesRerankFieldsInResponse() throws Exception {
		when(vectorStore.countRepoPoints(eq(testRepo))).thenReturn(5L);
		when(keywordIndex.exists(eq(testRepo))).thenReturn(true);

		SearchHit hit = new SearchHit(
				"pt-1",
				0.9,
				testRepo.normalizedKey(),
				"run1",
				"c1",
				"doc1",
				0,
				SourceType.CODE,
				ChunkType.FUNCTION,
				"src/main/java/Service.java",
				"java",
				"doWork",
				null,
				null,
				1,
				10,
				20,
				"url",
				"sha",
				"// content",
				"content"
		);

		HybridSearchHit hybridHit = new HybridSearchHit(
				hit,
				0.025,
				0.9,
				1,
				10.0,
				1,
				0.9876,
				2
		);

		HybridSearchResult searchResult = new HybridSearchResult(
				List.of(hybridHit),
				SearchMode.HYBRID,
				true,
				List.of(),
				10L,
				8L,
				4L,
				15L,
				37L
		);

		when(hybridSearchService.search(
				eq(testRepo),
				eq("do work"),
				eq(10),
				any(),
				any(),
				eq(SearchMode.HYBRID),
				eq(true),
				eq(true)
		)).thenReturn(searchResult);

		mockMvc.perform(get("/api/repos/{owner}/{name}/search", "testowner", "testrepo")
						.param("q", "do work")
						.param("rerank", "true")
						.param("excludeTests", "true"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.reranked").value(true))
				.andExpect(jsonPath("$.rerankMillis").value(15))
				.andExpect(jsonPath("$.hits[0].rerankScore").value(0.9876))
				.andExpect(jsonPath("$.hits[0].fusedRank").value(2));
	}

	@Test
	@DisplayName("GET /search with rerank=false passes false to HybridSearchService")
	void search_withRerankFalse_passesFalseToService() throws Exception {
		when(vectorStore.countRepoPoints(eq(testRepo))).thenReturn(5L);
		when(keywordIndex.exists(eq(testRepo))).thenReturn(true);

		when(hybridSearchService.search(
				eq(testRepo),
				eq("query"),
				eq(10),
				any(),
				any(),
				eq(SearchMode.HYBRID),
				eq(false),
				eq(false)
		)).thenReturn(new HybridSearchResult(List.of(), SearchMode.HYBRID, false, List.of(), 5L, 5L, 2L, 0L, 12L));

		mockMvc.perform(get("/api/repos/{owner}/{name}/search", "testowner", "testrepo")
						.param("q", "query")
						.param("rerank", "false"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.reranked").value(false))
				.andExpect(jsonPath("$.rerankMillis").value(0));
	}

	@Test
	@DisplayName("GET /index-status returns points count and collection name")
	void getIndexStatus_returnsPointsCountAndCollection() throws Exception {
		when(vectorStore.countRepoPoints(eq(testRepo))).thenReturn(42L);
		GitContextProperties.QdrantProperties qdrantConfig = new GitContextProperties.QdrantProperties(
				"http://localhost:6333",
				"custom_collection",
				128,
				30
		);
		when(properties.qdrant()).thenReturn(qdrantConfig);

		mockMvc.perform(get("/api/repos/{owner}/{name}/index-status", "testowner", "testrepo"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.repo").value("testowner/testrepo"))
				.andExpect(jsonPath("$.collection").value("custom_collection"))
				.andExpect(jsonPath("$.pointsCount").value(42));
	}
}
