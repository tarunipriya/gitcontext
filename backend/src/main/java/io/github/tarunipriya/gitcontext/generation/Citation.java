package io.github.tarunipriya.gitcontext.generation;

/**
 * Verified citation linking a factual claim in the answer to a source chunk.
 *
 * @param number source reference number
 * @param path repository relative file path
 * @param startLine 1-indexed starting line
 * @param endLine 1-indexed ending line
 * @param symbolName symbol identifier if available
 * @param url GitHub web URL if available
 * @param chunkId unique chunk identifier
 */
public record Citation(
		int number,
		String path,
		Integer startLine,
		Integer endLine,
		String symbolName,
		String url,
		String chunkId
) {
}
