package io.github.tarunipriya.gitcontext.api;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.tarunipriya.gitcontext.ingestion.IngestionJob;
import io.github.tarunipriya.gitcontext.ingestion.IngestionJobService;
import io.github.tarunipriya.gitcontext.ingestion.IngestionResult;
import io.github.tarunipriya.gitcontext.ingestion.JobStatus;
import io.github.tarunipriya.gitcontext.model.RepoRef;
import io.github.tarunipriya.gitcontext.model.SourceType;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(IngestionController.class)
class IngestionControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private IngestionJobService ingestionJobService;

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
	@DisplayName("GET /api/repos/{owner}/{name}/summary returns 200 OK with counts")
	void getRepoSummary_existing_returnsCounts() throws Exception {
		RepoRef repo = new RepoRef("testowner", "testrepo");
		String headSha = "abcdef1234567890abcdef1234567890abcdef12";
		Map<SourceType, Integer> counts = Map.of(
				SourceType.CODE, 10,
				SourceType.COMMIT, 5,
				SourceType.PULL_REQUEST, 2,
				SourceType.ISSUE, 1
		);
		IngestionResult result = new IngestionResult(
				repo,
				headSha,
				List.of(),
				counts,
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
}
