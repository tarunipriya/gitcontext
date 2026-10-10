package io.github.tarunipriya.gitcontext.ingestion.github;

import io.github.tarunipriya.gitcontext.config.GitContextProperties;
import io.github.tarunipriya.gitcontext.ingestion.github.dto.GitHubCommentDto;
import io.github.tarunipriya.gitcontext.ingestion.github.dto.GitHubIssueDto;
import io.github.tarunipriya.gitcontext.ingestion.github.dto.GitHubLabelDto;
import io.github.tarunipriya.gitcontext.model.Document;
import io.github.tarunipriya.gitcontext.model.DocumentMetadata;
import io.github.tarunipriya.gitcontext.model.RepoRef;
import io.github.tarunipriya.gitcontext.model.SourceType;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Ingests repository issues and discussion comments from the GitHub REST API, filtering out pull requests
 * and classifying incident-related issues.
 */
@Component
public class IssueIngestor {

	private static final Logger log = LoggerFactory.getLogger(IssueIngestor.class);

	private final GitHubClient gitHubClient;
	private final GitContextProperties properties;

	/**
	 * Creates a new {@link IssueIngestor}.
	 *
	 * @param gitHubClient GitHub REST API client
	 * @param properties application properties
	 */
	public IssueIngestor(GitHubClient gitHubClient, GitContextProperties properties) {
		this.gitHubClient = Objects.requireNonNull(gitHubClient, "gitHubClient must not be null");
		this.properties = Objects.requireNonNull(properties, "properties must not be null");
	}

	/**
	 * Ingests issues for the given repository up to {@code maxIssues}, skipping entries that are pull requests.
	 *
	 * @param repoRef repository descriptor
	 * @return list of issue {@link Document} records
	 */
	public List<Document> ingest(RepoRef repoRef) {
		Objects.requireNonNull(repoRef, "repoRef must not be null");

		int maxIssues = properties.github().maxIssues();
		int maxComments = properties.github().maxCommentsPerItem();

		Set<String> incidentLabels = new HashSet<>();
		for (String label : properties.github().incidentLabels()) {
			if (label != null && !label.isBlank()) {
				incidentLabels.add(label.trim().toLowerCase(Locale.ROOT));
			}
		}

		log.info("Fetching up to {} issues for repository {}", maxIssues, repoRef.fullName());
		List<GitHubIssueDto> issues = gitHubClient.getIssues(repoRef, maxIssues);

		List<Document> documents = new ArrayList<>();
		for (GitHubIssueDto issue : issues) {
			if (issue.isPullRequest()) {
				log.debug("Skipping issue #{} because it is a pull request", issue.number());
				continue;
			}

			String state = issue.state() != null ? issue.state().toLowerCase(Locale.ROOT) : "open";
			List<String> labelNames = issue.labels().stream()
					.map(GitHubLabelDto::name)
					.filter(Objects::nonNull)
					.toList();

			String labelsStr = String.join(", ", labelNames);

			boolean isIncident = false;
			for (String label : labelNames) {
				if (incidentLabels.contains(label.toLowerCase(Locale.ROOT))) {
					isIncident = true;
					break;
				}
			}

			List<GitHubCommentDto> comments = gitHubClient.getIssueComments(repoRef, issue.number(), maxComments);

			StringBuilder content = new StringBuilder();
			content.append("# ").append(issue.title()).append("\n\n");
			content.append("State: ").append(state).append("\n");
			if (!labelsStr.isBlank()) {
				content.append("Labels: ").append(labelsStr).append("\n");
			}
			content.append("\n");

			if (issue.body() != null && !issue.body().isBlank()) {
				content.append(issue.body().trim()).append("\n\n");
			}

			if (!comments.isEmpty()) {
				content.append("## Discussion\n");
				for (GitHubCommentDto comment : comments) {
					String author = comment.user() != null ? comment.user().login() : "unknown";
					String body = comment.body() != null ? comment.body().trim() : "";
					content.append("- ").append(author).append(": ").append(body).append("\n");
				}
				content.append("\n");
			}

			Map<String, String> extra = new LinkedHashMap<>();
			extra.put("state", state);
			extra.put("labels", labelsStr);
			extra.put("incident", isIncident ? "true" : "false");

			DocumentMetadata metadata = new DocumentMetadata(
					repoRef.fullName(),
					SourceType.ISSUE,
					null,
					issue.htmlUrl(),
					issue.user() != null ? issue.user().login() : null,
					issue.createdAt(),
					null,
					null,
					issue.number(),
					null,
					issue.title(),
					extra
			);

			Document doc = Document.create(content.toString().trim(), metadata, "issue-" + issue.number());
			documents.add(doc);
		}

		log.info("Ingested {} issue documents for repository {}", documents.size(), repoRef.fullName());
		return documents;
	}
}
