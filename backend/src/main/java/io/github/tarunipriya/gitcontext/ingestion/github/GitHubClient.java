package io.github.tarunipriya.gitcontext.ingestion.github;

import io.github.tarunipriya.gitcontext.config.GitContextProperties;
import io.github.tarunipriya.gitcontext.ingestion.github.dto.GitHubCommentDto;
import io.github.tarunipriya.gitcontext.ingestion.github.dto.GitHubIssueDto;
import io.github.tarunipriya.gitcontext.ingestion.github.dto.GitHubPullRequestDto;
import io.github.tarunipriya.gitcontext.ingestion.github.dto.GitHubReviewCommentDto;
import io.github.tarunipriya.gitcontext.model.RepoRef;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * HTTP client for the GitHub REST API using Spring {@link RestClient}.
 *
 * <p>Handles GitHub authentication, pagination via the {@code Link} header,
 * rate limit tracking via {@code X-RateLimit-*} headers, and error translation into
 * {@link GitHubApiException}.
 */
@Component
public class GitHubClient {

	private static final Logger log = LoggerFactory.getLogger(GitHubClient.class);
	private static final Pattern LINK_NEXT_PATTERN = Pattern.compile("<([^>]+)>;\\s*rel=[\"']?next[\"']?", Pattern.CASE_INSENSITIVE);

	private final RestClient restClient;
	private final GitContextProperties properties;

	/**
	 * Creates a new {@link GitHubClient} with the provided {@link RestClient.Builder} and properties.
	 *
	 * @param restClientBuilder Spring RestClient builder
	 * @param properties application properties
	 */
	public GitHubClient(RestClient.Builder restClientBuilder, GitContextProperties properties) {
		this.properties = Objects.requireNonNull(properties, "properties must not be null");
		Objects.requireNonNull(restClientBuilder, "restClientBuilder must not be null");

		RestClient.Builder builder = restClientBuilder
				.baseUrl(properties.github().apiBaseUrl())
				.defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
				.defaultHeader("X-GitHub-Api-Version", "2022-11-28")
				.defaultHeader(HttpHeaders.USER_AGENT, "gitcontext");

		if (properties.hasGithubToken()) {
			builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.githubToken());
		}

