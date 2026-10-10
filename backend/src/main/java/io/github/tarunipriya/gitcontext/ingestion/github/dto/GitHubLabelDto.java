package io.github.tarunipriya.gitcontext.ingestion.github.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Data transfer object representing a GitHub issue or PR label.
 *
 * @param name label name
 * @param description label description
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubLabelDto(
		String name,
		String description
) {
}
