package io.github.tarunipriya.gitcontext.ingestion;

import io.github.tarunipriya.gitcontext.chunking.ChunkingService;
import io.github.tarunipriya.gitcontext.indexing.IndexSummary;
import io.github.tarunipriya.gitcontext.indexing.VectorStore;
import io.github.tarunipriya.gitcontext.ingestion.RepositoryCloner.ClonedRepo;
import io.github.tarunipriya.gitcontext.ingestion.github.GitHubApiException;
import io.github.tarunipriya.gitcontext.ingestion.github.IssueIngestor;
import io.github.tarunipriya.gitcontext.ingestion.github.PullRequestIngestor;
import io.github.tarunipriya.gitcontext.model.Chunk;
import io.github.tarunipriya.gitcontext.model.ChunkType;
import io.github.tarunipriya.gitcontext.model.Document;
import io.github.tarunipriya.gitcontext.model.RepoRef;
import io.github.tarunipriya.gitcontext.model.SourceType;
import io.github.tarunipriya.gitcontext.retrieval.keyword.KeywordIndex;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

/**
 * Unified ingestion service orchestrating all repository ingestion stages:
 * cloning, file scanning, commit history parsing, pull requests, issues, chunking, and indexing.
 */
@Service
public class IngestionService {

	private static final Logger log = LoggerFactory.getLogger(IngestionService.class);

	private final RepositoryCloner repositoryCloner;
	private final FileIngestor fileIngestor;
	private final CommitIngestor commitIngestor;
	private final PullRequestIngestor pullRequestIngestor;
	private final IssueIngestor issueIngestor;
	private final ChunkingService chunkingService;
	private final VectorStore vectorStore;
	private final KeywordIndex keywordIndex;

	/**
	 * Creates a new {@link IngestionService} with all required stage ingestors.
	 *
	 * @param repositoryCloner repository cloner
	 * @param fileIngestor working tree file ingestor
	 * @param commitIngestor git commit history ingestor
	 * @param pullRequestIngestor GitHub pull request ingestor
	 * @param issueIngestor GitHub issue ingestor
	 * @param chunkingService document chunking service
	 * @param vectorStore vector store indexing facade
	 * @param keywordIndex Lucene BM25 keyword index facade
	 */
	public IngestionService(
			RepositoryCloner repositoryCloner,
			FileIngestor fileIngestor,
			CommitIngestor commitIngestor,
			PullRequestIngestor pullRequestIngestor,
			IssueIngestor issueIngestor,
			ChunkingService chunkingService,
			VectorStore vectorStore,
			KeywordIndex keywordIndex
	) {
		this.repositoryCloner = Objects.requireNonNull(repositoryCloner, "repositoryCloner must not be null");
		this.fileIngestor = Objects.requireNonNull(fileIngestor, "fileIngestor must not be null");
		this.commitIngestor = Objects.requireNonNull(commitIngestor, "commitIngestor must not be null");
		this.pullRequestIngestor = Objects.requireNonNull(pullRequestIngestor, "pullRequestIngestor must not be null");
		this.issueIngestor = Objects.requireNonNull(issueIngestor, "issueIngestor must not be null");
		this.chunkingService = Objects.requireNonNull(chunkingService, "chunkingService must not be null");
		this.vectorStore = Objects.requireNonNull(vectorStore, "vectorStore must not be null");
		this.keywordIndex = Objects.requireNonNull(keywordIndex, "keywordIndex must not be null");
	}

	/**
	 * Executes unified ingestion for the specified repository across all stages with a generated run identifier.
	 *
	 * @param repo repository identifier reference
	 * @return aggregated {@link IngestionResult}
	 */
	public IngestionResult ingest(RepoRef repo) {
		return ingest(repo, UUID.randomUUID().toString());
	}

