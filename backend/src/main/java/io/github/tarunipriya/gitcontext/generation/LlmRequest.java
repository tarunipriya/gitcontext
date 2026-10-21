package io.github.tarunipriya.gitcontext.generation;

/**
 * Request payload for LLM completion or answer generation.
 *
 * @param systemInstruction optional system prompt guiding model persona and rules
 * @param userPrompt user prompt containing question and context
 */
public record LlmRequest(
		String systemInstruction,
		String userPrompt
) {
	public LlmRequest {
		userPrompt = userPrompt != null ? userPrompt : "";
	}
}
