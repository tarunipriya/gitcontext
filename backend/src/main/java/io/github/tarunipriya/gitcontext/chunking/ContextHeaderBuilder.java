package io.github.tarunipriya.gitcontext.chunking;

import io.github.tarunipriya.gitcontext.model.ChunkMetadata;
import io.github.tarunipriya.gitcontext.model.Document;
import io.github.tarunipriya.gitcontext.model.DocumentMetadata;
import io.github.tarunipriya.gitcontext.model.SourceType;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Builds contextual comment headers prepended to chunk embed text.
 *
 * <p>Headers provide essential grounding for retrieval embeddings by specifying the file path,
 * enclosing class/parent symbol, function or symbol name, section hierarchy, and line ranges.
 * For git commits, pull requests, and issues, human-readable record titles and numbers are generated.
 */
@Component
public class ContextHeaderBuilder {

	private static final Set<String> HASH_COMMENT_LANGUAGES = Set.of(
			"python", "py",
			"yaml", "yml",
			"shell", "sh", "bash", "zsh",
			"ruby", "rb",
			"toml",
			"r"
	);

	/**
	 * Builds a file context header using the language's native single-line comment syntax.
	 *
	 * @param path POSIX file path (optional)
	 * @param language programming or markup language (optional)
	 * @param parentSymbol enclosing class or namespace symbol (optional)
	 * @param symbolName function or symbol name (optional)
	 * @param headingPath documentation section path (optional)
	 * @param startLine 1-based start line (optional)
	 * @param endLine 1-based end line (optional)
	 * @return formatted header string (without trailing newline), or empty string if no fields present
	 */
	public String buildFileHeader(
			String path,
			String language,
			String parentSymbol,
			String symbolName,
			String headingPath,
			Integer startLine,
			Integer endLine
	) {
		String prefix = determineCommentPrefix(language);
		List<String> lines = new ArrayList<>();

		if (path != null && !path.isBlank()) {
			lines.add(prefix + "File: " + path.replace('\\', '/'));
		}
		if (parentSymbol != null && !parentSymbol.isBlank()) {
			lines.add(prefix + "Class/Parent: " + parentSymbol.trim());
		}
		if (symbolName != null && !symbolName.isBlank()) {
			lines.add(prefix + "Symbol: " + symbolName.trim());
		}
		if (headingPath != null && !headingPath.isBlank()) {
			lines.add(prefix + "Section: " + headingPath.trim());
		}
		if (startLine != null && endLine != null) {
			lines.add(prefix + "Lines: " + startLine + "-" + endLine);
		}

		return String.join("\n", lines);
	}

	/**
	 * Builds a record header for commits, pull requests, and issues.
	 *
	 * @param sourceType origin category (COMMIT, PULL_REQUEST, or ISSUE)
	 * @param commitSha git commit SHA (optional)
	 * @param prNumber pull request number (optional)
	 * @param issueNumber issue number (optional)
	 * @param title item title or summary (optional)
	 * @return formatted record header string
	 */
	public String buildRecordHeader(
			SourceType sourceType,
			String commitSha,
			Integer prNumber,
			Integer issueNumber,
			String title
	) {
		String cleanTitle = (title != null && !title.isBlank()) ? title.trim() : null;

		return switch (sourceType) {
			case COMMIT -> {
				String shortSha = (commitSha != null && !commitSha.isBlank())
						? commitSha.trim().substring(0, Math.min(7, commitSha.trim().length()))
						: "unknown";
				yield (cleanTitle != null) ? "Commit " + shortSha + ": " + cleanTitle : "Commit " + shortSha;
			}
			case PULL_REQUEST -> {
				String num = (prNumber != null) ? String.valueOf(prNumber) : "?";
				yield (cleanTitle != null) ? "PR #" + num + ": " + cleanTitle : "PR #" + num;
			}
			case ISSUE -> {
				String num = (issueNumber != null) ? String.valueOf(issueNumber) : "?";
				yield (cleanTitle != null) ? "Issue #" + num + ": " + cleanTitle : "Issue #" + num;
			}
			default -> (cleanTitle != null) ? cleanTitle : "";
		};
	}

	/**
	 * Builds a record header directly from a {@link Document}.
	 */
	public String buildRecordHeader(Document document) {
		DocumentMetadata m = document.metadata();
		return buildRecordHeader(m.sourceType(), m.commitSha(), m.prNumber(), m.issueNumber(), m.title());
	}

	/**
	 * Builds a context header from {@link ChunkMetadata}.
	 */
	public String buildHeader(ChunkMetadata metadata) {
		if (metadata.chunkType() == null) {
			return "";
		}
		return switch (metadata.chunkType()) {
			case COMMIT -> buildRecordHeader(SourceType.COMMIT, metadata.commitSha(), null, null, metadata.headingPath());
			case PULL_REQUEST -> buildRecordHeader(SourceType.PULL_REQUEST, null, null, null, metadata.headingPath());
			case ISSUE -> buildRecordHeader(SourceType.ISSUE, null, null, null, metadata.headingPath());
			default -> buildFileHeader(
					metadata.path(),
					metadata.language(),
					metadata.parentSymbol(),
					metadata.symbolName(),
					metadata.headingPath(),
					metadata.startLine(),
					metadata.endLine()
			);
		};
	}

	/**
	 * Determines the single-line comment prefix for the given language.
	 *
	 * @param language programming or markup language
	 * @return {@code "# "} for python/yaml/shell family, {@code "// "} otherwise
	 */
	public String determineCommentPrefix(String language) {
		if (language == null || language.isBlank()) {
			return "// ";
		}
		String normalized = language.trim().toLowerCase(Locale.ROOT);
		if (HASH_COMMENT_LANGUAGES.contains(normalized)) {
			return "# ";
		}
		return "// ";
	}
}
