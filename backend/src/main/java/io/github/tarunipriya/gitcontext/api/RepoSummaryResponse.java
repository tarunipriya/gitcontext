package io.github.tarunipriya.gitcontext.api;

import io.github.tarunipriya.gitcontext.ingestion.IngestionResult;
import io.github.tarunipriya.gitcontext.model.SourceType;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Diagnostic summary response for the latest completed repository ingestion.
 *
 * <p>Excludes raw document payloads while exposing counts, commit revisions,
 * timestamps, and operational warnings.
 *
 * @param repo repository full identifier ("owner/name")
 * @param headCommitSha 40-character hexadecimal SHA of the indexed HEAD commit
 * @param countsBySourceType document counts grouped by source category
 * @param ingestedAt instant when ingestion was performed
 * @param warnings non-fatal warnings encountered during ingestion
 */
public record RepoSummaryResponse(
		String repo,
		String headCommitSha,
		Map<SourceType, Integer> countsBySourceType,
		Instant ingestedAt,
		List<String> warnings
) {

	/**
	 * Creates a {@link RepoSummaryResponse} from an {@link IngestionResult}.
	 *
	 * @param result full ingestion outcome
	 * @return summary response DTO
	 */
	public static RepoSummaryResponse from(IngestionResult result) {
		return new RepoSummaryResponse(
				result.repo().fullName(),
				result.headCommitSha(),
				result.countsBySourceType(),
				result.ingestedAt(),
				result.warnings()
		);
	}
}
