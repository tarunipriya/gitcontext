package io.github.tarunipriya.gitcontext.ingestion;

import io.github.tarunipriya.gitcontext.model.RepoRef;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Represents the execution state and metadata of an asynchronous repository ingestion job.
 *
 * @param jobId unique identifier for this job
 * @param repo repository identifier reference
 * @param status current lifecycle status of the job
 * @param submittedAt timestamp when the job was submitted
 * @param startedAt timestamp when job execution began, or null if queued
 * @param finishedAt timestamp when job execution finished, or null if active
 * @param summary human-readable summary of the completed ingestion result, or null
 * @param errorMessage error description if the job failed, or null
 */
public record IngestionJob(
		UUID jobId,
		RepoRef repo,
		JobStatus status,
		Instant submittedAt,
		Instant startedAt,
		Instant finishedAt,
		String summary,
		String errorMessage
) {

	/**
	 * Canonical constructor with null checks on required components.
	 */
	public IngestionJob {
		Objects.requireNonNull(jobId, "jobId must not be null");
		Objects.requireNonNull(repo, "repo must not be null");
		Objects.requireNonNull(status, "status must not be null");
		Objects.requireNonNull(submittedAt, "submittedAt must not be null");
	}

	/**
	 * Creates a newly queued job.
	 *
	 * @param jobId unique job id
	 * @param repo target repository reference
	 * @return new {@link IngestionJob} in {@link JobStatus#QUEUED} state
	 */
	public static IngestionJob queued(UUID jobId, RepoRef repo) {
		return new IngestionJob(jobId, repo, JobStatus.QUEUED, Instant.now(), null, null, null, null);
	}

	/**
	 * Returns a copy of this job transitioned to {@link JobStatus#RUNNING}.
	 *
	 * @param startedAt timestamp when execution began
	 * @return updated {@link IngestionJob}
	 */
	public IngestionJob withRunning(Instant startedAt) {
		return new IngestionJob(jobId, repo, JobStatus.RUNNING, submittedAt, startedAt, null, null, null);
	}

	/**
	 * Returns a copy of this job transitioned to {@link JobStatus#COMPLETED}.
	 *
	 * @param finishedAt timestamp when execution completed
	 * @param summary human-readable summary description
	 * @return updated {@link IngestionJob}
	 */
	public IngestionJob withCompleted(Instant finishedAt, String summary) {
		return new IngestionJob(jobId, repo, JobStatus.COMPLETED, submittedAt, startedAt, finishedAt, summary, null);
	}

	/**
	 * Returns a copy of this job transitioned to {@link JobStatus#FAILED}.
	 *
	 * @param finishedAt timestamp when failure occurred
	 * @param errorMessage description of the failure
	 * @return updated {@link IngestionJob}
	 */
	public IngestionJob withFailed(Instant finishedAt, String errorMessage) {
		return new IngestionJob(jobId, repo, JobStatus.FAILED, submittedAt, startedAt, finishedAt, null, errorMessage);
	}
}
