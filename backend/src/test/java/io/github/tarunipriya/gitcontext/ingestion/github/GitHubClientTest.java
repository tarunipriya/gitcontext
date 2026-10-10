package io.github.tarunipriya.gitcontext.ingestion.github;

import io.github.tarunipriya.gitcontext.config.GitContextProperties;
import io.github.tarunipriya.gitcontext.ingestion.github.dto.GitHubPullRequestDto;
import io.github.tarunipriya.gitcontext.model.RepoRef;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withUnauthorizedRequest;
import static org.springframework.http.HttpStatus.NOT_FOUND;

class GitHubClientTest {

	private static final RepoRef REPO = new RepoRef("testowner", "testrepo");

	@Test
	@DisplayName("Paginates across 2 pages using Link header and stops at max item count")
	void testPaginationAcrossPagesAndStopsAtMax() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

		GitContextProperties properties = createProperties(null);
		GitHubClient client = new GitHubClient(builder, properties);

		HttpHeaders page1Headers = new HttpHeaders();
		page1Headers.set(HttpHeaders.LINK, "<https://api.github.com/repos/testowner/testrepo/pulls?state=all&sort=created&direction=desc&page=2&per_page=100>; rel=\"next\"");
		page1Headers.set("X-RateLimit-Remaining", "4900");

		String page1Json = """
				[
					{"number": 101, "title": "PR 101", "state": "open"},
					{"number": 102, "title": "PR 102", "state": "closed"}
				]
				""";

		HttpHeaders page2Headers = new HttpHeaders();
		page2Headers.set("X-RateLimit-Remaining", "4899");

		String page2Json = """
				[
					{"number": 103, "title": "PR 103", "state": "open"},
					{"number": 104, "title": "PR 104", "state": "open"}
				]
				""";

		server.expect(requestTo("https://api.github.com/repos/testowner/testrepo/pulls?state=all&sort=created&direction=desc&per_page=100"))
				.andExpect(method(HttpMethod.GET))
				.andRespond(withSuccess(page1Json, MediaType.APPLICATION_JSON).headers(page1Headers));

		server.expect(requestTo("https://api.github.com/repos/testowner/testrepo/pulls?state=all&sort=created&direction=desc&page=2&per_page=100"))
				.andExpect(method(HttpMethod.GET))
				.andRespond(withSuccess(page2Json, MediaType.APPLICATION_JSON).headers(page2Headers));

		// Request max 3 items
		List<GitHubPullRequestDto> prs = client.getPullRequests(REPO, 3);