	/**
	 * Executes unified ingestion for the specified repository across all stages.
	 *
	 * <p>Cloning failures are fatal and propagate immediately. GitHub API, chunking,
	 * and indexing failures are recorded as warnings, allowing repository results to be preserved.
	 *
	 * @param repo repository identifier reference
	 * @param runId indexing run identifier
	 * @return aggregated {@link IngestionResult}
	 */
	public IngestionResult ingest(RepoRef repo, String runId) {
		Objects.requireNonNull(repo, "repo must not be null");
		if (runId == null || runId.isBlank()) {
			runId = UUID.randomUUID().toString();
		}

		long totalStart = System.currentTimeMillis();
		Map<String, Long> stageDurations = new LinkedHashMap<>();
		List<String> warnings = new ArrayList<>();
		List<Document> allDocuments = new ArrayList<>();

		// Stage 1: Clone or update
		long cloneStart = System.currentTimeMillis();
		ClonedRepo clonedRepo = repositoryCloner.cloneOrUpdate(repo);
		long cloneDuration = System.currentTimeMillis() - cloneStart;
		stageDurations.put("clone", cloneDuration);
		log.info("Stage [clone] completed for {} at commit {} in {} ms", repo.fullName(), clonedRepo.headCommitSha(), cloneDuration);

		// Stage 2: Files
		long fileStart = System.currentTimeMillis();
		List<Document> fileDocs = fileIngestor.ingest(clonedRepo);
		long fileDuration = System.currentTimeMillis() - fileStart;
		stageDurations.put("files", fileDuration);
		allDocuments.addAll(fileDocs);
		log.info("Stage [files] completed with {} documents in {} ms", fileDocs.size(), fileDuration);

		// Stage 3: Commits
		long commitStart = System.currentTimeMillis();
		List<Document> commitDocs = commitIngestor.ingest(clonedRepo);
		long commitDuration = System.currentTimeMillis() - commitStart;
		stageDurations.put("commits", commitDuration);
		allDocuments.addAll(commitDocs);
		log.info("Stage [commits] completed with {} documents in {} ms", commitDocs.size(), commitDuration);

		// Stage 4: Pull Requests (non-fatal GitHub API)
		long prStart = System.currentTimeMillis();
		int prCount = 0;
		try {
			List<Document> prDocs = pullRequestIngestor.ingest(repo);
			prCount = prDocs.size();
			allDocuments.addAll(prDocs);
		} catch (GitHubApiException | RestClientException e) {
			String msg = "Pull requests stage failed: " + e.getMessage();
			log.warn("Stage [pullRequests] failed: {}", e.getMessage(), e);
			warnings.add(msg);
		} catch (Exception e) {
			String msg = "Pull requests stage failed unexpectedly: " + e.getMessage();
			log.warn("Stage [pullRequests] failed unexpectedly: {}", e.getMessage(), e);
			warnings.add(msg);
		}
		long prDuration = System.currentTimeMillis() - prStart;
		stageDurations.put("pullRequests", prDuration);
		log.info("Stage [pullRequests] completed with {} documents in {} ms", prCount, prDuration);

		// Stage 5: Issues (non-fatal GitHub API)
		long issueStart = System.currentTimeMillis();
		int issueCount = 0;
		try {
			List<Document> issueDocs = issueIngestor.ingest(repo);
			issueCount = issueDocs.size();
			allDocuments.addAll(issueDocs);
		} catch (GitHubApiException | RestClientException e) {
			String msg = "Issues stage failed: " + e.getMessage();
			log.warn("Stage [issues] failed: {}", e.getMessage(), e);
			warnings.add(msg);
		} catch (Exception e) {
			String msg = "Issues stage failed unexpectedly: " + e.getMessage();
			log.warn("Stage [issues] failed unexpectedly: {}", e.getMessage(), e);
			warnings.add(msg);
		}
		long issueDuration = System.currentTimeMillis() - issueStart;
		stageDurations.put("issues", issueDuration);
		log.info("Stage [issues] completed with {} documents in {} ms", issueCount, issueDuration);

		// Stage 6: Chunking (non-fatal)
		long chunkStart = System.currentTimeMillis();
		List<Chunk> chunks = List.of();
		try {
			chunks = chunkingService.chunkAll(allDocuments);
		} catch (Exception e) {
			String msg = "Chunking stage failed unexpectedly: " + e.getMessage();
			log.warn("Stage [chunking] failed unexpectedly: {}", e.getMessage(), e);
			warnings.add(msg);
			chunks = List.of();
		}
		long chunkDuration = System.currentTimeMillis() - chunkStart;
		stageDurations.put("chunking", chunkDuration);
		log.info("Stage [chunking] completed with {} chunks in {} ms", chunks.size(), chunkDuration);

		// Stage 7: Indexing (non-fatal)
		long indexStart = System.currentTimeMillis();
		int indexedPoints = 0;
		long embedMillis = 0L;
		long upsertMillis = 0L;
		try {
			IndexSummary indexSummary = vectorStore.index(repo, runId, chunks);
			indexedPoints = indexSummary.count();
			embedMillis = indexSummary.embedMillis();
			upsertMillis = indexSummary.upsertMillis();
			vectorStore.deleteStale(repo, runId);
		} catch (Exception e) {
			String reason = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
			String msg = "Indexing skipped: " + reason + "; search will not include this run";
			log.warn("Stage [indexing] failed: {}", e.getMessage(), e);
			warnings.add(msg);
		}
		long indexDuration = System.currentTimeMillis() - indexStart;
		stageDurations.put("indexing", indexDuration);
		log.info("Stage [indexing] completed with {} points in {} ms (embed: {} ms, upsert: {} ms)",
				indexedPoints, indexDuration, embedMillis, upsertMillis);

		// Stage 8: Keyword Indexing (non-fatal)
		long keywordIndexStart = System.currentTimeMillis();
		try {
			keywordIndex.build(repo, chunks);
		} catch (Exception e) {
			String reason = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
			String msg = "Keyword indexing skipped: " + reason;
			log.warn("Stage [keywordIndexing] failed: {}", e.getMessage(), e);
			warnings.add(msg);
		}
		long keywordIndexDuration = System.currentTimeMillis() - keywordIndexStart;
		stageDurations.put("keywordIndexing", keywordIndexDuration);
		log.info("Stage [keywordIndexing] completed in {} ms", keywordIndexDuration);

		// Counts by SourceType
		Map<SourceType, Integer> counts = new EnumMap<>(SourceType.class);
		for (SourceType type : SourceType.values()) {
			counts.put(type, 0);
		}
		for (Document doc : allDocuments) {
			counts.merge(doc.metadata().sourceType(), 1, Integer::sum);
		}

		// Chunk metrics & counts by ChunkType
		Map<ChunkType, Integer> chunkCounts = new EnumMap<>(ChunkType.class);
		for (ChunkType type : ChunkType.values()) {
			chunkCounts.put(type, 0);
		}
		int maxTokens = 0;
		long totalTokens = 0;
		for (Chunk chunk : chunks) {
			chunkCounts.merge(chunk.metadata().chunkType(), 1, Integer::sum);
			int tokens = chunk.metadata().tokenCount();
			if (tokens > maxTokens) {
				maxTokens = tokens;
			}
			totalTokens += tokens;
		}
		double avgTokens = chunks.isEmpty() ? 0.0 : (double) totalTokens / chunks.size();

		long totalDuration = System.currentTimeMillis() - totalStart;
		log.info("Ingestion completed for {} in {} ms: total documents={}, total chunks={}, indexed points={}, warnings={}",
				repo.fullName(), totalDuration, allDocuments.size(), chunks.size(), indexedPoints, warnings.size());

		return new IngestionResult(
				repo,
				clonedRepo.headCommitSha(),
				allDocuments,
				counts,
				chunks,
				chunkCounts,
				avgTokens,
				maxTokens,
				indexedPoints,
				embedMillis,
				upsertMillis,
				stageDurations,
				totalDuration,
				warnings
		);
	}
}
