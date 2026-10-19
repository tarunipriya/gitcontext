package io.github.tarunipriya.gitcontext.generation;

/**
 * Standardised completion outcome returned by {@link LlmClient}.
 *
 * @param text generated textual output from the model
 * @param model exact model identifier that produced the answer
 * @param promptTokens number of input tokens consumed
 * @param outputTokens number of output tokens produced
 * @param thoughtsTokens number of tokens spent on internal thinking/reasoning (optional)
 * @param finishReason reason the generation completed (e.g. STOP, MAX_TOKENS, SAFETY)
 * @param latencyMillis execution duration in milliseconds
 */
public record LlmResponse(
		String text,
		String model,
		int promptTokens,
		int outputTokens,
		Integer thoughtsTokens,
		String finishReason,
		long latencyMillis
) {
	public LlmResponse(
			String text,
			String model,
			int promptTokens,
			int outputTokens,
			String finishReason,
			long latencyMillis
	) {
		this(text, model, promptTokens, outputTokens, null, finishReason, latencyMillis);
	}
}
