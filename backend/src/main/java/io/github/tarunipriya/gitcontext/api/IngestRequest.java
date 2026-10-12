package io.github.tarunipriya.gitcontext.api;

import jakarta.validation.constraints.NotBlank;

/**
 * Request payload for initiating repository ingestion.
 *
 * @param repo repository identifier in "owner/name" or GitHub URL format
 */
public record IngestRequest(
		@NotBlank(message = "repo must not be blank")
		String repo
) {}
