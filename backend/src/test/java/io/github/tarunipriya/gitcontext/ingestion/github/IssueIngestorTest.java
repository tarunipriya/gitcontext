package io.github.tarunipriya.gitcontext.ingestion.github;

import io.github.tarunipriya.gitcontext.config.GitContextProperties;
import io.github.tarunipriya.gitcontext.model.Document;
import io.github.tarunipriya.gitcontext.model.RepoRef;
import io.github.tarunipriya.gitcontext.model.SourceType;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class IssueIngestorTest {

	private static final RepoRef REPO = new RepoRef("testowner", "testrepo");

	@Test
	@DisplayName("Skips pull request entries and sets incident flag correctly from labels")
	void testSkipsPullRequestsAndClassifiesIncidentLabels() {
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
		IssueIngestor ingestor = new IssueIngestor(client, properties);

		String issuesJson = """
				[
					{
						"number": 10,
						"title": "PR disguised as issue",
						"state": "open",
						"body": "This is a pull request entry from GitHub issues API",
						"pull_request": {"url": "https://api.github.com/repos/testowner/testrepo/pulls/10"}
					},
					{
						"number": 11,
						"title": "Database connection pool exhausted during peak traffic",
						"state": "closed",
						"body": "Outage occurred at 14:00 UTC due to connection exhaustion.",
						"html_url": "https://github.com/testowner/testrepo/issues/11",
						"created_at": "2026-10-05T14:00:00Z",
						"user": {"login": "oncall_engineer"},
						"labels": [
							{"name": "BUG", "description": "Bug report"},
							{"name": "backend", "description": "Backend service"}
						]
					},
					{
						"number": 12,
						"title": "Add dark mode toggle to UI",
						"state": "open",
						"body": "User requested dark mode support.",
						"html_url": "https://github.com/testowner/testrepo/issues/12",
						"created_at": "2026-10-06T09:00:00Z",
						"user": {"login": "frontend_dev"},
						"labels": [
							{"name": "enhancement", "description": "Feature request"}
						]
					}
				]
				""";

		String comments11Json = """
				[
					{
						"id": 501,
						"body": "Resolved after increasing pool size from 10 to 50.",
						"created_at": "2026-10-05T14:45:00Z",
						"user": {"login": "dba_lead"}
					}
				]
				""";

		// 1. Fetch issues
		server.expect(requestTo("https://api.github.com/repos/testowner/testrepo/issues?state=all&per_page=100"))
				.andExpect(method(HttpMethod.GET))
				.andRespond(withSuccess(issuesJson, MediaType.APPLICATION_JSON));

		// 2. Fetch comments for issue 11
		server.expect(requestTo("https://api.github.com/repos/testowner/testrepo/issues/11/comments?per_page=100"))
				.andExpect(method(HttpMethod.GET))
				.andRespond(withSuccess(comments11Json, MediaType.APPLICATION_JSON));

		// 3. Fetch comments for issue 12
		server.expect(requestTo("https://api.github.com/repos/testowner/testrepo/issues/12/comments?per_page=100"))
				.andExpect(method(HttpMethod.GET))
				.andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

		List<Document> documents = ingestor.ingest(REPO);

		server.verify();

		// Issue #10 was a pull request and must have been skipped
		assertThat(documents).hasSize(2);

		// Issue #11 (Incident issue due to "BUG" label matching default incidentLabels)
		Document incidentDoc = documents.get(0);
		assertThat(incidentDoc.metadata().sourceType()).isEqualTo(SourceType.ISSUE);
		assertThat(incidentDoc.metadata().issueNumber()).isEqualTo(11);
		assertThat(incidentDoc.metadata().title()).isEqualTo("Database connection pool exhausted during peak traffic");
		assertThat(incidentDoc.metadata().author()).isEqualTo("oncall_engineer");
		assertThat(incidentDoc.metadata().extra()).containsEntry("incident", "true");
		assertThat(incidentDoc.metadata().extra()).containsEntry("labels", "BUG, backend");
		assertThat(incidentDoc.metadata().extra()).containsEntry("state", "closed");
		assertThat(incidentDoc.content()).contains("# Database connection pool exhausted during peak traffic");
		assertThat(incidentDoc.content()).contains("State: closed");
		assertThat(incidentDoc.content()).contains("Labels: BUG, backend");
		assertThat(incidentDoc.content()).contains("## Discussion");
		assertThat(incidentDoc.content()).contains("- dba_lead: Resolved after increasing pool size from 10 to 50.");

		// Issue #12 (Normal non-incident issue)
		Document normalDoc = documents.get(1);
		assertThat(normalDoc.metadata().issueNumber()).isEqualTo(12);
		assertThat(normalDoc.metadata().title()).isEqualTo("Add dark mode toggle to UI");
		assertThat(normalDoc.metadata().extra()).containsEntry("incident", "false");
		assertThat(normalDoc.metadata().extra()).containsEntry("labels", "enhancement");
		assertThat(normalDoc.metadata().extra()).containsEntry("state", "open");
	}

	@Test
	@DisplayName("Pagination continues past pull requests across pages until maxIssues real issues are collected")
	void testPaginationContinuesUntilMaxRealIssuesCollected() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

		// Configure maxIssues = 2
		GitContextProperties properties = createProperties(2, 50);
		GitHubClient client = new GitHubClient(builder, properties);
		IssueIngestor ingestor = new IssueIngestor(client, properties);

		// Page 1: 2 PRs, 1 real issue (issue #103)
		HttpHeaders page1Headers = new HttpHeaders();
		page1Headers.set(HttpHeaders.LINK, "<https://api.github.com/repos/testowner/testrepo/issues?state=all&page=2&per_page=100>; rel=\"next\"");

		String page1Json = """
				[
					{
						"number": 101,
						"title": "PR 101",
						"state": "open",
						"pull_request": {"url": "https://api.github.com/repos/testowner/testrepo/pulls/101"}
					},
					{
						"number": 102,
						"title": "PR 102",
						"state": "open",
						"pull_request": {"url": "https://api.github.com/repos/testowner/testrepo/pulls/102"}
					},
					{
						"number": 103,
						"title": "Real Issue 103",
						"state": "open",
						"body": "First real issue",
						"html_url": "https://github.com/testowner/testrepo/issues/103",
						"created_at": "2026-10-01T10:00:00Z",
						"labels": []
					}
				]
				""";

		// Page 2: 1 PR, 1 real issue (issue #105)
		String page2Json = """
				[
					{
						"number": 104,
						"title": "PR 104",
						"state": "open",
						"pull_request": {"url": "https://api.github.com/repos/testowner/testrepo/pulls/104"}
					},
					{
						"number": 105,
						"title": "Real Issue 105",
						"state": "open",
						"body": "Second real issue",
						"html_url": "https://github.com/testowner/testrepo/issues/105",
						"created_at": "2026-10-02T10:00:00Z",
						"labels": []
					}
				]
				""";

		server.expect(requestTo("https://api.github.com/repos/testowner/testrepo/issues?state=all&per_page=100"))
				.andExpect(method(HttpMethod.GET))
				.andRespond(withSuccess(page1Json, MediaType.APPLICATION_JSON).headers(page1Headers));

		server.expect(requestTo("https://api.github.com/repos/testowner/testrepo/issues?state=all&page=2&per_page=100"))
				.andExpect(method(HttpMethod.GET))
				.andRespond(withSuccess(page2Json, MediaType.APPLICATION_JSON));

		server.expect(requestTo("https://api.github.com/repos/testowner/testrepo/issues/103/comments?per_page=100"))
				.andExpect(method(HttpMethod.GET))
				.andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

		server.expect(requestTo("https://api.github.com/repos/testowner/testrepo/issues/105/comments?per_page=100"))
				.andExpect(method(HttpMethod.GET))
				.andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

		List<Document> documents = ingestor.ingest(REPO);

		server.verify();
		assertThat(documents).hasSize(2);
		assertThat(documents.get(0).metadata().issueNumber()).isEqualTo(103);
		assertThat(documents.get(1).metadata().issueNumber()).isEqualTo(105);
	}

	@Test
	@DisplayName("Ingests issue with no incident labels and marks incident as false")
	void testIssueWithNoIncidentLabel() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

		GitContextProperties properties = createProperties(10, 50);
		GitHubClient client = new GitHubClient(builder, properties);
		IssueIngestor ingestor = new IssueIngestor(client, properties);

		String issuesJson = """
				[
					{
						"number": 201,
						"title": "Update README docs",
						"state": "open",
						"body": "Clarify setup instructions",
						"html_url": "https://github.com/testowner/testrepo/issues/201",
						"created_at": "2026-10-01T10:00:00Z",
						"labels": [
							{"name": "documentation"}
						]
					}
				]
				""";

		server.expect(requestTo("https://api.github.com/repos/testowner/testrepo/issues?state=all&per_page=100"))
				.andExpect(method(HttpMethod.GET))
				.andRespond(withSuccess(issuesJson, MediaType.APPLICATION_JSON));

		server.expect(requestTo("https://api.github.com/repos/testowner/testrepo/issues/201/comments?per_page=100"))
				.andExpect(method(HttpMethod.GET))
				.andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

		List<Document> documents = ingestor.ingest(REPO);

		server.verify();
		assertThat(documents).hasSize(1);
		Document doc = documents.get(0);
		assertThat(doc.metadata().extra()).containsEntry("incident", "false");
		assertThat(doc.metadata().extra()).containsEntry("labels", "documentation");
	}

	@Test
	@DisplayName("Ingests issue with null body cleanly without NPE or null text")
	void testIssueWithNullBody() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

		GitContextProperties properties = createProperties(10, 50);
		GitHubClient client = new GitHubClient(builder, properties);
		IssueIngestor ingestor = new IssueIngestor(client, properties);

		String issuesJson = """
				[
					{
						"number": 202,
						"title": "Empty body issue",
						"state": "open",
						"body": null,
						"html_url": "https://github.com/testowner/testrepo/issues/202",
						"created_at": "2026-10-01T10:00:00Z",
						"labels": []
					}
				]
				""";

		server.expect(requestTo("https://api.github.com/repos/testowner/testrepo/issues?state=all&per_page=100"))
				.andExpect(method(HttpMethod.GET))
				.andRespond(withSuccess(issuesJson, MediaType.APPLICATION_JSON));

		server.expect(requestTo("https://api.github.com/repos/testowner/testrepo/issues/202/comments?per_page=100"))
				.andExpect(method(HttpMethod.GET))
				.andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

		List<Document> documents = ingestor.ingest(REPO);

		server.verify();
		assertThat(documents).hasSize(1);
		Document doc = documents.get(0);
		assertThat(doc.content()).isEqualTo("# Empty body issue\n\nState: open");
	}

	@Test
	@DisplayName("Limits issue comments to maxCommentsPerItem")
	void testCommentsLimitedToMaxCommentsPerItem() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

		GitContextProperties properties = createProperties(10, 1);
		GitHubClient client = new GitHubClient(builder, properties);
		IssueIngestor ingestor = new IssueIngestor(client, properties);

		String issuesJson = """
				[
					{
						"number": 203,
						"title": "Issue with discussion",
						"state": "open",
						"body": "Discussion topic",
						"html_url": "https://github.com/testowner/testrepo/issues/203",
						"created_at": "2026-10-01T10:00:00Z",
						"labels": []
					}
				]
				""";

		String commentsJson = """
				[
					{"id": 1, "body": "First reply", "user": {"login": "user1"}},
					{"id": 2, "body": "Second reply", "user": {"login": "user2"}}
				]
				""";

		server.expect(requestTo("https://api.github.com/repos/testowner/testrepo/issues?state=all&per_page=100"))
				.andExpect(method(HttpMethod.GET))
				.andRespond(withSuccess(issuesJson, MediaType.APPLICATION_JSON));

		server.expect(requestTo("https://api.github.com/repos/testowner/testrepo/issues/203/comments?per_page=100"))
				.andExpect(method(HttpMethod.GET))
				.andRespond(withSuccess(commentsJson, MediaType.APPLICATION_JSON));

		List<Document> documents = ingestor.ingest(REPO);

		server.verify();
		assertThat(documents).hasSize(1);
		Document doc = documents.get(0);
		assertThat(doc.content()).contains("- user1: First reply");
		assertThat(doc.content()).doesNotContain("Second reply");
	}

	private static GitContextProperties createProperties(int maxIssues, int maxComments) {
		return new GitContextProperties(
				null,
				Path.of("./data/repos"),
				"http://localhost:6333",
				null,
				null,
				new GitContextProperties.GitHubProperties("https://api.github.com", 200, maxIssues, maxComments, List.of("incident", "bug"), 50)
		);
	}
}
