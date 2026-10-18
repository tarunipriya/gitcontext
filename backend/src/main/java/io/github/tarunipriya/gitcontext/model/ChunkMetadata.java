package io.github.tarunipriya.gitcontext.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Normalised metadata detailing provenance, AST symbols, line ranges, and metrics of an ingested {@link Chunk}.
 *
 * @param repo repository identifier in "owner/name" format (required)
 * @param sourceType origin category of the parent document (required)
 * @param path POSIX-style relative file path, or null if not applicable (optional)
 * @param language programming or markup language identifier (optional)
 * @param url canonical web URL for the document (optional)
 * @param commitSha git commit SHA for commits or file revisions (optional)
 * @param author author username or commit author (optional)
 * @param timestamp creation or modification instant (optional)
 * @param chunkType structural or semantic chunk classification (required)
 * @param symbolName identifier of the extracted symbol (e.g. function or class name, optional)
 * @param parentSymbol identifier of the enclosing symbol (e.g. parent class, optional)
 * @param headingPath hierarchy of section headings (e.g. "README > Setup > Windows", optional)
 * @param startLine 1-based inclusive start line number in source file (optional, null for non-file chunks)
 * @param endLine 1-based inclusive end line number in source file (optional, null for non-file chunks)
 * @param tokenCount estimated or computed token count of the chunk (non-negative)
 */
public record ChunkMetadata(
		String repo,
		SourceType sourceType,
		String path,
		String language,
		String url,
		String commitSha,
		String author,
		Instant timestamp,
		ChunkType chunkType,
		String symbolName,
		String parentSymbol,
		String headingPath,
		Integer startLine,
		Integer endLine,
		int tokenCount
) {

	/**
	 * Compact constructor validating required components and normalising paths.
	 */
	public ChunkMetadata {
		Objects.requireNonNull(repo, "repo must not be null");
		if (repo.isBlank()) {
			throw new IllegalArgumentException("repo must not be blank");
		}
		Objects.requireNonNull(sourceType, "sourceType must not be null");
		Objects.requireNonNull(chunkType, "chunkType must not be null");
		if (tokenCount < 0) {
			throw new IllegalArgumentException("tokenCount must be non-negative");
		}
		if (path != null) {
			path = path.replace('\\', '/');
		}
		if (startLine != null && startLine < 1) {
			throw new IllegalArgumentException("startLine must be >= 1");
		}
		if (endLine != null && endLine < 1) {
			throw new IllegalArgumentException("endLine must be >= 1");
		}
		if (startLine != null && endLine != null && endLine < startLine) {
			throw new IllegalArgumentException("endLine cannot be less than startLine");
		}
	}

	/**
	 * Factory method copying shared provenance from {@link DocumentMetadata} while setting chunk-specific attributes.
	 *
	 * <p>For {@link SourceType#CODE} and {@link SourceType#DOC} chunks with line numbers, sets the chunk URL
	 * to a GitHub blob citation URL with line anchors (e.g. {@code #L147-L192} or single-line {@code #L12}).
	 * Non-file chunks (e.g. commits, pull requests, issues) retain their parent document URL.
	 */
	public static ChunkMetadata from(
			DocumentMetadata docMeta,
			ChunkType chunkType,
			int tokenCount,
			String symbolName,
			String parentSymbol,
			String headingPath,
			Integer startLine,
			Integer endLine
	) {
		Objects.requireNonNull(docMeta, "docMeta must not be null");
		String chunkUrl = computeCitationUrl(docMeta, startLine, endLine);
		return new ChunkMetadata(
				docMeta.repo(),
				docMeta.sourceType(),
				docMeta.path(),
				docMeta.language(),
				chunkUrl,
				docMeta.commitSha(),
				docMeta.author(),
				docMeta.timestamp(),
				chunkType,
				symbolName,
				parentSymbol,
				headingPath,
				startLine,
				endLine,
				tokenCount
		);
	}

	private static String computeCitationUrl(DocumentMetadata docMeta, Integer startLine, Integer endLine) {
		if ((docMeta.sourceType() == SourceType.CODE || docMeta.sourceType() == SourceType.DOC) && startLine != null) {
			String baseUrl = docMeta.url();
			if (baseUrl == null && docMeta.path() != null) {
				String sha = (docMeta.commitSha() != null && !docMeta.commitSha().isBlank())
						? docMeta.commitSha()
						: "HEAD";
				baseUrl = "https://github.com/" + docMeta.repo() + "/blob/" + sha + "/" + docMeta.path();
			}
			if (baseUrl != null) {
				int hashIdx = baseUrl.indexOf('#');
				if (hashIdx >= 0) {
					baseUrl = baseUrl.substring(0, hashIdx);
				}
				String anchor = (endLine != null && !endLine.equals(startLine))
						? "#L" + startLine + "-L" + endLine
						: "#L" + startLine;
				return baseUrl + anchor;
			}
		}
		return docMeta.url();
	}
}