		this.restClient = builder.build();
	}

	/**
	 * Fetches pull requests for the repository up to {@code maxItems}.
	 *
	 * @param repo repository reference
	 * @param maxItems maximum number of pull requests to return
	 * @return list of pull request DTOs
	 */
	public List<GitHubPullRequestDto> getPullRequests(RepoRef repo, int maxItems) {
		Objects.requireNonNull(repo, "repo must not be null");
		String uri = "/repos/" + repo.fullName() + "/pulls?state=all&sort=created&direction=desc";
		return getPaged(uri, GitHubPullRequestDto.class, maxItems);
	}

	/**
	 * Fetches issues for the repository up to {@code maxItems}, filtering out pull request entries so
	 * that pagination continues until {@code maxItems} real issues are collected (or pages are exhausted).
	 *
	 * @param repo repository reference
	 * @param maxItems maximum number of real issues to return
	 * @return list of real issue DTOs
	 */
	public List<GitHubIssueDto> getIssues(RepoRef repo, int maxItems) {
		Objects.requireNonNull(repo, "repo must not be null");
		String uri = "/repos/" + repo.fullName() + "/issues?state=all";
		return getPaged(uri, GitHubIssueDto.class, maxItems, issue -> !issue.isPullRequest());
	}

	/**
	 * Fetches discussion comments for an issue or pull request up to {@code maxItems}.
	 *
	 * @param repo repository reference
	 * @param issueOrPrNumber issue or PR number
	 * @param maxItems maximum number of comments to return
	 * @return list of comment DTOs
	 */
	public List<GitHubCommentDto> getIssueComments(RepoRef repo, int issueOrPrNumber, int maxItems) {
		Objects.requireNonNull(repo, "repo must not be null");
		String uri = "/repos/" + repo.fullName() + "/issues/" + issueOrPrNumber + "/comments";
		return getPaged(uri, GitHubCommentDto.class, maxItems);
	}

	/**
	 * Fetches code review comments for a pull request up to {@code maxItems}.
	 *
	 * @param repo repository reference
	 * @param prNumber pull request number
	 * @param maxItems maximum number of review comments to return
	 * @return list of review comment DTOs
	 */
	public List<GitHubReviewCommentDto> getReviewComments(RepoRef repo, int prNumber, int maxItems) {
		Objects.requireNonNull(repo, "repo must not be null");
		String uri = "/repos/" + repo.fullName() + "/pulls/" + prNumber + "/comments";
		return getPaged(uri, GitHubReviewCommentDto.class, maxItems);
	}

	/**
	 * Performs a generic paginated GET request, appending {@code per_page=100}, following {@code Link}
	 * headers with {@code rel="next"}, and respecting rate limits.
	 *
	 * @param initialUri the starting relative or absolute URI
	 * @param elementType the DTO element class
	 * @param maxItems maximum items to collect
	 * @param <T> DTO element type
	 * @return unmodifiable list of collected items
	 */
	public <T> List<T> getPaged(String initialUri, Class<T> elementType, int maxItems) {
		return getPaged(initialUri, elementType, maxItems, null);
	}

	/**
	 * Performs a generic paginated GET request with item filtering, appending {@code per_page=100},
	 * following {@code Link} headers with {@code rel="next"}, and respecting rate limits.
	 *
	 * @param initialUri the starting relative or absolute URI
	 * @param elementType the DTO element class
	 * @param maxItems maximum items to collect
	 * @param filter optional predicate to filter items before counting towards {@code maxItems}
	 * @param <T> DTO element type
	 * @return unmodifiable list of collected items
	 */
	public <T> List<T> getPaged(String initialUri, Class<T> elementType, int maxItems, Predicate<T> filter) {
		Objects.requireNonNull(initialUri, "initialUri must not be null");
		Objects.requireNonNull(elementType, "elementType must not be null");

		List<T> collected = new ArrayList<>();
		if (maxItems <= 0) {
			return collected;
		}

		String currentUri = appendPerPage(initialUri, 100);
		@SuppressWarnings("unchecked")
		Class<T[]> arrayType = (Class<T[]>) elementType.arrayType();

		while (currentUri != null && collected.size() < maxItems) {
			try {
				ResponseEntity<T[]> response = restClient.get()
						.uri(currentUri)
						.retrieve()
						.onStatus(HttpStatusCode::is4xxClientError, this::handleClientError)
						.onStatus(HttpStatusCode::is5xxServerError, this::handleServerError)
						.toEntity(arrayType);

				HttpHeaders headers = response.getHeaders();
				boolean canContinue = checkRateLimit(headers);

				T[] body = response.getBody();
				if (body != null) {
					for (T item : body) {
						if (filter != null && !filter.test(item)) {
							continue;
						}
						collected.add(item);
						if (collected.size() >= maxItems) {
							break;
						}
					}
				}

				if (collected.size() >= maxItems || !canContinue) {
					break;
				}

				currentUri = extractNextLink(headers.getFirst(HttpHeaders.LINK));
			} catch (GitHubRateLimitException e) {
				log.warn("GitHub API rate limit exhausted: {}. Halting pagination.", e.getMessage());
				break;
			}
		}

		return Collections.unmodifiableList(collected);
	}

	static String extractNextLink(String linkHeader) {
		if (linkHeader == null || linkHeader.isBlank()) {
			return null;
		}
		Matcher matcher = LINK_NEXT_PATTERN.matcher(linkHeader);
		if (matcher.find()) {
			return matcher.group(1);
		}
		return null;
	}

	private static String appendPerPage(String uri, int perPage) {
		if (uri.contains("per_page=")) {
			return uri;
		}
		String separator = uri.contains("?") ? "&" : "?";
		return uri + separator + "per_page=" + perPage;
	}

	private boolean checkRateLimit(HttpHeaders headers) {
		String remainingStr = headers.getFirst("X-RateLimit-Remaining");
		if (remainingStr == null || remainingStr.isBlank()) {
			return true;
		}

		try {
			int remaining = Integer.parseInt(remainingStr.trim());
			if (remaining == 0) {
				String resetStr = headers.getFirst("X-RateLimit-Reset");
				Instant resetTime = (resetStr != null && !resetStr.isBlank())
						? Instant.ofEpochSecond(Long.parseLong(resetStr.trim()))
						: Instant.now();
				log.warn("GitHub API rate limit exhausted (0 remaining). Reset scheduled at {}. Stopping further requests.", resetTime);
				return false;
			}
			if (remaining < properties.github().rateLimitWarnThreshold()) {
				log.warn("GitHub API rate limit approaching threshold: {} remaining (warn threshold: {}).",
						remaining, properties.github().rateLimitWarnThreshold());
			}
		} catch (NumberFormatException e) {
			log.debug("Failed to parse X-RateLimit headers: {}", e.getMessage());
		}

		return true;
	}

	private void handleClientError(HttpRequest request, ClientHttpResponse response) throws IOException {
		HttpHeaders headers = response.getHeaders();
		String remainingStr = headers.getFirst("X-RateLimit-Remaining");
		if (remainingStr != null) {
			try {
				int remaining = Integer.parseInt(remainingStr.trim());
				if (remaining == 0) {
					String resetStr = headers.getFirst("X-RateLimit-Reset");
					Instant resetTime = (resetStr != null && !resetStr.isBlank())
							? Instant.ofEpochSecond(Long.parseLong(resetStr.trim()))
							: Instant.now();
					throw new GitHubRateLimitException("Rate limit reached 0, reset scheduled at " + resetTime);
				}
			} catch (NumberFormatException ignored) {
			}
		}

		int statusCode = response.getStatusCode().value();
		if (statusCode == 401) {
			throw new GitHubApiException(401, "GitHub API authentication failed (401 Unauthorized): invalid or expired token.");
		}
		if (statusCode == 403) {
			throw new GitHubApiException(403, "GitHub API access forbidden (403 Forbidden): insufficient permissions or private repository.");
		}
		if (statusCode == 404) {
			throw new GitHubApiException(404, "GitHub API resource not found (404 Not Found): repository or endpoint does not exist.");
		}

		throw new GitHubApiException(statusCode, "GitHub API client error (" + statusCode + "): " + response.getStatusText());
	}

	private void handleServerError(HttpRequest request, ClientHttpResponse response) throws IOException {
		int statusCode = response.getStatusCode().value();
		throw new GitHubApiException(statusCode, "GitHub API server error (" + statusCode + "): " + response.getStatusText());
	}

	static class GitHubRateLimitException extends RuntimeException {
		GitHubRateLimitException(String message) {
			super(message);
		}
	}
}
