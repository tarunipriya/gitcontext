package io.github.tarunipriya.gitcontext.ingestion.github;

import io.github.tarunipriya.gitcontext.config.GitContextProperties;
import io.github.tarunipriya.gitcontext.ingestion.github.dto.GitHubCommentDto;
import io.github.tarunipriya.gitcontext.ingestion.github.dto.GitHubPullRequestDto;
import io.github.tarunipriya.gitcontext.ingestion.github.dto.GitHubReviewCommentDto;
import io.github.tarunipriya.gitcontext.model.Document;
import io.github.tarunipriya.gitcontext.model.DocumentMetadata;
import io.github.tarunipriya.gitcontext.model.RepoRef;
import io.github.tarunipriya.gitcontext.model.SourceType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Ingests pull requests, discussion comments, and code review comments from the GitHub REST API.
 */
@Component
public class PullRequestIngestor {

	private static final Logger log = LoggerFactory.getLogger(PullRequestIngestor.class);

	private final GitHubClient gitHubClient;
	private final GitContextProperties properties;

	/**
	 * Creates a new {@link PullRequestIngestor}.
	 *
	 * @param gitHubClient GitHub REST API client
	 * @param properties application properties
	 */
	public PullRequestIngestor(GitHubClient gitHubClient, GitContextProperties properties) {
		this.gitHubClient = Objects.requireNonNull(gitHubClient, "gitHubClient must not be null");
		this.properties = Objects.requireNonNull(properties, "properties must not be null");
	}

	/**
	 * Ingests pull requests for the given repository up to {@code maxPullRequests}.
	 *
	 * @param repoRef repository descriptor
	 * @return list of pull request {@link Document} records
	 */
	public List<Document> ingest(RepoRef repoRef) {
		Objects.requireNonNull(repoRef, "repoRef must not be null");

		int maxPrs = properties.github().maxPullRequests();
		int maxComments = properties.github().maxCommentsPerItem();

		log.info("Fetching up to {} pull requests for repository {}", maxPrs, repoRef.fullName());
		List<GitHubPullRequestDto> pullRequests = gitHubClient.getPullRequests(repoRef, maxPrs);

		List<Document> documents = new ArrayList<>(pullRequests.size());
		for (GitHubPullRequestDto pr : pullRequests) {
			String state = pr.isMerged()
					? "merged"
					: (pr.state() != null ? pr.state().toLowerCase(Locale.ROOT) : "open");

			List<GitHubCommentDto> discussionComments = gitHubClient.getIssueComments(repoRef, pr.number(), maxComments);
			List<GitHubReviewCommentDto> reviewComments = gitHubClient.getReviewComments(repoRef, pr.number(), maxComments);

			StringBuilder content = new StringBuilder();
			content.append("# ").append(pr.title()).append("\n\n");
			content.append("State: ").append(state).append("\n\n");

			if (pr.body() != null && !pr.body().isBlank()) {
				content.append(pr.body().trim()).append("\n\n");
			}

			if (!discussionComments.isEmpty()) {
				content.append("## Discussion\n");
				for (GitHubCommentDto comment : discussionComments) {
					String author = comment.user() != null ? comment.user().login() : "unknown";
					String body = comment.body() != null ? comment.body().trim() : "";
					content.append("- ").append(author).append(": ").append(body).append("\n");
				}
				content.append("\n");
			}

			if (!reviewComments.isEmpty()) {
				content.append("## Review comments\n");
				for (GitHubReviewCommentDto rc : reviewComments) {
					String author = rc.user() != null ? rc.user().login() : "unknown";
					String path = rc.path() != null ? rc.path() : "";
					String body = rc.body() != null ? rc.body().trim() : "";
					if (!path.isBlank()) {
						content.append("- ").append(author).append(" (").append(path).append("): ").append(body).append("\n");
					} else {
						content.append("- ").append(author).append(": ").append(body).append("\n");
					}
				}
				content.append("\n");
			}

			Map<String, String> extra = new LinkedHashMap<>();
			extra.put("state", state);
			extra.put("mergedAt", pr.mergedAt() != null ? pr.mergedAt().toString() : "");
			extra.put("baseBranch", pr.base() != null && pr.base().ref() != null ? pr.base().ref() : "");
			extra.put("headBranch", pr.head() != null && pr.head().ref() != null ? pr.head().ref() : "");
			extra.put("commentCount", String.valueOf(discussionComments.size() + reviewComments.size()));

			DocumentMetadata metadata = new DocumentMetadata(
					repoRef.fullName(),
					SourceType.PULL_REQUEST,
					null,
					pr.htmlUrl(),
					pr.user() != null ? pr.user().login() : null,
					pr.createdAt(),
					null,
					pr.number(),
					null,
					null,
					pr.title(),
					extra
			);

			Document doc = Document.create(content.toString().trim(), metadata, "pr-" + pr.number());
			documents.add(doc);
		}

		log.info("Ingested {} pull request documents for repository {}", documents.size(), repoRef.fullName());
		return documents;
	}
}
