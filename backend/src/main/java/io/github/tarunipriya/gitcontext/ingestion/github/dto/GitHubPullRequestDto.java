package io.github.tarunipriya.gitcontext.ingestion.github.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

/**
 * Data transfer object representing a GitHub pull request.
 *
 * @param number pull request number
 * @param title pull request title
 * @param state state ("open" or "closed")
 * @param body pull request markdown description
 * @param user author user
 * @param createdAt creation timestamp
 * @param mergedAt merge timestamp, or null if unmerged
 * @param htmlUrl web link to the pull request
 * @param base base branch reference
 * @param head head branch reference
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubPullRequestDto(
		int number,
		String title,
		String state,
		String body,
		GitHubUserDto user,
		@JsonProperty("created_at") Instant createdAt,
		@JsonProperty("merged_at") Instant mergedAt,
		@JsonProperty("html_url") String htmlUrl,
		GitHubBranchRefDto base,
		GitHubBranchRefDto head
) {

	/**
	 * Returns {@code true} if the pull request was merged.
	 *
	 * @return true if mergedAt is not null
	 */
	public boolean isMerged() {
		return mergedAt != null;
	}

	/**
	 * Data transfer object for git branch references in pull requests.
	 *
	 * @param ref branch name
	 */
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record GitHubBranchRefDto(
			String ref
	) {
	}
}
