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

	/**
	 * Extracts a readable title from this search hit's metadata or content.
	 *
	 * @return extracted title, or null if not applicable
	 */
	public String extractTitle() {
		if (sourceType == SourceType.COMMIT || chunkType == ChunkType.COMMIT) {
			if (content != null && !content.isBlank()) {
				return content.lines()
						.map(String::trim)
						.filter(line -> !line.isEmpty())
						.findFirst()
						.orElse(null);
			}
			if (text != null) {
				String firstLine = text.lines().findFirst().orElse("");
				int colonIdx = firstLine.indexOf(": ");
				if (colonIdx >= 0 && firstLine.startsWith("Commit ")) {
					return firstLine.substring(colonIdx + 2).trim();
				}
			}
			return null;
		}

		if (sourceType == SourceType.PULL_REQUEST || chunkType == ChunkType.PULL_REQUEST) {
			if (content != null && !content.isBlank()) {
				String firstLine = content.lines().map(String::trim).filter(l -> !l.isEmpty()).findFirst().orElse("");
				if (firstLine.startsWith("# ")) {
					return firstLine.substring(2).trim();
				}
				return firstLine;
			}
			if (text != null) {
				String firstLine = text.lines().findFirst().orElse("");
				int colonIdx = firstLine.indexOf(": ");
				if (colonIdx >= 0 && firstLine.startsWith("PR #")) {
					return firstLine.substring(colonIdx + 2).trim();
				}
			}
			return null;
		}

		if (sourceType == SourceType.ISSUE || chunkType == ChunkType.ISSUE) {
			if (content != null && !content.isBlank()) {
				String firstLine = content.lines().map(String::trim).filter(l -> !l.isEmpty()).findFirst().orElse("");
				if (firstLine.startsWith("# ")) {
					return firstLine.substring(2).trim();
				}
				return firstLine;
			}
			if (text != null) {
				String firstLine = text.lines().findFirst().orElse("");
				int colonIdx = firstLine.indexOf(": ");
				if (colonIdx >= 0 && firstLine.startsWith("Issue #")) {
					return firstLine.substring(colonIdx + 2).trim();
				}
			}
			return null;
		}

		if (headingPath != null && !headingPath.isBlank()) {
			return headingPath;
		}

		return null;
	}
}
