package io.github.tarunipriya.gitcontext.indexing;

import io.github.tarunipriya.gitcontext.model.ChunkType;
import io.github.tarunipriya.gitcontext.model.SourceType;

/**
 * Result of a vector similarity or hybrid search against indexed chunks.
 *
 * @param id Qdrant point UUID
 * @param score similarity score (e.g. Cosine similarity)
 * @param repo normalised repository identifier
 * @param runId indexing run identifier
 * @param chunkId SHA-256 chunk identifier
 * @param documentId parent document identifier
 * @param chunkIndex sequential index within parent document
 * @param sourceType origin category of the parent document
 * @param chunkType structural granularity of the chunk
 * @param path relative file path or null
 * @param language programming or markup language identifier
 * @param symbolName AST symbol name or null
 * @param parentSymbol enclosing symbol name or null
 * @param headingPath hierarchy of section headings or null
 * @param startLine 1-based start line or null
 * @param endLine 1-based end line or null
 * @param url citation URL with line anchors or null
 * @param commitSha git commit SHA or null
 * @param tokenCount estimated token count
 * @param text full chunk representation including comment header
 * @param content raw slice without comment header
 */
public record SearchHit(
		String id,
		double score,
		String repo,
		String runId,
		String chunkId,
		String documentId,
		int chunkIndex,
		SourceType sourceType,
		ChunkType chunkType,
		String path,
		String language,
		String symbolName,
		String parentSymbol,
		String headingPath,
		Integer startLine,
		Integer endLine,
		int tokenCount,
		String url,
		String commitSha,
		String text,
		String content
) {
}
