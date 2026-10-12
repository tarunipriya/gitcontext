package io.github.tarunipriya.gitcontext.model;

import java.util.Objects;

/**
 * Represents a segmented, indexed slice of a {@link Document}.
 *
 * <p>Each chunk maintains deterministic identity via SHA-256 of its parent document id and zero-based index:
 * {@code Hashing.sha256(documentId + ":" + chunkIndex)}.
 *
 * <h2>Text vs Content</h2>
 * <ul>
 *   <li><b>{@code text}:</b> Full representation to embed into vector databases, including contextual comment headers
 *       (e.g. file path, enclosing symbol, section path, line ranges).</li>
 *   <li><b>{@code content}:</b> Raw textual slice from the original document source without added headers.</li>
 * </ul>
 *
 * @param id deterministic unique identifier derived from documentId and chunkIndex
 * @param documentId identifier of the parent {@link Document}
 * @param chunkIndex 0-based sequential index of this chunk within the parent document
 * @param text content to embed including context header
 * @param content raw source slice without header
 * @param metadata structured chunk metadata detailing provenance, symbol, and line ranges
 */
public record Chunk(
		String id,
		String documentId,
		int chunkIndex,
		String text,
		String content,
		ChunkMetadata metadata
) {

	/**
	 * Compact constructor validating required components and computing deterministic id if omitted.
	 */
	public Chunk {
		Objects.requireNonNull(documentId, "documentId must not be null");
		if (chunkIndex < 0) {
			throw new IllegalArgumentException("chunkIndex must be non-negative");
		}
		if (id == null) {
			id = Hashing.sha256(documentId + ":" + chunkIndex);
		}
		Objects.requireNonNull(text, "text must not be null");
		Objects.requireNonNull(content, "content must not be null");
		Objects.requireNonNull(metadata, "metadata must not be null");
	}

	/**
	 * Convenience constructor automatically generating the deterministic chunk id.
	 */
	public Chunk(String documentId, int chunkIndex, String text, String content, ChunkMetadata metadata) {
		this(null, documentId, chunkIndex, text, content, metadata);
	}

	/**
	 * Static factory method computing the deterministic chunk id from documentId and chunkIndex.
	 */
	public static Chunk of(String documentId, int chunkIndex, String text, String content, ChunkMetadata metadata) {
		return new Chunk(documentId, chunkIndex, text, content, metadata);
	}
}
