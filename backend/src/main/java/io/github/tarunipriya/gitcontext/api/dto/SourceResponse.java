package io.github.tarunipriya.gitcontext.api.dto;

import io.github.tarunipriya.gitcontext.generation.Source;

/**
 * Numbered source document formatted for context injection in API response.
 */
public record SourceResponse(
		int number,
		String path,
		Integer startLine,
		Integer endLine,
		String chunkType,
		String symbolName,
		String url,
		boolean cited,
		String snippet
) {
	public static SourceResponse from(Source source) {
		var hit = source.hit();
		String content = hit.content();
		String snippet = content != null ? (content.length() > 300 ? content.substring(0, 300) + "..." : content) : "";
		return new SourceResponse(
				source.number(),
				hit.path(),
				hit.startLine(),
				hit.endLine(),
				hit.chunkType() != null ? hit.chunkType().name() : null,
				hit.symbolName(),
				hit.url(),
				source.cited(),
				snippet
		);
	}
}
