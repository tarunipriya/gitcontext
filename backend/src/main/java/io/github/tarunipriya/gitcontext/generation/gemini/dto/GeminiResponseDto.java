package io.github.tarunipriya.gitcontext.generation.gemini.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * Response payload received from Google Gemini generateContent REST endpoint.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GeminiResponseDto(
		List<GeminiCandidateDto> candidates,
		GeminiUsageMetadataDto usageMetadata,
		String modelVersion
) {

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record GeminiCandidateDto(
			GeminiContentDto content,
			String finishReason,
			Integer index
	) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record GeminiContentDto(
			String role,
			List<GeminiPartDto> parts
	) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record GeminiPartDto(
			String text
	) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record GeminiUsageMetadataDto(
			Integer promptTokenCount,
			Integer candidatesTokenCount,
			Integer totalTokenCount,
			Integer thoughtsTokenCount
	) {
		public GeminiUsageMetadataDto(Integer promptTokenCount, Integer candidatesTokenCount, Integer totalTokenCount) {
			this(promptTokenCount, candidatesTokenCount, totalTokenCount, null);
		}
	}

	/**
	 * Extracts the first text part from candidate content, returning an empty string if null or absent.
	 */
	public String firstCandidateText() {
		if (candidates == null || candidates.isEmpty()) {
			return "";
		}
		GeminiCandidateDto candidate = candidates.get(0);
		if (candidate == null || candidate.content() == null || candidate.content().parts() == null || candidate.content().parts().isEmpty()) {
			return "";
		}
		StringBuilder sb = new StringBuilder();
		for (GeminiPartDto part : candidate.content().parts()) {
			if (part != null && part.text() != null) {
				sb.append(part.text());
			}
		}
		return sb.toString();
	}

	/**
	 * Returns finishReason of the first candidate, or null.
	 */
	public String firstFinishReason() {
		if (candidates == null || candidates.isEmpty()) {
			return null;
		}
		GeminiCandidateDto candidate = candidates.get(0);
		return candidate != null ? candidate.finishReason() : null;
	}

	public int promptTokens() {
		return (usageMetadata != null && usageMetadata.promptTokenCount() != null)
				? usageMetadata.promptTokenCount()
				: 0;
	}

	public int outputTokens() {
		return (usageMetadata != null && usageMetadata.candidatesTokenCount() != null)
				? usageMetadata.candidatesTokenCount()
				: 0;
	}

	public Integer thoughtsTokens() {
		return (usageMetadata != null) ? usageMetadata.thoughtsTokenCount() : null;
	}
}