		server.verify();
		assertThat(prs).hasSize(3);
		assertThat(prs.get(0).number()).isEqualTo(101);
		assertThat(prs.get(1).number()).isEqualTo(102);
		assertThat(prs.get(2).number()).isEqualTo(103);
	}

	@Test
	@DisplayName("Authorization header is present when token is configured and absent when unconfigured")
	void testAuthorizationHeaderConfiguration() {
		// 1. With token configured
		RestClient.Builder builderWithToken = RestClient.builder();
		MockRestServiceServer serverWithToken = MockRestServiceServer.bindTo(builderWithToken).build();

		GitContextProperties propsWithToken = createProperties("ghp_testTokenSecret123");
		GitHubClient clientWithToken = new GitHubClient(builderWithToken, propsWithToken);

		serverWithToken.expect(requestTo("https://api.github.com/repos/testowner/testrepo/pulls?state=all&sort=created&direction=desc&per_page=100"))
				.andExpect(method(HttpMethod.GET))
				.andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer ghp_testTokenSecret123"))
				.andExpect(header(HttpHeaders.ACCEPT, "application/vnd.github+json"))
				.andExpect(header("X-GitHub-Api-Version", "2022-11-28"))
				.andExpect(header(HttpHeaders.USER_AGENT, "gitcontext"))
				.andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

		clientWithToken.getPullRequests(REPO, 10);
		serverWithToken.verify();

		// 2. Without token configured
		RestClient.Builder builderWithoutToken = RestClient.builder();
		MockRestServiceServer serverWithoutToken = MockRestServiceServer.bindTo(builderWithoutToken).build();

		GitContextProperties propsWithoutToken = createProperties(null);
		GitHubClient clientWithoutToken = new GitHubClient(builderWithoutToken, propsWithoutToken);

		serverWithoutToken.expect(requestTo("https://api.github.com/repos/testowner/testrepo/pulls?state=all&sort=created&direction=desc&per_page=100"))
				.andExpect(method(HttpMethod.GET))
				.andExpect(headerDoesNotExist(HttpHeaders.AUTHORIZATION))
				.andExpect(header(HttpHeaders.ACCEPT, "application/vnd.github+json"))
				.andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

		clientWithoutToken.getPullRequests(REPO, 10);
		serverWithoutToken.verify();
	}

	@Test
	@DisplayName("Rate limit stopping cleanly when X-RateLimit-Remaining reaches 0")
	void testRateLimitStopsCleanlyWhenZero() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

		GitContextProperties properties = createProperties(null);
		GitHubClient client = new GitHubClient(builder, properties);

		HttpHeaders headers = new HttpHeaders();
		headers.set(HttpHeaders.LINK, "<https://api.github.com/repos/testowner/testrepo/pulls?page=2&per_page=100>; rel=\"next\"");
		headers.set("X-RateLimit-Remaining", "0");
		headers.set("X-RateLimit-Reset", "1700000000");

		String json = """
				[
					{"number": 1, "title": "Last Allowed PR", "state": "open"}
				]
				""";

		server.expect(requestTo("https://api.github.com/repos/testowner/testrepo/pulls?state=all&sort=created&direction=desc&per_page=100"))
				.andExpect(method(HttpMethod.GET))
				.andRespond(withSuccess(json, MediaType.APPLICATION_JSON).headers(headers));

		// Page 2 should never be requested because rate limit hit 0
		List<GitHubPullRequestDto> prs = client.getPullRequests(REPO, 100);

		server.verify();
		assertThat(prs).hasSize(1);
		assertThat(prs.get(0).title()).isEqualTo("Last Allowed PR");
	}

	@Test
	@DisplayName("404 and 401 translate to GitHubApiException with helpful messages")
	void testErrorTranslationToGitHubApiException() {
		// 1. 404 Not Found
		RestClient.Builder builder404 = RestClient.builder();
		MockRestServiceServer server404 = MockRestServiceServer.bindTo(builder404).build();

		GitHubClient client404 = new GitHubClient(builder404, createProperties(null));
		server404.expect(requestTo("https://api.github.com/repos/testowner/testrepo/pulls?state=all&sort=created&direction=desc&per_page=100"))
				.andRespond(withStatus(NOT_FOUND));

		assertThatThrownBy(() -> client404.getPullRequests(REPO, 10))
				.isInstanceOf(GitHubApiException.class)
				.hasMessageContaining("404")
				.hasMessageContaining("not found");

		server404.verify();

		// 2. 401 Unauthorized
		RestClient.Builder builder401 = RestClient.builder();
		MockRestServiceServer server401 = MockRestServiceServer.bindTo(builder401).build();

		GitHubClient client401 = new GitHubClient(builder401, createProperties("ghp_badToken"));
		server401.expect(requestTo("https://api.github.com/repos/testowner/testrepo/pulls?state=all&sort=created&direction=desc&per_page=100"))
				.andRespond(withUnauthorizedRequest());

		assertThatThrownBy(() -> client401.getPullRequests(REPO, 10))
				.isInstanceOf(GitHubApiException.class)
				.hasMessageContaining("401")
				.hasMessageContaining("authentication failed");

		server401.verify();
	}

	private static GitContextProperties createProperties(String githubToken) {
		return new GitContextProperties(
				githubToken,
				Path.of("./data/repos"),
				"http://localhost:6333",
				null,
				null,
				null
		);
	}
}
