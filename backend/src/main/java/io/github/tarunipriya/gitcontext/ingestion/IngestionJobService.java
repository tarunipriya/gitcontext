package io.github.tarunipriya.gitcontext.ingestion;

import io.github.tarunipriya.gitcontext.model.RepoRef;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * Service managing asynchronous execution, state transitions, and querying of repository ingestion jobs.
 */
@Service
public class IngestionJobService {

	private static final Logger log = LoggerFactory.getLogger(IngestionJobService.class);

	private final IngestionService ingestionService;
	private final Executor executor;

	// In-memory store; persistent storage arrives with the vector store in Phase 3.
	private final Map<UUID, IngestionJob> jobs = new ConcurrentHashMap<>();
	private final Map<String, IngestionResult> latestResultsByRepo = new ConcurrentHashMap<>();
	private final Object lock = new Object();

	/**
	 * Creates a new {@link IngestionJobService}.
	 *
	 * @param ingestionService core ingestion orchestrator
	 * @param executor dedicated bounded task executor
	 */
	public IngestionJobService(
			IngestionService ingestionService,
			@Qualifier("ingestionExecutor") Executor executor
	) {
		this.ingestionService = Objects.requireNonNull(ingestionService, "ingestionService must not be null");
		this.executor = Objects.requireNonNull(executor, "executor must not be null");
	}

	/**
	 * Submits a repository ingestion job for asynchronous execution.
	 *
	 * <p>If an ingestion for the specified repository is already {@link JobStatus#QUEUED}
	 * or {@link JobStatus#RUNNING}, the existing active job is returned without starting a duplicate.
	 *
	 * @param repo repository identifier reference
	 * @return active or newly created {@link IngestionJob}
	 * @throws IngestionQueueFullException if the worker executor queue rejects the task
	 */
	public IngestionJob submit(RepoRef repo) {
		Objects.requireNonNull(repo, "repo must not be null");

		UUID jobId;
		IngestionJob queuedJob;
		synchronized (lock) {
			for (IngestionJob existing : jobs.values()) {
				if (existing.repo().normalizedKey().equals(repo.normalizedKey())
						&& (existing.status() == JobStatus.QUEUED || existing.status() == JobStatus.RUNNING)) {
					log.info("Ingestion job {} already {} for repo {}", existing.jobId(), existing.status(), repo.fullName());
					return existing;
				}
			}
			jobId = UUID.randomUUID();
			queuedJob = IngestionJob.queued(jobId, repo);
			jobs.put(jobId, queuedJob);
		}

		try {
			executor.execute(() -> runJob(jobId, repo));
		} catch (java.util.concurrent.RejectedExecutionException ex) {
			synchronized (lock) {
				jobs.remove(jobId);
			}
			log.warn("Ingestion job {} rejected for repo {}: executor queue is full", jobId, repo.fullName());
			throw new IngestionQueueFullException("Ingestion task queue is full, please retry later", ex);
		}

		return queuedJob;
	}

	private void runJob(UUID jobId, RepoRef repo) {
		log.info("Starting ingestion job {} for repo {}", jobId, repo.fullName());
		jobs.computeIfPresent(jobId, (id, current) -> current.withRunning(Instant.now()));

		try {
			IngestionResult result = ingestionService.ingest(repo, jobId.toString());
			if (result == null) {
				result = ingestionService.ingest(repo);
			}
			String summary = String.format("Ingested %d documents in %d ms (warnings: %d)",
					result.documents().size(), result.totalDurationMillis(), result.warnings().size());
			jobs.computeIfPresent(jobId, (id, current) -> current.withCompleted(Instant.now(), summary));
			latestResultsByRepo.put(repo.normalizedKey(), result);
			log.info("Completed ingestion job {} for repo {}", jobId, repo.fullName());
		} catch (Exception ex) {
			String error = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
			jobs.computeIfPresent(jobId, (id, current) -> current.withFailed(Instant.now(), error));
			log.error("Ingestion job {} failed for repo {}: {}", jobId, repo.fullName(), error, ex);
		}
	}

	/**
	 * Finds a job by its unique identifier.
	 *
	 * @param jobId job identifier
	 * @return optional containing the job if found
	 */
	public Optional<IngestionJob> getJob(UUID jobId) {
		Objects.requireNonNull(jobId, "jobId must not be null");
		return Optional.ofNullable(jobs.get(jobId));
	}

	/**
	 * Returns recent ingestion jobs ordered newest first (by submission timestamp).
	 *
	 * @return sorted list of recent jobs
	 */
	public List<IngestionJob> getRecentJobs() {
		return jobs.values().stream()
				.sorted(Comparator.comparing(IngestionJob::submittedAt).reversed())
				.toList();
	}

	/**
	 * Finds the latest completed ingestion result for a repository.
	 *
	 * @param repo repository identifier reference
	 * @return optional containing the latest {@link IngestionResult} if available
	 */
	public Optional<IngestionResult> getLatestResult(RepoRef repo) {
		Objects.requireNonNull(repo, "repo must not be null");
		return Optional.ofNullable(latestResultsByRepo.get(repo.normalizedKey()));
	}
}
