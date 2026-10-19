package io.github.tarunipriya.gitcontext.generation;

/**
 * Thrown when the LLM service is rate-limited (HTTP 429) after exhausting retry attempts.
 */
public class LlmRateLimitedException extends RuntimeException {

	private final Long retryAfterSeconds;

	public LlmRateLimitedException(String message, Long retryAfterSeconds) {
		super(message);
		this.retryAfterSeconds = retryAfterSeconds;
	}

	public LlmRateLimitedException(String message, Long retryAfterSeconds, Throwable cause) {
		super(message, cause);
		this.retryAfterSeconds = retryAfterSeconds;
	}

	public Long retryAfterSeconds() {
		return retryAfterSeconds;
	}
}
