package io.github.tarunipriya.gitcontext.chunking;

import io.github.tarunipriya.gitcontext.model.Chunk;
import io.github.tarunipriya.gitcontext.model.Document;
import java.util.List;

/**
 * Strategy interface for decomposing a normalised {@link Document} into semantic or structural {@link Chunk}s.
 */
public interface Chunker {

	/**
	 * Determines whether this chunker can handle the specified document based on its source type,
	 * language, or metadata attributes.
	 *
	 * @param document the document candidate (not null)
	 * @return {@code true} if this chunker supports the document, {@code false} otherwise
	 */
	boolean supports(Document document);

	/**
	 * Decomposes the given document into sequential chunks with deterministic identifiers,
	 * context headers, and provenance metadata.
	 *
	 * @param document the document to chunk (not null)
	 * @return list of chunks produced from the document (never null)
	 */
	List<Chunk> chunk(Document document);
}
