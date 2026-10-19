package io.github.tarunipriya.gitcontext.generation.gemini.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * Request payload sent to Google Gemini generateContent REST endpoint.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GeminiRequestDto(
		GeminiContentDto systemInstruction,
		List<GeminiContentDto> contents,
		GeminiGenerationConfigDto generationConfig
) {

	public static GeminiRequestDto of(
			String systemInstruction,
			String userPrompt,
			double temperature,
			int maxOutputTokens,
			GeminiThinkingConfigDto thinkingConfig
	) {
		GeminiContentDto systemContent = (systemInstruction != null && !systemInstruction.isBlank())
				? new GeminiContentDto(null, List.of(new GeminiPartDto(systemInstruction)))
				: null;

		List<GeminiContentDto> userContents = List.of(
				new GeminiContentDto("user", List.of(new GeminiPartDto(userPrompt != null ? userPrompt : "")))
		);

		GeminiGenerationConfigDto config = new GeminiGenerationConfigDto(temperature, maxOutputTokens, thinkingConfig);
		return new GeminiRequestDto(systemContent, userContents, config);
	}

	public static GeminiRequestDto of(String systemInstruction, String userPrompt, double temperature, int maxOutputTokens) {
		return of(systemInstruction, userPrompt, temperature, maxOutputTokens, null);
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record GeminiContentDto(
			String role,
			List<GeminiPartDto> parts
	) {
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record GeminiPartDto(
			String text
	) {
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record GeminiGenerationConfigDto(
			Double temperature,
			Integer maxOutputTokens,
			GeminiThinkingConfigDto thinkingConfig
	) {
		public GeminiGenerationConfigDto(Double temperature, Integer maxOutputTokens) {
			this(temperature, maxOutputTokens, null);
		}
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record GeminiThinkingConfigDto(
			String thinkingLevel,
			Integer thinkingBudget
	) {
		public static GeminiThinkingConfigDto ofLevel(String thinkingLevel) {
			return new GeminiThinkingConfigDto(thinkingLevel, null);
		}

		public static GeminiThinkingConfigDto ofBudget(int thinkingBudget) {
			return new GeminiThinkingConfigDto(null, thinkingBudget);
		}
	}
}
