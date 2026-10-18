package io.github.tarunipriya.gitcontext.indexing.qdrant;

/**
 * Exception thrown when a Qdrant REST API request returns an HTTP error status or fails unexpectedly.
 */
public class QdrantException extends RuntimeException {

	public QdrantException(String message) {
		super(message);
	}

	public QdrantException(String message, Throwable cause) {
		super(message, cause);
	}
}
