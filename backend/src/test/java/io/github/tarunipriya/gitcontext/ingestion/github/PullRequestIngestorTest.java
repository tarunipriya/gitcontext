package io.github.tarunipriya.gitcontext.ingestion.github;

import io.github.tarunipriya.gitcontext.config.GitContextProperties;
import io.github.tarunipriya.gitcontext.model.Document;
import io.github.tarunipriya.gitcontext.model.RepoRef;
import io.github.tarunipriya.gitcontext.model.SourceType;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class PullRequestIngestorTest {

	private static final RepoRef REPO = new RepoRef("testowner", "testrepo");

	@Test
	@DisplayName("Ingests pull request with discussion, code review comments, and merged metadata")
	void testIngestPullRequestWithCommentsAndMergedState() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

		GitContextProperties properties = new GitContextProperties(
				null,
				Path.of("./data/repos"),
				"http://localhost:6333",
				null,
				null,
				null
		);
		GitHubClient client = new GitHubClient(builder, properties);
		PullRequestIngestor ingestor = new PullRequestIngestor(client, properties);

		String prsJson = """
				[
					{
						"number": 42,
						"title": "feat: add user authentication",
						"state": "closed",
						"body": "Implements JWT based authentication flow.",
						"html_url": "https://github.com/testowner/testrepo/pull/42",
						"created_at": "2026-10-01T12:00:00Z",
						"merged_at": "2026-10-02T15:30:00Z",
						"user": {"login": "alice", "html_url": "https://github.com/alice"},
						"base": {"ref": "main"},
						"head": {"ref": "feat/jwt-auth"}
					}
				]
				""";

		String commentsJson = """
				[
					{
						"id": 1001,
						"body": "Tested locally, looks solid!",
						"created_at": "2026-10-01T14:00:00Z",
						"user": {"login": "bob"}
					}
				]
				""";

		String reviewCommentsJson = """
				[
					{
						"id": 2001,
						"body": "Please add null check for token header.",
						"path": "src/main/java/AuthFilter.java",
						"line": 45,
						"created_at": "2026-10-01T16:00:00Z",
						"user": {"login": "charlie"}
					}
				]
				""";

		// 1. Fetch PRs
		server.expect(requestTo("https://api.github.com/repos/testowner/testrepo/pulls?state=all&sort=created&direction=desc&per_page=100"))
				.andExpect(method(HttpMethod.GET))
				.andRespond(withSuccess(prsJson, MediaType.APPLICATION_JSON));

		// 2. Fetch issue discussion comments for PR 42
		server.expect(requestTo("https://api.github.com/repos/testowner/testrepo/issues/42/comments?per_page=100"))
				.andExpect(method(HttpMethod.GET))
				.andRespond(withSuccess(commentsJson, MediaType.APPLICATION_JSON));

		// 3. Fetch code review comments for PR 42
		server.expect(requestTo("https://api.github.com/repos/testowner/testrepo/pulls/42/comments?per_page=100"))
				.andExpect(method(HttpMethod.GET))
				.andRespond(withSuccess(reviewCommentsJson, MediaType.APPLICATION_JSON));

		List<Document> documents = ingestor.ingest(REPO);

		server.verify();
		assertThat(documents).hasSize(1);

		Document doc = documents.get(0);
		assertThat(doc.metadata().sourceType()).isEqualTo(SourceType.PULL_REQUEST);
		assertThat(doc.metadata().repo()).isEqualTo("testowner/testrepo");
		assertThat(doc.metadata().prNumber()).isEqualTo(42);
		assertThat(doc.metadata().title()).isEqualTo("feat: add user authentication");
		assertThat(doc.metadata().author()).isEqualTo("alice");
		assertThat(doc.metadata().url()).isEqualTo("https://github.com/testowner/testrepo/pull/42");

		// Extra metadata assertions
		assertThat(doc.metadata().extra()).containsEntry("state", "merged");
		assertThat(doc.metadata().extra()).containsEntry("mergedAt", "2026-10-02T15:30:00Z");
		assertThat(doc.metadata().extra()).containsEntry("baseBranch", "main");
		assertThat(doc.metadata().extra()).containsEntry("headBranch", "feat/jwt-auth");
		assertThat(doc.metadata().extra()).containsEntry("commentCount", "2");

		// Content markdown assertions
		assertThat(doc.content()).contains("# feat: add user authentication");
		assertThat(doc.content()).contains("State: merged");
		assertThat(doc.content()).contains("Implements JWT based authentication flow.");
		assertThat(doc.content()).contains("## Discussion");
		assertThat(doc.content()).contains("- bob: Tested locally, looks solid!");
		assertThat(doc.content()).contains("## Review comments");
		assertThat(doc.content()).contains("- charlie (src/main/java/AuthFilter.java): Please add null check for token header.");
	}
}
