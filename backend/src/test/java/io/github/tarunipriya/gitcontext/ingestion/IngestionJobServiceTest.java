package io.github.tarunipriya.gitcontext.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.tarunipriya.gitcontext.model.RepoRef;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class IngestionJobServiceTest {

	private final IngestionService ingestionService = mock(IngestionService.class);
	private final RepoRef repoRef = new RepoRef("testowner", "testrepo");

	@Test
	@DisplayName("Job transitions QUEUED -> RUNNING -> COMPLETED deterministically with synchronous executor")
	void transitionsQueuedToRunningToCompleted() {
		Executor synchronousExecutor = Runnable::run;
		IngestionJobService jobService = new IngestionJobService(ingestionService, synchronousExecutor);

		IngestionResult mockResult = new IngestionResult(
				repoRef,
				"abc123sha",
				List.of(),
				Map.of(),
				Map.of(),
				100L,
				List.of(),
				Instant.now()
		);

		AtomicReference<JobStatus> statusWhileRunning = new AtomicReference<>();
		when(ingestionService.ingest(repoRef)).thenAnswer(invocation -> {
			List<IngestionJob> recent = jobService.getRecentJobs();
			if (!recent.isEmpty()) {
				statusWhileRunning.set(recent.getFirst().status());
			}
			return mockResult;
		});

		IngestionJob submittedJob = jobService.submit(repoRef);

		// Returned immediately as QUEUED snapshot upon submit
		assertThat(submittedJob.status()).isEqualTo(JobStatus.QUEUED);
		assertThat(submittedJob.jobId()).isNotNull();

		// During execution inside the synchronous executor, status was RUNNING
		assertThat(statusWhileRunning.get()).isEqualTo(JobStatus.RUNNING);

		// After synchronous completion, stored job transitioned to COMPLETED
		Optional<IngestionJob> completedJob = jobService.getJob(submittedJob.jobId());
		assertThat(completedJob).isPresent();
		assertThat(completedJob.get().status()).isEqualTo(JobStatus.COMPLETED);
		assertThat(completedJob.get().startedAt()).isNotNull();
		assertThat(completedJob.get().finishedAt()).isNotNull();
		assertThat(completedJob.get().summary()).contains("Ingested 0 documents");
		assertThat(completedJob.get().errorMessage()).isNull();

		// Latest result stored
		Optional<IngestionResult> latest = jobService.getLatestResult(repoRef);
		assertThat(latest).isPresent();
		assertThat(latest.get().headCommitSha()).isEqualTo("abc123sha");
	}

	@Test
	@DisplayName("Job transitions to FAILED on error")
	void jobTransitionsToFailedOnError() {
		Executor synchronousExecutor = Runnable::run;
		IngestionJobService jobService = new IngestionJobService(ingestionService, synchronousExecutor);

		when(ingestionService.ingest(repoRef)).thenThrow(new RuntimeException("Clone failed due to network timeout"));

		IngestionJob submittedJob = jobService.submit(repoRef);
		assertThat(submittedJob.status()).isEqualTo(JobStatus.QUEUED);

		Optional<IngestionJob> failedJob = jobService.getJob(submittedJob.jobId());
		assertThat(failedJob).isPresent();
		assertThat(failedJob.get().status()).isEqualTo(JobStatus.FAILED);
		assertThat(failedJob.get().startedAt()).isNotNull();
		assertThat(failedJob.get().finishedAt()).isNotNull();
		assertThat(failedJob.get().errorMessage()).contains("Clone failed due to network timeout");

		assertThat(jobService.getLatestResult(repoRef)).isEmpty();
	}

	@Test
	@DisplayName("Duplicate submission returns existing job when already QUEUED or RUNNING")
	void duplicateSubmission_returnsExistingJob() {
		// Use a controlled executor that holds the task in QUEUED state
		Executor noopExecutor = task -> {};
		IngestionJobService jobService = new IngestionJobService(ingestionService, noopExecutor);

		IngestionJob job1 = jobService.submit(repoRef);
		assertThat(job1.status()).isEqualTo(JobStatus.QUEUED);

		IngestionJob job2 = jobService.submit(repoRef);
		assertThat(job2.jobId()).isEqualTo(job1.jobId());
		assertThat(job2.status()).isEqualTo(JobStatus.QUEUED);

		// IngestionService was never invoked because executor was noop
		verifyNoInteractions(ingestionService);
	}

	@Test
	@DisplayName("Recent jobs are returned ordered newest first")
	void recentJobs_orderedNewestFirst() throws InterruptedException {
		Executor synchronousExecutor = Runnable::run;
		IngestionJobService jobService = new IngestionJobService(ingestionService, synchronousExecutor);

		RepoRef repo1 = new RepoRef("owner", "repo1");
		RepoRef repo2 = new RepoRef("owner", "repo2");

		IngestionResult mockResult1 = new IngestionResult(repo1, "sha1", List.of(), Map.of(), Map.of(), 50L, List.of());
		IngestionResult mockResult2 = new IngestionResult(repo2, "sha2", List.of(), Map.of(), Map.of(), 50L, List.of());

		when(ingestionService.ingest(repo1)).thenReturn(mockResult1);
		when(ingestionService.ingest(repo2)).thenReturn(mockResult2);

		IngestionJob job1 = jobService.submit(repo1);
		Thread.sleep(10);
		IngestionJob job2 = jobService.submit(repo2);

		List<IngestionJob> recent = jobService.getRecentJobs();
		assertThat(recent).hasSize(2);
		assertThat(recent.getFirst().jobId()).isEqualTo(job2.jobId());
		assertThat(recent.getLast().jobId()).isEqualTo(job1.jobId());
	}

	@Test
	@DisplayName("Executor queue full rejection removes queued job and throws IngestionQueueFullException")
	void executorQueueFull_removesQueuedJobAndThrows() {
		Executor rejectingExecutor = task -> {
			throw new java.util.concurrent.RejectedExecutionException("Queue capacity 100 exceeded");
		};
		IngestionJobService jobService = new IngestionJobService(ingestionService, rejectingExecutor);

		org.assertj.core.api.Assertions.assertThatThrownBy(() -> jobService.submit(repoRef))
				.isInstanceOf(IngestionQueueFullException.class)
				.hasMessageContaining("Ingestion task queue is full");

		// Stored jobs must NOT contain a stranded QUEUED job
		assertThat(jobService.getRecentJobs()).isEmpty();
	}

	@Test
	@DisplayName("Repo lookup and duplicate-job check are case-insensitive")
	void repoLookupAndDuplicateCheck_areCaseInsensitive() {
		Executor synchronousExecutor = Runnable::run;
		IngestionJobService jobService = new IngestionJobService(ingestionService, synchronousExecutor);

		RepoRef lowerRepo = new RepoRef("myowner", "myrepo");
		RepoRef upperRepo = new RepoRef("MYOWNER", "MYREPO");
		RepoRef mixedRepo = new RepoRef("MyOwner", "MyRepo");

		IngestionResult mockResult = new IngestionResult(
				lowerRepo,
				"sha-case",
				List.of(),
				Map.of(),
				Map.of(),
				50L,
				List.of()
		);
		when(ingestionService.ingest(any())).thenReturn(mockResult);

		jobService.submit(lowerRepo);

		// Lookups with different casings return the completed result
		assertThat(jobService.getLatestResult(upperRepo)).isPresent();
		assertThat(jobService.getLatestResult(mixedRepo)).isPresent();
		assertThat(jobService.getLatestResult(upperRepo).get().repo().fullName()).isEqualTo("myowner/myrepo");

		// Duplicate job check with noopExecutor
		Executor noopExecutor = task -> {};
		IngestionJobService noopJobService = new IngestionJobService(ingestionService, noopExecutor);

		IngestionJob job1 = noopJobService.submit(lowerRepo);
		IngestionJob job2 = noopJobService.submit(upperRepo);
		assertThat(job2.jobId()).isEqualTo(job1.jobId());
	}
}
