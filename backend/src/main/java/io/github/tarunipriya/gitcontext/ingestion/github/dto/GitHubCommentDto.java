package io.github.tarunipriya.gitcontext.ingestion.github.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

/**
 * Data transfer object representing an issue or pull request discussion comment.
 *
 * @param id comment identifier
 * @param body comment markdown text
 * @param user author user
 * @param createdAt creation timestamp
 * @param htmlUrl web link to the comment
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubCommentDto(
		Long id,
		String body,
		GitHubUserDto user,
		@JsonProperty("created_at") Instant createdAt,
		@JsonProperty("html_url") String htmlUrl
) {
}
