package io.github.tarunipriya.gitcontext.indexing.qdrant;

/**
 * Exception thrown when the Qdrant vector database cannot be reached over the network.
 */
public class QdrantUnavailableException extends RuntimeException {

	/**
	 * Creates a new {@link QdrantUnavailableException} with a helpful guidance message.
	 *
	 * @param url unreachable Qdrant URL
	 * @param cause underlying network cause
	 */
	public QdrantUnavailableException(String url, Throwable cause) {
		super("Qdrant is not reachable at " + url + ". Please ensure Docker Desktop is running and start the Qdrant service with 'docker compose up -d'.", cause);
	}

	/**
	 * Creates a new {@link QdrantUnavailableException} with a custom message.
	 *
	 * @param message detail message
	 */
	public QdrantUnavailableException(String message) {
		super(message);
	}
}
