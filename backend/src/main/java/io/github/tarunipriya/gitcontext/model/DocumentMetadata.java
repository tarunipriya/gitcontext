package io.github.tarunipriya.gitcontext.model;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Normalised metadata associated with an ingested {@link Document}.
 *
 * <p>Every ingested source (code files, documentation, commits, PRs, issues) is normalised into this
 * common metadata structure to ensure downstream pipeline stages remain decoupled from source-specific schemas.
 *
 * @param repo repository identifier in "owner/name" format (required)
 * @param sourceType origin category of the document (required)
 * @param path POSIX-style relative path within the repository, or null if not applicable (e.g. issues/PRs)
 * @param url canonical web URL for the document (optional)
 * @param author author username, commit author, or PR/issue creator (optional)
 * @param timestamp creation or modification instant (optional)
 * @param commitSha git commit SHA for commits or file revisions (optional)
 * @param prNumber pull request number if originating from a PR (optional)
 * @param issueNumber issue number if originating from an issue (optional)
 * @param language programming or markup language (e.g. "java", "markdown") (optional)
 * @param title title or summary of the document / issue / PR (optional)
 * @param extra arbitrary source-specific key-value pairs (never null, unmodifiable)
 */
public record DocumentMetadata(
		String repo,
		SourceType sourceType,
		String path,
		String url,
		String author,
		Instant timestamp,
		String commitSha,
		Integer prNumber,
		Integer issueNumber,
		String language,
		String title,
		Map<String, String> extra
) {

	/**
	 * Compact constructor validating required fields and creating defensive copies.
	 */
	public DocumentMetadata {
		Objects.requireNonNull(repo, "repo must not be null");
		if (repo.isBlank()) {
			throw new IllegalArgumentException("repo must not be blank");
		}
		Objects.requireNonNull(sourceType, "sourceType must not be null");

		if (path != null) {
			path = path.replace('\\', '/');
		}

		extra = (extra == null) ? Map.of() : Map.copyOf(extra);
	}
}
