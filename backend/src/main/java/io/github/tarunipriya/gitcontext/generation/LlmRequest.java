package io.github.tarunipriya.gitcontext.generation;

/**
 * Request payload for LLM completion or answer generation.
 *
 * @param systemInstruction optional system prompt guiding model persona and rules
 * @param userPrompt user prompt containing question and context
 * @param temperature optional temperature override (null uses configured default)
 * @param model optional model override (null uses configured default)
 */
public record LlmRequest(
		String systemInstruction,
		String userPrompt,
		Double temperature,
		String model
) {
	public LlmRequest {
		userPrompt = userPrompt != null ? userPrompt : "";
	}

	/**
	 * Creates an LLM request using default configured temperature and model.
	 *
	 * @param systemInstruction optional system prompt
	 * @param userPrompt user prompt content
	 */
	public LlmRequest(String systemInstruction, String userPrompt) {
		this(systemInstruction, userPrompt, null, null);
	}

	/**
	 * Creates an LLM request with temperature override using default configured model.
	 *
	 * @param systemInstruction optional system prompt
	 * @param userPrompt user prompt content
	 * @param temperature optional temperature override
	 */
	public LlmRequest(String systemInstruction, String userPrompt, Double temperature) {
		this(systemInstruction, userPrompt, temperature, null);
	}
}
