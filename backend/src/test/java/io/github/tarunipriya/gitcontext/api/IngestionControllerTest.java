package io.github.tarunipriya.gitcontext.api;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.tarunipriya.gitcontext.config.GitContextProperties;
import io.github.tarunipriya.gitcontext.ingestion.IngestionJob;
import io.github.tarunipriya.gitcontext.ingestion.IngestionJobService;
import io.github.tarunipriya.gitcontext.ingestion.IngestionResult;
import io.github.tarunipriya.gitcontext.ingestion.JobStatus;
import io.github.tarunipriya.gitcontext.model.RepoRef;
import io.github.tarunipriya.gitcontext.model.SourceType;
import io.github.tarunipriya.gitcontext.retrieval.keyword.CodeAnalyzer;
import io.github.tarunipriya.gitcontext.retrieval.keyword.KeywordIndex;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.FSDirectory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@WebMvcTest(IngestionController.class)
class IngestionControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private IngestionJobService ingestionJobService;

	@MockitoBean
	private KeywordIndex keywordIndex;

	@Test
	@DisplayName("POST /api/ingestions returns 202 Accepted, Location header, and job body")
	void submitIngestion_validRepo_returns202AndLocation() throws Exception {
		UUID jobId = UUID.randomUUID();
		RepoRef repo = new RepoRef("testowner", "testrepo");
		IngestionJob job = IngestionJob.queued(jobId, repo);

		when(ingestionJobService.submit(repo)).thenReturn(job);

		mockMvc.perform(post("/api/ingestions")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"repo\":\"testowner/testrepo\"}"))
				.andExpect(status().isAccepted())
				.andExpect(header().string("Location", "/api/ingestions/" + jobId))
				.andExpect(jsonPath("$.jobId").value(jobId.toString()))
				.andExpect(jsonPath("$.repo").value("testowner/testrepo"))
				.andExpect(jsonPath("$.status").value("QUEUED"));
	}

	@Test
	@DisplayName("POST /api/ingestions returns 400 Bad Request when repo is blank")
	void submitIngestion_blankRepo_returns400() throws Exception {
		mockMvc.perform(post("/api/ingestions")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"repo\":\"   \"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.title").value("Validation Failed"))
				.andExpect(jsonPath("$.detail", containsString("repo must not be blank")));
	}

	@Test
	@DisplayName("POST /api/ingestions returns 400 Bad Request when repo format is invalid")
	void submitIngestion_invalidRepo_returns400() throws Exception {
		mockMvc.perform(post("/api/ingestions")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"repo\":\"invalid_repo_without_slash\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.title").value("Invalid Request"))
				.andExpect(jsonPath("$.detail", containsString("Invalid repository reference")));
	}

	@Test
	@DisplayName("GET /api/ingestions/{jobId} returns 200 OK for known job")
	void getJob_knownJob_returns200() throws Exception {
		UUID jobId = UUID.randomUUID();
		RepoRef repo = new RepoRef("testowner", "testrepo");
		IngestionJob job = IngestionJob.queued(jobId, repo);

		when(ingestionJobService.getJob(jobId)).thenReturn(Optional.of(job));

		mockMvc.perform(get("/api/ingestions/{jobId}", jobId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.jobId").value(jobId.toString()))
				.andExpect(jsonPath("$.repo").value("testowner/testrepo"))
				.andExpect(jsonPath("$.status").value("QUEUED"));
	}

	@Test
	@DisplayName("GET /api/ingestions/{jobId} returns 404 Not Found for unknown job")
	void getJob_unknownJob_returns404() throws Exception {
		UUID jobId = UUID.randomUUID();
		when(ingestionJobService.getJob(jobId)).thenReturn(Optional.empty());

		mockMvc.perform(get("/api/ingestions/{jobId}", jobId))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.title").value("Resource Not Found"))
				.andExpect(jsonPath("$.detail", containsString(jobId.toString())));
	}

	@Test
	@DisplayName("GET /api/ingestions returns recent jobs list")
	void getRecentJobs_returnsList() throws Exception {
		UUID jobId = UUID.randomUUID();
		RepoRef repo = new RepoRef("testowner", "testrepo");
		IngestionJob job = IngestionJob.queued(jobId, repo);

		when(ingestionJobService.getRecentJobs()).thenReturn(List.of(job));

		mockMvc.perform(get("/api/ingestions"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].jobId").value(jobId.toString()))
				.andExpect(jsonPath("$[0].repo").value("testowner/testrepo"));
	}

	@Test
	@DisplayName("POST /api/ingestions returns 503 Service Unavailable with Retry-After header when queue is full")
	void submitIngestion_queueFull_returns503WithRetryAfter() throws Exception {
		when(ingestionJobService.submit(any()))
				.thenThrow(new io.github.tarunipriya.gitcontext.ingestion.IngestionQueueFullException("Ingestion task queue is full, please retry later"));

		mockMvc.perform(post("/api/ingestions")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"repo\":\"testowner/testrepo\"}"))
				.andExpect(status().isServiceUnavailable())
				.andExpect(header().string("Retry-After", "60"))
				.andExpect(jsonPath("$.title").value("Service Unavailable"))
				.andExpect(jsonPath("$.detail", containsString("Ingestion task queue is full")));
	}

	@Test
	@DisplayName("GET /api/repos/{owner}/{name}/summary returns 200 OK with counts and chunking metrics")
	void getRepoSummary_existing_returnsCounts() throws Exception {
		RepoRef repo = new RepoRef("testowner", "testrepo");
		String headSha = "abcdef1234567890abcdef1234567890abcdef12";
		Map<SourceType, Integer> counts = Map.of(
				SourceType.CODE, 10,
				SourceType.COMMIT, 5,
				SourceType.PULL_REQUEST, 2,
				SourceType.ISSUE, 1
		);
		Map<io.github.tarunipriya.gitcontext.model.ChunkType, Integer> chunkCounts = Map.of(
				io.github.tarunipriya.gitcontext.model.ChunkType.CLASS, 3,
				io.github.tarunipriya.gitcontext.model.ChunkType.METHOD, 5
		);
		IngestionResult result = new IngestionResult(
				repo,
				headSha,
				List.of(),
				counts,
				List.of(),
				chunkCounts,
				42.5,
				120,
				8,
				15L,
				25L,
				Map.of(),
				250L,
				List.of("Warning: rate limit near threshold")
		);

		when(ingestionJobService.getLatestResult(repo)).thenReturn(Optional.of(result));

		mockMvc.perform(get("/api/repos/{owner}/{name}/summary", "testowner", "testrepo"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.repo").value("testowner/testrepo"))
				.andExpect(jsonPath("$.headCommitSha").value(headSha))
				.andExpect(jsonPath("$.countsBySourceType.CODE").value(10))
				.andExpect(jsonPath("$.countsBySourceType.COMMIT").value(5))
				.andExpect(jsonPath("$.totalChunks").value(0))
				.andExpect(jsonPath("$.chunkCountsByType.CLASS").value(3))
				.andExpect(jsonPath("$.chunkCountsByType.METHOD").value(5))
				.andExpect(jsonPath("$.avgChunkTokens").value(42.5))
				.andExpect(jsonPath("$.maxChunkTokens").value(120))
				.andExpect(jsonPath("$.indexedPoints").value(8))
				.andExpect(jsonPath("$.embedMillis").value(15))
				.andExpect(jsonPath("$.upsertMillis").value(25))
				.andExpect(jsonPath("$.warnings[0]", containsString("rate limit near threshold")));
	}

	@Test
	@DisplayName("GET /api/repos/{owner}/{name}/summary returns 404 when no completed ingestion exists")
	void getRepoSummary_unknown_returns404() throws Exception {
		RepoRef repo = new RepoRef("unknown", "repo");
		when(ingestionJobService.getLatestResult(repo)).thenReturn(Optional.empty());

		mockMvc.perform(get("/api/repos/{owner}/{name}/summary", "unknown", "repo"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.title").value("Resource Not Found"))
				.andExpect(jsonPath("$.detail", containsString("unknown/repo")));
	}

	@Test
	@DisplayName("GET /api/repos returns list of repository summaries with in-memory result details")
	void listRepos_withInMemoryResults_returnsSummaries() throws Exception {
		RepoRef repo1 = new RepoRef("owner1", "repo1");
		RepoRef repo2 = new RepoRef("owner2", "repo2");
		when(keywordIndex.listIndexedRepos()).thenReturn(List.of(repo1, repo2));

		IngestionResult result1 = new IngestionResult(
				repo1,
				"abc1234",
				List.of(),
				Map.of(SourceType.CODE, 10),
				List.of(),
				Map.of(),
				30.0,
				50,
				25,
				100L,
				50L,
				Map.of(),
				250L,
				List.of()
		);
		when(ingestionJobService.getLatestResult(repo1)).thenReturn(Optional.of(result1));
		when(ingestionJobService.getLatestResult(repo2)).thenReturn(Optional.empty());
		when(keywordIndex.getDocumentCount(repo2)).thenReturn(42);
		when(keywordIndex.getLastModified(repo2)).thenReturn(Instant.parse("2026-10-27T09:00:00Z"));

		mockMvc.perform(get("/api/repos"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(2)))
				.andExpect(jsonPath("$[0].repo").value("owner1/repo1"))
				.andExpect(jsonPath("$[0].headCommitSha").value("abc1234"))
				.andExpect(jsonPath("$[0].totalChunks").value(0))
				.andExpect(jsonPath("$[0].indexedPoints").value(25))
				.andExpect(jsonPath("$[1].repo").value("owner2/repo2"))
				.andExpect(jsonPath("$[1].totalChunks").value(42));
	}

	@Test
	@DisplayName("GET /api/repos returns empty list when no repos are indexed")
	void listRepos_empty_returnsEmptyList() throws Exception {
		when(keywordIndex.listIndexedRepos()).thenReturn(List.of());

		mockMvc.perform(get("/api/repos"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(0)));
	}

	@Test
	@DisplayName("GET /api/repos with temp index dir discovers repositories on disk via MockMvc")
	void listRepos_withTempIndexDir_discoversReposOnDisk(@TempDir Path tempDir) throws Exception {
		Path luceneDir = tempDir.resolve("lucene");
		Path repo1Dir = luceneDir.resolve("testowner").resolve("myrepo");
		Files.createDirectories(repo1Dir);

		// Build a real minimal Lucene index in repo1Dir
		CodeAnalyzer analyzer = new CodeAnalyzer();
		try (FSDirectory dir = FSDirectory.open(repo1Dir)) {
			IndexWriterConfig config = new IndexWriterConfig(analyzer);
			try (IndexWriter writer = new IndexWriter(dir, config)) {
				Document doc = new Document();
				doc.add(new StringField("chunkId", "c1", Field.Store.YES));
				writer.addDocument(doc);
				writer.commit();
			}
		}

		GitContextProperties.KeywordProperties keywordProps = new GitContextProperties.KeywordProperties(
				luceneDir, 3.0, 1.5, 1.0
		);
		GitContextProperties props = new GitContextProperties(
				null, tempDir.resolve("repos"), "http://localhost:6333",
				null, null, null, null, null, null, keywordProps, null
		);

		KeywordIndex realKeywordIndex = new KeywordIndex(props, analyzer);
		IngestionController controller = new IngestionController(ingestionJobService, realKeywordIndex);
		MockMvc standaloneMockMvc = MockMvcBuilders.standaloneSetup(controller).build();

		standaloneMockMvc.perform(get("/api/repos"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].repo").value("testowner/myrepo"))
				.andExpect(jsonPath("$[0].totalChunks").value(1));

		realKeywordIndex.close();
	}
}
