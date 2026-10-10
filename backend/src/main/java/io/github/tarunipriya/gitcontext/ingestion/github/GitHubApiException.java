package io.github.tarunipriya.gitcontext.ingestion.github;

/**
 * Exception thrown when a GitHub REST API request encounters an error response (such as 401, 403, 404).
 */
public class GitHubApiException extends RuntimeException {

	private final int statusCode;

	/**
	 * Creates a new {@link GitHubApiException}.
	 *
	 * @param statusCode HTTP response status code
	 * @param message descriptive error message
	 */
	public GitHubApiException(int statusCode, String message) {
		super(message);
		this.statusCode = statusCode;
	}

	/**
	 * Creates a new {@link GitHubApiException} with a cause.
	 *
	 * @param statusCode HTTP response status code
	 * @param message descriptive error message
	 * @param cause underlying cause
	 */
	public GitHubApiException(int statusCode, String message, Throwable cause) {
		super(message, cause);
		this.statusCode = statusCode;
	}

	/**
	 * Returns the HTTP status code of the error response.
	 *
	 * @return HTTP status code
	 */
	public int statusCode() {
		return statusCode;
	}
}
