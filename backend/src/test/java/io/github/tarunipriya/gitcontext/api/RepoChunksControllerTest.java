package io.github.tarunipriya.gitcontext.api;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.tarunipriya.gitcontext.ingestion.IngestionJobService;
import io.github.tarunipriya.gitcontext.ingestion.IngestionResult;
import io.github.tarunipriya.gitcontext.model.Chunk;
import io.github.tarunipriya.gitcontext.model.ChunkMetadata;
import io.github.tarunipriya.gitcontext.model.ChunkType;
import io.github.tarunipriya.gitcontext.model.Document;
import io.github.tarunipriya.gitcontext.model.DocumentMetadata;
import io.github.tarunipriya.gitcontext.model.RepoRef;
import io.github.tarunipriya.gitcontext.model.SourceType;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RepoChunksController.class)
class RepoChunksControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private IngestionJobService ingestionJobService;

	private RepoRef repoRef;
	private IngestionResult sampleResult;
	private Chunk chunk1;
	private Chunk chunk2;
	private Chunk chunk3;
	private Chunk chunk4;
	private Chunk chunk5;

	@BeforeEach
	void setUp() {
		repoRef = new RepoRef("testowner", "testrepo");

		DocumentMetadata docMeta1 = new DocumentMetadata(
				repoRef.fullName(), SourceType.CODE, "src/App.java",
				"https://github.com/testowner/testrepo/blob/sha1/src/App.java",
				"alice", Instant.now(), "sha1", null, null, "java", "App.java", Map.of()
		);
		Document doc1 = Document.create("class App {}", docMeta1, "src/App.java");

		DocumentMetadata docMeta2 = new DocumentMetadata(
				repoRef.fullName(), SourceType.CODE, "src/Helper.java",
				"https://github.com/testowner/testrepo/blob/sha1/src/Helper.java",
				"alice", Instant.now(), "sha1", null, null, "java", "Helper.java", Map.of()
		);
		Document doc2 = Document.create("class Helper {}", docMeta2, "src/Helper.java");

		DocumentMetadata docMeta3 = new DocumentMetadata(
				repoRef.fullName(), SourceType.DOC, "docs/guide.md",
				"https://github.com/testowner/testrepo/blob/sha1/docs/guide.md",
				"bob", Instant.now(), "sha1", null, null, "markdown", "guide.md", Map.of()
		);
		Document doc3 = Document.create("# Guide", docMeta3, "docs/guide.md");

		DocumentMetadata docMeta4 = new DocumentMetadata(
				repoRef.fullName(), SourceType.COMMIT, null,
				"https://github.com/testowner/testrepo/commit/sha1",
				"alice", Instant.now(), "sha1", null, null, null, "initial commit", Map.of()
		);
		Document doc4 = Document.create("initial commit message", docMeta4, "sha1");

		chunk1 = new Chunk("chunk-1", doc1.id(), 0, "header\nclass App", "class App {}",
				ChunkMetadata.from(docMeta1, ChunkType.CLASS, 100, "App", null, null, 1, 50));
		chunk2 = new Chunk("chunk-2", doc1.id(), 1, "header\nvoid run()", "void run() {}",
				ChunkMetadata.from(docMeta1, ChunkType.METHOD, 40, "run", "App", null, 10, 30));
		chunk3 = new Chunk("chunk-3", doc2.id(), 0, "header\nvoid help()", "void help() {}",
				ChunkMetadata.from(docMeta2, ChunkType.METHOD, 25, "help", "Helper", null, 5, 15));
		chunk4 = new Chunk("chunk-4", doc3.id(), 0, "header\n# Overview", "# Overview",
				ChunkMetadata.from(docMeta3, ChunkType.SECTION, 60, null, null, "Guide > Overview", 1, 20));
		chunk5 = new Chunk("chunk-5", doc4.id(), 0, "initial commit", "initial commit",
				ChunkMetadata.from(docMeta4, ChunkType.COMMIT, 15, null, null, null, null, null));

		List<Document> documents = List.of(doc1, doc2, doc3, doc4);
		List<Chunk> chunks = List.of(chunk1, chunk2, chunk3, chunk4, chunk5);

		Map<SourceType, Integer> countsBySource = Map.of(
				SourceType.CODE, 2,
				SourceType.DOC, 1,
				SourceType.COMMIT, 1
		);
		Map<ChunkType, Integer> chunkCounts = Map.of(
				ChunkType.CLASS, 1,
				ChunkType.METHOD, 2,
				ChunkType.SECTION, 1,
				ChunkType.COMMIT, 1
		);

		sampleResult = new IngestionResult(
				repoRef,
				"sha1",
				documents,
				countsBySource,
				chunks,
				chunkCounts,
				48.0,
				100,
				Map.of(),
				100L,
				List.of()
		);
	}

	@Test
	@DisplayName("GET /api/repos/{owner}/{name}/files returns distinct files sorted by path with chunk counts")
	void getFiles_returnsSortedDistinctFiles() throws Exception {
		when(ingestionJobService.getLatestResult(repoRef)).thenReturn(Optional.of(sampleResult));

		mockMvc.perform(get("/api/repos/{owner}/{name}/files", "testowner", "testrepo"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(3)))
				.andExpect(jsonPath("$[0].path").value("docs/guide.md"))
				.andExpect(jsonPath("$[0].sourceType").value("DOC"))
				.andExpect(jsonPath("$[0].language").value("markdown"))
				.andExpect(jsonPath("$[0].chunkCount").value(1))
				.andExpect(jsonPath("$[1].path").value("src/App.java"))
				.andExpect(jsonPath("$[1].sourceType").value("CODE"))
				.andExpect(jsonPath("$[1].chunkCount").value(2))
				.andExpect(jsonPath("$[2].path").value("src/Helper.java"))
				.andExpect(jsonPath("$[2].chunkCount").value(1));
	}

	@Test
	@DisplayName("GET /api/repos/{owner}/{name}/chunks returns paginated list with total count")
	void getChunks_defaultPagination_returnsAll() throws Exception {
		when(ingestionJobService.getLatestResult(repoRef)).thenReturn(Optional.of(sampleResult));

		mockMvc.perform(get("/api/repos/{owner}/{name}/chunks", "testowner", "testrepo"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.total").value(5))
				.andExpect(jsonPath("$.limit").value(50))
				.andExpect(jsonPath("$.offset").value(0))
				.andExpect(jsonPath("$.items", hasSize(5)))
				.andExpect(jsonPath("$.items[0].id").value("chunk-1"))
				.andExpect(jsonPath("$.items[0].chunkType").value("CLASS"))
				.andExpect(jsonPath("$.items[0].symbolName").value("App"))
				.andExpect(jsonPath("$.items[0].startLine").value(1))
				.andExpect(jsonPath("$.items[0].endLine").value(50))
				.andExpect(jsonPath("$.items[0].url").value("https://github.com/testowner/testrepo/blob/sha1/src/App.java#L1-L50"))
				.andExpect(jsonPath("$.items[0].text").value("header\nclass App"));
	}

	@Test
	@DisplayName("GET /api/repos/{owner}/{name}/chunks filters by exact path")
	void getChunks_filtersByPath() throws Exception {
		when(ingestionJobService.getLatestResult(repoRef)).thenReturn(Optional.of(sampleResult));

		mockMvc.perform(get("/api/repos/{owner}/{name}/chunks", "testowner", "testrepo")
						.param("path", "src/App.java"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.total").value(2))
				.andExpect(jsonPath("$.items", hasSize(2)))
				.andExpect(jsonPath("$.items[0].path").value("src/App.java"))
				.andExpect(jsonPath("$.items[1].path").value("src/App.java"));
	}

	@Test
	@DisplayName("GET /api/repos/{owner}/{name}/chunks filters by ChunkType")
	void getChunks_filtersByType() throws Exception {
		when(ingestionJobService.getLatestResult(repoRef)).thenReturn(Optional.of(sampleResult));

		mockMvc.perform(get("/api/repos/{owner}/{name}/chunks", "testowner", "testrepo")
						.param("type", "METHOD"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.total").value(2))
				.andExpect(jsonPath("$.items", hasSize(2)))
				.andExpect(jsonPath("$.items[0].symbolName").value("run"))
				.andExpect(jsonPath("$.items[1].symbolName").value("help"));
	}

	@Test
	@DisplayName("GET /api/repos/{owner}/{name}/chunks filters by case-insensitive symbol substring")
	void getChunks_filtersBySymbol() throws Exception {
		when(ingestionJobService.getLatestResult(repoRef)).thenReturn(Optional.of(sampleResult));

		mockMvc.perform(get("/api/repos/{owner}/{name}/chunks", "testowner", "testrepo")
						.param("symbol", "RUN"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.total").value(1))
				.andExpect(jsonPath("$.items[0].symbolName").value("run"));
	}

	@Test
	@DisplayName("GET /api/repos/{owner}/{name}/chunks paginates using limit and offset")
	void getChunks_paginationSlice() throws Exception {
		when(ingestionJobService.getLatestResult(repoRef)).thenReturn(Optional.of(sampleResult));

		mockMvc.perform(get("/api/repos/{owner}/{name}/chunks", "testowner", "testrepo")
						.param("limit", "2")
						.param("offset", "1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.total").value(5))
				.andExpect(jsonPath("$.limit").value(2))
				.andExpect(jsonPath("$.offset").value(1))
				.andExpect(jsonPath("$.items", hasSize(2)))
				.andExpect(jsonPath("$.items[0].id").value("chunk-2"))
				.andExpect(jsonPath("$.items[1].id").value("chunk-3"));
	}

	@Test
	@DisplayName("GET /api/repos/{owner}/{name}/chunks returns 400 for invalid limit or offset")
	void getChunks_invalidPagination_returns400() throws Exception {
		mockMvc.perform(get("/api/repos/{owner}/{name}/chunks", "testowner", "testrepo")
						.param("limit", "0"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.title").value("Invalid Request"))
				.andExpect(jsonPath("$.detail", containsString("limit must be between 1 and 200")));

		mockMvc.perform(get("/api/repos/{owner}/{name}/chunks", "testowner", "testrepo")
						.param("limit", "250"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail", containsString("limit must be between 1 and 200")));

		mockMvc.perform(get("/api/repos/{owner}/{name}/chunks", "testowner", "testrepo")
						.param("offset", "-1"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail", containsString("offset must be non-negative")));
	}

	@Test
	@DisplayName("GET /api/repos/{owner}/{name}/chunks returns 400 with allowed values for invalid ChunkType")
	void getChunks_invalidType_returns400WithAllowedValues() throws Exception {
		mockMvc.perform(get("/api/repos/{owner}/{name}/chunks", "testowner", "testrepo")
						.param("type", "NON_EXISTENT_TYPE"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.title").value("Invalid Request"))
				.andExpect(jsonPath("$.detail", containsString("Invalid chunk type 'NON_EXISTENT_TYPE'")))
				.andExpect(jsonPath("$.detail", containsString("CLASS, METHOD, FUNCTION, MODULE, SECTION, LINES, COMMIT, PULL_REQUEST, ISSUE")));
	}

	@Test
	@DisplayName("GET /api/repos/{owner}/{name}/chunks/{chunkId} returns 200 with text and content")
	void getChunk_knownId_returnsFullDetail() throws Exception {
		when(ingestionJobService.getLatestResult(repoRef)).thenReturn(Optional.of(sampleResult));

		mockMvc.perform(get("/api/repos/{owner}/{name}/chunks/{chunkId}", "testowner", "testrepo", "chunk-1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value("chunk-1"))
				.andExpect(jsonPath("$.chunkType").value("CLASS"))
				.andExpect(jsonPath("$.symbolName").value("App"))
				.andExpect(jsonPath("$.text").value("header\nclass App"))
				.andExpect(jsonPath("$.content").value("class App {}"));
	}

	@Test
	@DisplayName("GET /api/repos/{owner}/{name}/chunks/{chunkId} returns 404 for unknown chunkId")
	void getChunk_unknownId_returns404() throws Exception {
		when(ingestionJobService.getLatestResult(repoRef)).thenReturn(Optional.of(sampleResult));

		mockMvc.perform(get("/api/repos/{owner}/{name}/chunks/{chunkId}", "testowner", "testrepo", "unknown-chunk"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.title").value("Resource Not Found"))
				.andExpect(jsonPath("$.detail", containsString("Chunk not found: unknown-chunk")));
	}

	@Test
	@DisplayName("All repo endpoints return 404 ProblemDetail if repo has no completed ingestion")
	void repoEndpoints_unknownRepo_return404() throws Exception {
		when(ingestionJobService.getLatestResult(any())).thenReturn(Optional.empty());

		mockMvc.perform(get("/api/repos/{owner}/{name}/files", "unknown", "repo"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.title").value("Resource Not Found"))
				.andExpect(jsonPath("$.detail", containsString("unknown/repo")));

		mockMvc.perform(get("/api/repos/{owner}/{name}/chunks", "unknown", "repo"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.title").value("Resource Not Found"));

		mockMvc.perform(get("/api/repos/{owner}/{name}/chunks/{chunkId}", "unknown", "repo", "some-id"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.title").value("Resource Not Found"));
	}

	@Test
	@DisplayName("Case-insensitive repo lookup on chunk endpoints")
	void caseInsensitiveRepoLookup() throws Exception {
		when(ingestionJobService.getLatestResult(new RepoRef("TESTOWNER", "TESTREPO")))
				.thenReturn(Optional.of(sampleResult));

		mockMvc.perform(get("/api/repos/{owner}/{name}/files", "TESTOWNER", "TESTREPO"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(3)));
	}
}
