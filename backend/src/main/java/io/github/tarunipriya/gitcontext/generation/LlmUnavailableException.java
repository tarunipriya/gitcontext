package io.github.tarunipriya.gitcontext.generation;

/**
 * Thrown when the LLM service cannot be invoked (e.g. missing API key, invalid key, model unavailable).
 */
public class LlmUnavailableException extends RuntimeException {

	public LlmUnavailableException(String message) {
		super(message);
	}

	public LlmUnavailableException(String message, Throwable cause) {
		super(message, cause);
	}
}
