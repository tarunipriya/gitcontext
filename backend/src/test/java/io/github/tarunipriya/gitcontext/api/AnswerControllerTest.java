package io.github.tarunipriya.gitcontext.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.tarunipriya.gitcontext.api.dto.AskRequest;
import io.github.tarunipriya.gitcontext.generation.AnswerResult;
import io.github.tarunipriya.gitcontext.generation.AnswerService;
import io.github.tarunipriya.gitcontext.generation.Citation;
import io.github.tarunipriya.gitcontext.generation.LlmRateLimitedException;
import io.github.tarunipriya.gitcontext.generation.LlmUnavailableException;
import io.github.tarunipriya.gitcontext.generation.Source;
import io.github.tarunipriya.gitcontext.indexing.SearchHit;
import io.github.tarunipriya.gitcontext.indexing.VectorStore;
import io.github.tarunipriya.gitcontext.model.ChunkType;
import io.github.tarunipriya.gitcontext.model.RepoRef;
import io.github.tarunipriya.gitcontext.model.SourceType;
import io.github.tarunipriya.gitcontext.retrieval.keyword.KeywordIndex;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AnswerController.class)
class AnswerControllerTest {

	@Autowired
	private MockMvc mockMvc;

	private final ObjectMapper objectMapper = new ObjectMapper();

	@MockitoBean
	private AnswerService answerService;

	@MockitoBean
	private VectorStore vectorStore;

	@MockitoBean
	private KeywordIndex keywordIndex;

	private final RepoRef testRepo = new RepoRef("testowner", "testrepo");

