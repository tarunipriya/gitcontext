package io.github.tarunipriya.gitcontext.ingestion;

/**
 * Thrown when an ingestion task cannot be queued or executed because the worker executor queue is saturated.
 */
public class IngestionQueueFullException extends RuntimeException {

	public IngestionQueueFullException(String message) {
		super(message);
	}

	public IngestionQueueFullException(String message, Throwable cause) {
		super(message, cause);
	}
}
