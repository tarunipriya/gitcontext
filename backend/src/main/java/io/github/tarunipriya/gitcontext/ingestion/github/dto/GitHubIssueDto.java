package io.github.tarunipriya.gitcontext.ingestion.github.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Data transfer object representing a GitHub issue.
 *
 * <p>Because GitHub's Issues API includes both issues and pull requests, the presence of the
 * {@code pull_request} field indicates that an item is actually a pull request and should be filtered out.
 *
 * @param number issue number
 * @param title issue title
 * @param state issue state ("open" or "closed")
 * @param body issue description markdown
 * @param user author user
 * @param createdAt creation timestamp
 * @param htmlUrl web link to the issue
 * @param labels assigned labels
 * @param pullRequest pull request payload map if this issue is actually a pull request, null otherwise
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubIssueDto(
		int number,
		String title,
		String state,
		String body,
		GitHubUserDto user,
		@JsonProperty("created_at") Instant createdAt,
		@JsonProperty("html_url") String htmlUrl,
		List<GitHubLabelDto> labels,
		@JsonProperty("pull_request") Map<String, Object> pullRequest
) {

	public GitHubIssueDto {
		labels = (labels == null) ? List.of() : List.copyOf(labels);
	}

	/**
	 * Returns {@code true} if this issue entry is actually a pull request.
	 *
	 * @return true if pullRequest field is present
	 */
	public boolean isPullRequest() {
		return pullRequest != null;
	}
}
