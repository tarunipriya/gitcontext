package io.github.tarunipriya.gitcontext.ingestion;

import io.github.tarunipriya.gitcontext.model.Chunk;
import io.github.tarunipriya.gitcontext.model.ChunkType;
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
 * @param chunks unmodifiable list of all ingested {@link Chunk} segments
 * @param chunkCountsByType chunk counts grouped by {@link ChunkType}
 * @param avgChunkTokens average token count across all chunks (0.0 if empty)
 * @param maxChunkTokens maximum token count observed across chunks (0 if empty)
 * @param indexedPoints total points successfully indexed into vector store
 * @param embeddedChunks number of chunks whose embeddings were computed
 * @param reusedChunks number of chunks whose existing embeddings were reused
 * @param embedMillis duration in milliseconds for embedding chunks
 * @param upsertMillis duration in milliseconds for upserting points into Qdrant
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
		List<Chunk> chunks,
		Map<ChunkType, Integer> chunkCountsByType,
		double avgChunkTokens,
		int maxChunkTokens,
		int indexedPoints,
		int embeddedChunks,
		int reusedChunks,
		long embedMillis,
		long upsertMillis,
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
		chunks = chunks != null ? List.copyOf(chunks) : List.of();
		chunkCountsByType = chunkCountsByType != null ? Map.copyOf(chunkCountsByType) : Map.of();
		stageDurationsMillis = stageDurationsMillis != null ? Map.copyOf(stageDurationsMillis) : Map.of();
		warnings = warnings != null ? List.copyOf(warnings) : List.of();
		ingestedAt = ingestedAt != null ? ingestedAt : Instant.now();
	}

	/**
	 * Overloaded constructor preserving compatibility for callers with all properties except embeddedChunks and reusedChunks.
	 */
	public IngestionResult(
			RepoRef repo,
			String headCommitSha,
			List<Document> documents,
			Map<SourceType, Integer> countsBySourceType,
			List<Chunk> chunks,
			Map<ChunkType, Integer> chunkCountsByType,
			double avgChunkTokens,
			int maxChunkTokens,
			int indexedPoints,
			long embedMillis,
			long upsertMillis,
			Map<String, Long> stageDurationsMillis,
			long totalDurationMillis,
			List<String> warnings,
			Instant ingestedAt
	) {
		this(repo, headCommitSha, documents, countsBySourceType, chunks, chunkCountsByType, avgChunkTokens, maxChunkTokens, indexedPoints, indexedPoints, 0, embedMillis, upsertMillis, stageDurationsMillis, totalDurationMillis, warnings, ingestedAt);
	}

	/**
	 * Convenience constructor with all indexing metrics defaulting {@code ingestedAt} to now.
	 */
	public IngestionResult(
			RepoRef repo,
			String headCommitSha,
			List<Document> documents,
			Map<SourceType, Integer> countsBySourceType,
			List<Chunk> chunks,
			Map<ChunkType, Integer> chunkCountsByType,
			double avgChunkTokens,
			int maxChunkTokens,
			int indexedPoints,
			int embeddedChunks,
			int reusedChunks,
			long embedMillis,
			long upsertMillis,
			Map<String, Long> stageDurationsMillis,
			long totalDurationMillis,
			List<String> warnings
	) {
		this(repo, headCommitSha, documents, countsBySourceType, chunks, chunkCountsByType, avgChunkTokens, maxChunkTokens, indexedPoints, embeddedChunks, reusedChunks, embedMillis, upsertMillis, stageDurationsMillis, totalDurationMillis, warnings, Instant.now());
	}

	/**
	 * Convenience constructor with indexing metrics defaulting {@code ingestedAt} to now, and embeddedChunks to indexedPoints.
	 */
	public IngestionResult(
			RepoRef repo,
			String headCommitSha,
			List<Document> documents,
			Map<SourceType, Integer> countsBySourceType,
			List<Chunk> chunks,
			Map<ChunkType, Integer> chunkCountsByType,
			double avgChunkTokens,
			int maxChunkTokens,
			int indexedPoints,
			long embedMillis,
			long upsertMillis,
			Map<String, Long> stageDurationsMillis,
			long totalDurationMillis,
			List<String> warnings
	) {
		this(repo, headCommitSha, documents, countsBySourceType, chunks, chunkCountsByType, avgChunkTokens, maxChunkTokens, indexedPoints, indexedPoints, 0, embedMillis, upsertMillis, stageDurationsMillis, totalDurationMillis, warnings, Instant.now());
	}

	/**
	 * Convenience constructor without indexing metrics defaulting them to zero and {@code ingestedAt} to now.
	 */
	public IngestionResult(
			RepoRef repo,
			String headCommitSha,
			List<Document> documents,
			Map<SourceType, Integer> countsBySourceType,
			List<Chunk> chunks,
			Map<ChunkType, Integer> chunkCountsByType,
			double avgChunkTokens,
			int maxChunkTokens,
			Map<String, Long> stageDurationsMillis,
			long totalDurationMillis,
			List<String> warnings
	) {
		this(repo, headCommitSha, documents, countsBySourceType, chunks, chunkCountsByType, avgChunkTokens, maxChunkTokens, 0, 0, 0, 0L, 0L, stageDurationsMillis, totalDurationMillis, warnings, Instant.now());
	}

	/**
	 * Compatibility constructor for tests without chunk parameters.
	 */
	public IngestionResult(
			RepoRef repo,
			String headCommitSha,
			List<Document> documents,
			Map<SourceType, Integer> countsBySourceType,
			Map<String, Long> stageDurationsMillis,
			long totalDurationMillis,
			List<String> warnings,
			Instant ingestedAt
	) {
		this(repo, headCommitSha, documents, countsBySourceType, List.of(), Map.of(), 0.0, 0, 0, 0, 0, 0L, 0L, stageDurationsMillis, totalDurationMillis, warnings, ingestedAt);
	}

	/**
	 * Compatibility constructor for tests without chunk parameters defaulting {@code ingestedAt} to now.
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
		this(repo, headCommitSha, documents, countsBySourceType, List.of(), Map.of(), 0.0, 0, 0, 0, 0, 0L, 0L, stageDurationsMillis, totalDurationMillis, warnings, Instant.now());
	}

	/**
	 * Total count of generated chunks.
	 *
	 * @return number of chunks
	 */
	public int totalChunks() {
		return chunks.size();
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
