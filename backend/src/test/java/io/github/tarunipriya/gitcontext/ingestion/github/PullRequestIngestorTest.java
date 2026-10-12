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

	@Test
	@DisplayName("Ingests open unmerged pull request with state open and no mergedAt")
	void testOpenUnmergedPullRequest() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

		GitContextProperties properties = createProperties(50);
		GitHubClient client = new GitHubClient(builder, properties);
		PullRequestIngestor ingestor = new PullRequestIngestor(client, properties);

		String prsJson = """
				[
					{
						"number": 50,
						"title": "feat: open work in progress",
						"state": "open",
						"body": "Still working on this",
						"html_url": "https://github.com/testowner/testrepo/pull/50",
						"created_at": "2026-10-03T10:00:00Z",
						"merged_at": null,
						"user": {"login": "dev1"}
					}
				]
				""";

		server.expect(requestTo("https://api.github.com/repos/testowner/testrepo/pulls?state=all&sort=created&direction=desc&per_page=100"))
				.andExpect(method(HttpMethod.GET))
				.andRespond(withSuccess(prsJson, MediaType.APPLICATION_JSON));
		server.expect(requestTo("https://api.github.com/repos/testowner/testrepo/issues/50/comments?per_page=100"))
				.andExpect(method(HttpMethod.GET))
				.andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
		server.expect(requestTo("https://api.github.com/repos/testowner/testrepo/pulls/50/comments?per_page=100"))
				.andExpect(method(HttpMethod.GET))
				.andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

		List<Document> documents = ingestor.ingest(REPO);

		server.verify();
		assertThat(documents).hasSize(1);
		Document doc = documents.get(0);
		assertThat(doc.metadata().extra()).containsEntry("state", "open");
		assertThat(doc.metadata().extra().get("mergedAt")).isEmpty();
		assertThat(doc.content()).contains("State: open");
	}

	@Test
	@DisplayName("Ingests closed but unmerged pull request with state closed")
	void testClosedUnmergedPullRequest() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

		GitContextProperties properties = createProperties(50);
		GitHubClient client = new GitHubClient(builder, properties);
		PullRequestIngestor ingestor = new PullRequestIngestor(client, properties);

		String prsJson = """
				[
					{
						"number": 51,
						"title": "feat: rejected proposal",
						"state": "closed",
						"body": "Closed without merge",
						"html_url": "https://github.com/testowner/testrepo/pull/51",
						"created_at": "2026-10-03T10:00:00Z",
						"merged_at": null,
						"user": {"login": "dev2"}
					}
				]
				""";

		server.expect(requestTo("https://api.github.com/repos/testowner/testrepo/pulls?state=all&sort=created&direction=desc&per_page=100"))
				.andExpect(method(HttpMethod.GET))
				.andRespond(withSuccess(prsJson, MediaType.APPLICATION_JSON));
		server.expect(requestTo("https://api.github.com/repos/testowner/testrepo/issues/51/comments?per_page=100"))
				.andExpect(method(HttpMethod.GET))
				.andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
		server.expect(requestTo("https://api.github.com/repos/testowner/testrepo/pulls/51/comments?per_page=100"))
				.andExpect(method(HttpMethod.GET))
				.andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

		List<Document> documents = ingestor.ingest(REPO);

		server.verify();
		assertThat(documents).hasSize(1);
		Document doc = documents.get(0);
		assertThat(doc.metadata().extra()).containsEntry("state", "closed");
		assertThat(doc.metadata().extra().get("mergedAt")).isEmpty();
		assertThat(doc.content()).contains("State: closed");
	}

	@Test
	@DisplayName("Ingests pull request with null body cleanly without errors or null text")
	void testPullRequestWithNullBody() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

		GitContextProperties properties = createProperties(50);
		GitHubClient client = new GitHubClient(builder, properties);
		PullRequestIngestor ingestor = new PullRequestIngestor(client, properties);

		String prsJson = """
				[
					{
						"number": 52,
						"title": "fix: tiny typo fix",
						"state": "open",
						"body": null,
						"html_url": "https://github.com/testowner/testrepo/pull/52",
						"created_at": "2026-10-04T10:00:00Z",
						"merged_at": null,
						"user": {"login": "dev3"}
					}
				]
				""";

		server.expect(requestTo("https://api.github.com/repos/testowner/testrepo/pulls?state=all&sort=created&direction=desc&per_page=100"))
				.andExpect(method(HttpMethod.GET))
				.andRespond(withSuccess(prsJson, MediaType.APPLICATION_JSON));
		server.expect(requestTo("https://api.github.com/repos/testowner/testrepo/issues/52/comments?per_page=100"))
				.andExpect(method(HttpMethod.GET))
				.andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
		server.expect(requestTo("https://api.github.com/repos/testowner/testrepo/pulls/52/comments?per_page=100"))
				.andExpect(method(HttpMethod.GET))
				.andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

		List<Document> documents = ingestor.ingest(REPO);

		server.verify();
		assertThat(documents).hasSize(1);
		Document doc = documents.get(0);
		assertThat(doc.content()).isEqualTo("# fix: tiny typo fix\n\nState: open");
	}

	@Test
	@DisplayName("Limits pull request comments to maxCommentsPerItem")
	void testCommentsLimitedToMaxCommentsPerItem() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

		// Configure maxCommentsPerItem to 1
		GitContextProperties properties = createProperties(1);
		GitHubClient client = new GitHubClient(builder, properties);
		PullRequestIngestor ingestor = new PullRequestIngestor(client, properties);

		String prsJson = """
				[
					{
						"number": 53,
						"title": "PR with many comments",
						"state": "open",
						"body": "Check my PR",
						"html_url": "https://github.com/testowner/testrepo/pull/53",
						"created_at": "2026-10-04T10:00:00Z",
						"user": {"login": "dev4"}
					}
				]
				""";

		String commentsJson = """
				[
					{"id": 1, "body": "Comment 1", "user": {"login": "u1"}},
					{"id": 2, "body": "Comment 2", "user": {"login": "u2"}}
				]
				""";

		String reviewCommentsJson = """
				[
					{"id": 11, "body": "Review 1", "path": "Foo.java", "user": {"login": "u3"}},
					{"id": 12, "body": "Review 2", "path": "Foo.java", "user": {"login": "u4"}}
				]
				""";

		server.expect(requestTo("https://api.github.com/repos/testowner/testrepo/pulls?state=all&sort=created&direction=desc&per_page=100"))
				.andExpect(method(HttpMethod.GET))
				.andRespond(withSuccess(prsJson, MediaType.APPLICATION_JSON));
		server.expect(requestTo("https://api.github.com/repos/testowner/testrepo/issues/53/comments?per_page=100"))
				.andExpect(method(HttpMethod.GET))
				.andRespond(withSuccess(commentsJson, MediaType.APPLICATION_JSON));
		server.expect(requestTo("https://api.github.com/repos/testowner/testrepo/pulls/53/comments?per_page=100"))
				.andExpect(method(HttpMethod.GET))
				.andRespond(withSuccess(reviewCommentsJson, MediaType.APPLICATION_JSON));

		List<Document> documents = ingestor.ingest(REPO);

		server.verify();
		assertThat(documents).hasSize(1);
		Document doc = documents.get(0);
		// With maxCommentsPerItem=1, getPaged caps each list at 1
		assertThat(doc.content()).contains("- u1: Comment 1");
		assertThat(doc.content()).doesNotContain("Comment 2");
		assertThat(doc.content()).contains("- u3 (Foo.java): Review 1");
		assertThat(doc.content()).doesNotContain("Review 2");
	}

	private static GitContextProperties createProperties(int maxComments) {
		return new GitContextProperties(
				null,
				Path.of("./data/repos"),
				"http://localhost:6333",
				null,
				null,
				new GitContextProperties.GitHubProperties("https://api.github.com", 200, 200, maxComments, List.of("incident", "bug"), 50)
		);
	}
}
