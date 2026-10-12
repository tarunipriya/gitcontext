package io.github.tarunipriya.gitcontext.ingestion;

import io.github.tarunipriya.gitcontext.model.Document;
import io.github.tarunipriya.gitcontext.model.RepoRef;
import io.github.tarunipriya.gitcontext.model.SourceType;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Encapsulates the complete outcome of ingesting a repository across all stages.
 *
 * @param repo repository identifier reference
 * @param headCommitSha 40-character hexadecimal SHA of the HEAD commit
 * @param documents unmodifiable list of all ingested {@link Document} records
 * @param countsBySourceType document counts grouped by {@link SourceType}
 * @param stageDurationsMillis execution duration in milliseconds for each ingestion stage
 * @param totalDurationMillis total elapsed execution time in milliseconds
 * @param warnings non-fatal warnings encountered during ingestion (e.g. GitHub API errors)
 * @param ingestedAt timestamp when the ingestion was performed
 */
public record IngestionResult(
		RepoRef repo,
		String headCommitSha,
		List<Document> documents,
		Map<SourceType, Integer> countsBySourceType,
		Map<String, Long> stageDurationsMillis,
		long totalDurationMillis,
		List<String> warnings,
		Instant ingestedAt
) {

	/**
	 * Canonical constructor with defensive copies and validation.
	 */
	public IngestionResult {
		Objects.requireNonNull(repo, "repo must not be null");
		Objects.requireNonNull(headCommitSha, "headCommitSha must not be null");
		documents = documents != null ? List.copyOf(documents) : List.of();
		countsBySourceType = countsBySourceType != null ? Map.copyOf(countsBySourceType) : Map.of();
		stageDurationsMillis = stageDurationsMillis != null ? Map.copyOf(stageDurationsMillis) : Map.of();
		warnings = warnings != null ? List.copyOf(warnings) : List.of();
		ingestedAt = ingestedAt != null ? ingestedAt : Instant.now();
	}

	/**
	 * Convenience constructor defaulting {@code ingestedAt} to now.
	 */
	public IngestionResult(
			RepoRef repo,
			String headCommitSha,
			List<Document> documents,
			Map<SourceType, Integer> countsBySourceType,
			Map<String, Long> stageDurationsMillis,
			long totalDurationMillis,
			List<String> warnings
	) {
		this(repo, headCommitSha, documents, countsBySourceType, stageDurationsMillis, totalDurationMillis, warnings, Instant.now());
	}

	/**
	 * Alias for {@link #stageDurationsMillis()} providing durations per stage.
	 *
	 * @return map of stage name to duration in milliseconds
	 */
	public Map<String, Long> stageDurations() {
		return stageDurationsMillis;
	}
}
