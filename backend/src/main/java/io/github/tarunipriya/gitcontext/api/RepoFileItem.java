package io.github.tarunipriya.gitcontext.api;

import io.github.tarunipriya.gitcontext.model.SourceType;

/**
 * File summary item detailing ingested file path, type, language, and chunk count.
 *
 * @param path POSIX-style relative file path within the repository
 * @param sourceType document source type (CODE or DOC)
 * @param language programming or markup language identifier
 * @param chunkCount total number of chunks produced from this file
 */
public record RepoFileItem(
		String path,
		SourceType sourceType,
		String language,
		int chunkCount
) {
}
