package io.github.tarunipriya.gitcontext.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.github.tarunipriya.gitcontext.generation.Source;
import io.github.tarunipriya.gitcontext.indexing.SearchHit;
import io.github.tarunipriya.gitcontext.model.ChunkType;
import io.github.tarunipriya.gitcontext.model.SourceType;

/**
 * Numbered source document formatted for context injection in API response.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SourceResponse(
		int number,
		String path,
		Integer startLine,
		Integer endLine,
		String chunkType,
		String symbolName,
		String url,
		boolean cited,
		String snippet,
		String title
) {
	public SourceResponse(
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
		this(number, path, startLine, endLine, chunkType, symbolName, url, cited, snippet, null);
	}

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
				snippet,
				extractTitle(hit)
		);
	}

	/**
	 * Extracts a readable title from a search hit's metadata or content.
	 *
	 * @param hit search hit containing chunk metadata and content
	 * @return extracted title, or null if not applicable
	 */
	public static String extractTitle(SearchHit hit) {
		return hit != null ? hit.extractTitle() : null;
	}
}
