package io.github.tarunipriya.gitcontext.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request payload for repository question answering.
 */
public record AskRequest(
		@NotBlank(message = "question must not be blank")
		@Size(min = 1, max = 1000, message = "question must be between 1 and 1000 characters")
		String question,
		String mode,
		Boolean excludeTests,
		Boolean rerank,
		Integer maxSources
) {
}
