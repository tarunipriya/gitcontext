package io.github.tarunipriya.gitcontext.api;

import io.github.tarunipriya.gitcontext.indexing.SearchHit;
import io.github.tarunipriya.gitcontext.model.ChunkType;
import java.util.List;

/**
 * Detailed representation of a retrieved search hit in the search API response.
 *
 * @param score similarity score rounded to 3 decimal places
 * @param chunkType structural granularity of the chunk
 * @param symbolName AST symbol name or null
 * @param parentSymbol enclosing symbol name or null
 * @param headingPath hierarchy of section headings or null
 * @param path relative file path or null
 * @param language programming or markup language identifier
 * @param startLine 1-based start line or null
 * @param endLine 1-based end line or null
 * @param url citation URL with line anchors or null
 * @param snippet chunk content without context header, truncated to at most 20 lines
 */
public record SearchHitItemResponse(
		double score,
		ChunkType chunkType,
		String symbolName,
		String parentSymbol,
		String headingPath,
		String path,
		String language,
		Integer startLine,
		Integer endLine,
		String url,
		String snippet
) {

	/**
	 * Creates a {@link SearchHitItemResponse} from a {@link SearchHit}, rounding score to 3 decimals
	 * and limiting content to 20 lines snippet.
	 *
	 * @param hit source search hit
	 * @return formatted hit item response
	 */
	public static SearchHitItemResponse from(SearchHit hit) {
		double roundedScore = Math.round(hit.score() * 1000.0) / 1000.0;
		String snippet = extractSnippet(hit.content());
		return new SearchHitItemResponse(
				roundedScore,
				hit.chunkType(),
				hit.symbolName(),
				hit.parentSymbol(),
				hit.headingPath(),
				hit.path(),
				hit.language(),
				hit.startLine(),
				hit.endLine(),
				hit.url(),
				snippet
		);
	}

	private static String extractSnippet(String content) {
		if (content == null || content.isEmpty()) {
			return "";
		}
		List<String> lines = content.lines().limit(20).toList();
		return String.join("\n", lines);
	}
}