	@Test
	@DisplayName("POST /api/repos/{owner}/{name}/ask happy path returns 200 with citations, sources, timings")
	void ask_happyPath_returns200WithFullShape() throws Exception {
		when(vectorStore.countRepoPoints(eq(testRepo))).thenReturn(10L);
		when(keywordIndex.exists(eq(testRepo))).thenReturn(true);

		SearchHit hit = new SearchHit(
				"point-uuid",
				0.85,
				testRepo.normalizedKey(),
				"run-1",
				"chunk-sha",
				"doc-1",
				0,
				SourceType.CODE,
				ChunkType.METHOD,
				"src/GitHubClient.java",
				"java",
				"handleRateLimits",
				"GitHubClient",
				null,
				10,
				25,
				30,
				"https://github.com/testowner/testrepo/blob/main/src/GitHubClient.java#L10-L25",
				"commit123",
				"// code",
				"code"
		);

		Source source = new Source(1, hit, true);
		Citation citation = new Citation(
				1,
				"src/GitHubClient.java",
				10,
				25,
				"handleRateLimits",
				"https://github.com/testowner/testrepo/blob/main/src/GitHubClient.java#L10-L25",
				"chunk-sha"
		);

		AnswerResult result = new AnswerResult(
				"GitContext pauses when rate limits occur [1].",
				List.of(citation),
				List.of(source),
				"gemini-3.8-flash",
				150,
				35,
				45L,
				520L,
				565L,
				List.of()
		);

		when(answerService.ask(eq(testRepo), eq("How does rate limit work?"), any())).thenReturn(result);

		AskRequest request = new AskRequest("How does rate limit work?", "HYBRID", true, false, 5);

		mockMvc.perform(post("/api/repos/{owner}/{name}/ask", "testowner", "testrepo")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.question").value("How does rate limit work?"))
				.andExpect(jsonPath("$.answer").value("GitContext pauses when rate limits occur [1]."))
				.andExpect(jsonPath("$.model").value("gemini-3.8-flash"))
				.andExpect(jsonPath("$.promptTokens").value(150))
				.andExpect(jsonPath("$.outputTokens").value(35))
				.andExpect(jsonPath("$.citations", hasSize(1)))
				.andExpect(jsonPath("$.citations[0].number").value(1))
				.andExpect(jsonPath("$.citations[0].path").value("src/GitHubClient.java"))
				.andExpect(jsonPath("$.citations[0].startLine").value(10))
				.andExpect(jsonPath("$.citations[0].endLine").value(25))
				.andExpect(jsonPath("$.citations[0].symbolName").value("handleRateLimits"))
				.andExpect(jsonPath("$.citations[0].chunkId").value("chunk-sha"))
				.andExpect(jsonPath("$.sources", hasSize(1)))
				.andExpect(jsonPath("$.sources[0].number").value(1))
				.andExpect(jsonPath("$.sources[0].path").value("src/GitHubClient.java"))
				.andExpect(jsonPath("$.sources[0].cited").value(true))
				.andExpect(jsonPath("$.retrievalMillis").value(45))
				.andExpect(jsonPath("$.generationMillis").value(520))
				.andExpect(jsonPath("$.totalMillis").value(565))
				.andExpect(jsonPath("$.warnings").isArray());
	}

	@Test
	@DisplayName("POST /api/repos/{owner}/{name}/ask with mixed case repo path matches case-insensitively")
	void ask_mixedCaseRepo_resolvesCorrectly() throws Exception {
		when(vectorStore.countRepoPoints(eq(testRepo))).thenReturn(10L);
		when(keywordIndex.exists(eq(testRepo))).thenReturn(true);

		AnswerResult result = new AnswerResult(
				"Simple answer.",
				List.of(),
				List.of(),
				"gemini-3.8-flash",
				50,
				10,
				20L,
				300L,
				320L,
				List.of()
		);
		when(answerService.ask(eq(testRepo), eq("What is this?"), any())).thenReturn(result);

		AskRequest request = new AskRequest("What is this?", null, null, null, null);

		mockMvc.perform(post("/api/repos/{owner}/{name}/ask", "TestOwner", "TestRepo")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.answer").value("Simple answer."));
	}

	@Test
	@DisplayName("POST /api/repos/{owner}/{name}/ask returns 400 when question is blank")
	void ask_blankQuestion_returns400() throws Exception {
		AskRequest request = new AskRequest("   ", "HYBRID", true, false, 8);

		mockMvc.perform(post("/api/repos/{owner}/{name}/ask", "testowner", "testrepo")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value(containsString("question must not be blank")));
	}

	@Test
	@DisplayName("POST /api/repos/{owner}/{name}/ask returns 400 when question exceeds 1000 chars")
	void ask_excessiveLengthQuestion_returns400() throws Exception {
		String longQuestion = "a".repeat(1001);
		AskRequest request = new AskRequest(longQuestion, "HYBRID", true, false, 8);

		mockMvc.perform(post("/api/repos/{owner}/{name}/ask", "testowner", "testrepo")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value(containsString("1000 characters")));
	}

	@Test
	@DisplayName("POST /api/repos/{owner}/{name}/ask returns 400 when maxSources is out of range")
	void ask_invalidMaxSources_returns400() throws Exception {
		AskRequest request = new AskRequest("Valid question", "HYBRID", true, false, 15);

		mockMvc.perform(post("/api/repos/{owner}/{name}/ask", "testowner", "testrepo")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value(containsString("maxSources must be between 1 and 12")));
	}

	@Test
	@DisplayName("POST /api/repos/{owner}/{name}/ask returns 400 when search mode is unknown")
	void ask_unknownSearchMode_returns400() throws Exception {
		when(vectorStore.countRepoPoints(eq(testRepo))).thenReturn(5L);
		when(keywordIndex.exists(eq(testRepo))).thenReturn(true);

		AskRequest request = new AskRequest("Valid question", "UNKNOWN_MODE", true, false, 5);

		mockMvc.perform(post("/api/repos/{owner}/{name}/ask", "testowner", "testrepo")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value(containsString("Unknown search mode: 'UNKNOWN_MODE'")));
	}

	@Test
	@DisplayName("POST /api/repos/{owner}/{name}/ask returns 404 when repo is missing from both indexes")
	void ask_repoMissingFromBothIndexes_returns404() throws Exception {
		when(vectorStore.countRepoPoints(eq(testRepo))).thenReturn(0L);
		when(keywordIndex.exists(eq(testRepo))).thenReturn(false);

		AskRequest request = new AskRequest("Valid question", "HYBRID", true, false, 5);

		mockMvc.perform(post("/api/repos/{owner}/{name}/ask", "testowner", "testrepo")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.title").value("Resource Not Found"))
				.andExpect(jsonPath("$.detail").value(containsString("testowner/testrepo")));
	}

	@Test
	@DisplayName("POST /api/repos/{owner}/{name}/ask returns 503 when LLM is unavailable")
	void ask_llmUnavailable_returns503WithHelpfulMessage() throws Exception {
		when(vectorStore.countRepoPoints(eq(testRepo))).thenReturn(10L);
		when(keywordIndex.exists(eq(testRepo))).thenReturn(true);

		when(answerService.ask(eq(testRepo), eq("Valid question"), any()))
				.thenThrow(new LlmUnavailableException("Gemini API key not set. Add LLM_API_KEY to the .env file at the repository root."));

		AskRequest request = new AskRequest("Valid question", "HYBRID", true, false, 5);

		mockMvc.perform(post("/api/repos/{owner}/{name}/ask", "testowner", "testrepo")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.title").value("Service Unavailable"))
				.andExpect(jsonPath("$.detail").value(containsString("Gemini API key not set")));
	}

	@Test
	@DisplayName("POST /api/repos/{owner}/{name}/ask returns 429 with Retry-After header when rate limited")
	void ask_llmRateLimited_returns429WithRetryAfterHeader() throws Exception {
		when(vectorStore.countRepoPoints(eq(testRepo))).thenReturn(10L);
		when(keywordIndex.exists(eq(testRepo))).thenReturn(true);

		when(answerService.ask(eq(testRepo), eq("Valid question"), any()))
				.thenThrow(new LlmRateLimitedException("Rate limit exceeded for Gemini API. Please try again later.", 45L));

		AskRequest request = new AskRequest("Valid question", "HYBRID", true, false, 5);

		mockMvc.perform(post("/api/repos/{owner}/{name}/ask", "testowner", "testrepo")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isTooManyRequests())
				.andExpect(header().string(HttpHeaders.RETRY_AFTER, "45"))
				.andExpect(jsonPath("$.title").value("Rate Limit Exceeded"))
				.andExpect(jsonPath("$.detail").value(containsString("Rate limit exceeded for Gemini API")));
	}

	@Test
	@DisplayName("POST /api/repos/{owner}/{name}/ask includes title for non-file chunks (COMMIT, PR, ISSUE)")
	void ask_nonFileSources_includesTitleInResponse() throws Exception {
		when(vectorStore.countRepoPoints(eq(testRepo))).thenReturn(10L);
		when(keywordIndex.exists(eq(testRepo))).thenReturn(true);

		SearchHit commitHit = new SearchHit(
				"id-1", 0.95, "testowner/testrepo", "run-1", "chunk-1", "doc-1", 0,
				SourceType.COMMIT, ChunkType.COMMIT, null, null, null, null, null,
				null, null, 20, "https://github.com/testowner/testrepo/commit/abc1234",
				"abc1234567890", "Commit abc1234: Add search endpoint\n\nFull details",
				"Add search endpoint\n\nFull commit message body"
		);
		SearchHit prHit = new SearchHit(
				"id-2", 0.90, "testowner/testrepo", "run-1", "chunk-2", "doc-2", 0,
				SourceType.PULL_REQUEST, ChunkType.PULL_REQUEST, null, null, null, null, null,
				null, null, 25, "https://github.com/testowner/testrepo/pull/16",
				null, "PR #16: Implement hybrid search\n\nDetails",
				"# Implement hybrid search\n\nState: merged"
		);

		Source source1 = new Source(1, commitHit, true);
		Source source2 = new Source(2, prHit, true);
		Citation citation1 = new Citation(1, null, null, null, null, commitHit.url(), "chunk-1", "Add search endpoint");
		Citation citation2 = new Citation(2, null, null, null, null, prHit.url(), "chunk-2", "Implement hybrid search");

		AnswerResult result = new AnswerResult(
				"Answer referring to commit [1] and PR [2].",
				List.of(citation1, citation2),
				List.of(source1, source2),
				"gemini-3.8-flash",
				100, 20, 10L, 200L, 210L, List.of()
		);

		when(answerService.ask(eq(testRepo), eq("What commits or PRs changed search?"), any())).thenReturn(result);

		AskRequest request = new AskRequest("What commits or PRs changed search?", "HYBRID", true, false, 5);

		mockMvc.perform(post("/api/repos/{owner}/{name}/ask", "testowner", "testrepo")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.sources[0].title").value("Add search endpoint"))
				.andExpect(jsonPath("$.sources[0].chunkType").value("COMMIT"))
				.andExpect(jsonPath("$.sources[1].title").value("Implement hybrid search"))
				.andExpect(jsonPath("$.sources[1].chunkType").value("PULL_REQUEST"))
				.andExpect(jsonPath("$.citations[0].title").value("Add search endpoint"))
				.andExpect(jsonPath("$.citations[1].title").value("Implement hybrid search"));
	}
}
