package io.github.tarunipriya.gitcontext.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.github.tarunipriya.gitcontext.generation.Citation;

/**
 * Verified citation linking response text to an indexed repository chunk.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CitationResponse(
		int number,
		String path,
		Integer startLine,
		Integer endLine,
		String symbolName,
		String url,
		String chunkId,
		String title
) {
	public CitationResponse(
			int number,
			String path,
			Integer startLine,
			Integer endLine,
			String symbolName,
			String url,
			String chunkId
	) {
		this(number, path, startLine, endLine, symbolName, url, chunkId, null);
	}

	public static CitationResponse from(Citation citation) {
		return new CitationResponse(
				citation.number(),
				citation.path(),
				citation.startLine(),
				citation.endLine(),
				citation.symbolName(),
				citation.url(),
				citation.chunkId(),
				citation.title()
		);
	}
}
