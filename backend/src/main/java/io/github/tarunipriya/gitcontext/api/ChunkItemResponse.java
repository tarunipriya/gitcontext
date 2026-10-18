package io.github.tarunipriya.gitcontext.api;

import io.github.tarunipriya.gitcontext.model.Chunk;
import io.github.tarunipriya.gitcontext.model.ChunkType;

/**
 * Summarised chunk item representation for search, listing, and pagination.
 *
 * @param id deterministic unique chunk identifier
 * @param documentId identifier of the parent document
 * @param chunkIndex zero-based sequential index within the document
 * @param chunkType structural classification of the chunk
 * @param symbolName identifier of the AST symbol (e.g. method or class name)
 * @param parentSymbol identifier of the enclosing symbol (e.g. parent class)
 * @param headingPath hierarchy of section headings
 * @param path POSIX relative file path
 * @param language programming or markup language
 * @param startLine 1-based inclusive start line number
 * @param endLine 1-based inclusive end line number
 * @param tokenCount token count of the chunk
 * @param url canonical web or blob citation URL with line anchors
 * @param text full contextual text to embed
 */
public record ChunkItemResponse(
		String id,
		String documentId,
		int chunkIndex,
		ChunkType chunkType,
		String symbolName,
		String parentSymbol,
		String headingPath,
		String path,
		String language,
		Integer startLine,
		Integer endLine,
		int tokenCount,
		String url,
		String text
) {

	/**
	 * Creates a {@link ChunkItemResponse} from a domain {@link Chunk}.
	 *
	 * @param chunk source chunk
	 * @return chunk item DTO
	 */
	public static ChunkItemResponse from(Chunk chunk) {
		return new ChunkItemResponse(
				chunk.id(),
				chunk.documentId(),
				chunk.chunkIndex(),
				chunk.metadata().chunkType(),
				chunk.metadata().symbolName(),
				chunk.metadata().parentSymbol(),
				chunk.metadata().headingPath(),
				chunk.metadata().path(),
				chunk.metadata().language(),
				chunk.metadata().startLine(),
				chunk.metadata().endLine(),
				chunk.metadata().tokenCount(),
				chunk.metadata().url(),
				chunk.text()
		);
	}
}
