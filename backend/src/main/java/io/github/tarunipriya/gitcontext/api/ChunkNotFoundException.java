package io.github.tarunipriya.gitcontext.api;

/**
 * Thrown when a chunk ID does not exist within the ingested repository chunks.
 */
public class ChunkNotFoundException extends RuntimeException {

	public ChunkNotFoundException(String chunkId) {
		super("Chunk not found: " + chunkId);
	}
}
