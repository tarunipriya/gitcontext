package io.github.tarunipriya.gitcontext.api.dto;

import io.github.tarunipriya.gitcontext.generation.Citation;

/**
 * Verified citation linking response text to an indexed repository chunk.
 */
public record CitationResponse(
		int number,
		String path,
		Integer startLine,
		Integer endLine,
		String symbolName,
		String url,
		String chunkId
) {
	public static CitationResponse from(Citation citation) {
		return new CitationResponse(
				citation.number(),
				citation.path(),
				citation.startLine(),
				citation.endLine(),
				citation.symbolName(),
				citation.url(),
				citation.chunkId()
		);
	}
}
