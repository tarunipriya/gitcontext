package io.github.tarunipriya.gitcontext.ingestion.github.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Data transfer object representing a GitHub user account.
 *
 * @param login username / handle
 * @param htmlUrl profile URL on GitHub
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubUserDto(
		String login,
		@JsonProperty("html_url") String htmlUrl
) {
}
