package io.github.tarunipriya.gitcontext.ingestion.github.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

/**
 * Data transfer object representing a pull request code review comment on a diff.
 *
 * @param id review comment identifier
 * @param body review comment text
 * @param path relative file path within the repository
 * @param line line number in the diff/file
 * @param user author user
 * @param createdAt creation timestamp
 * @param htmlUrl web link to the review comment
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubReviewCommentDto(
		Long id,
		String body,
		String path,
		Integer line,
		GitHubUserDto user,
		@JsonProperty("created_at") Instant createdAt,
		@JsonProperty("html_url") String htmlUrl
) {
}
