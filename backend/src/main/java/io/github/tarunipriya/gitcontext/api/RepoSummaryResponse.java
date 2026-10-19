package io.github.tarunipriya.gitcontext.api;

import io.github.tarunipriya.gitcontext.ingestion.IngestionResult;
import io.github.tarunipriya.gitcontext.model.ChunkType;
import io.github.tarunipriya.gitcontext.model.SourceType;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Diagnostic summary response for the latest completed repository ingestion.
 *
 * <p>Excludes raw document payloads while exposing counts, commit revisions,
 * chunking metrics, timestamps, and operational warnings.
 *
 * @param repo repository full identifier ("owner/name")
 * @param headCommitSha 40-character hexadecimal SHA of the indexed HEAD commit
 * @param countsBySourceType document counts grouped by source category
 * @param totalChunks total count of generated chunks
 * @param chunkCountsByType chunk counts grouped by {@link ChunkType}
 * @param avgChunkTokens average token count across all chunks
 * @param maxChunkTokens maximum token count observed across chunks
 * @param indexedPoints total points indexed into vector store
 * @param embedMillis duration in milliseconds for embedding chunks
 * @param upsertMillis duration in milliseconds for upserting points into Qdrant
 * @param ingestedAt instant when ingestion was performed
 * @param warnings non-fatal warnings encountered during ingestion
 */
public record RepoSummaryResponse(
		String repo,
		String headCommitSha,
		Map<SourceType, Integer> countsBySourceType,
		int totalChunks,
		Map<ChunkType, Integer> chunkCountsByType,
		double avgChunkTokens,
		int maxChunkTokens,
		int indexedPoints,
		long embedMillis,
		long upsertMillis,
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
				result.totalChunks(),
				result.chunkCountsByType(),
				result.avgChunkTokens(),
				result.maxChunkTokens(),
				result.indexedPoints(),
				result.embedMillis(),
				result.upsertMillis(),
				result.ingestedAt(),
				result.warnings()
		);
	}
}
