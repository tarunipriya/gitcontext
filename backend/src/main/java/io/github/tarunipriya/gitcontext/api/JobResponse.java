package io.github.tarunipriya.gitcontext.api;

import io.github.tarunipriya.gitcontext.ingestion.IngestionJob;
import io.github.tarunipriya.gitcontext.ingestion.JobStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * Response payload representing an ingestion job's status and metadata.
 *
 * @param jobId unique identifier of the job
 * @param repo repository identifier in "owner/name" format
 * @param status current lifecycle status of the job
 * @param submittedAt timestamp when the job was submitted
 * @param startedAt timestamp when the job started executing, or null if queued
 * @param finishedAt timestamp when the job finished, or null if active
 * @param summary human-readable summary upon completion, or null
 * @param errorMessage error message upon failure, or null
 */
public record JobResponse(
		UUID jobId,
		String repo,
		JobStatus status,
		Instant submittedAt,
		Instant startedAt,
		Instant finishedAt,
		String summary,
		String errorMessage
) {

	/**
	 * Creates a {@link JobResponse} from an {@link IngestionJob}.
	 *
	 * @param job the domain job record
	 * @return API response representation
	 */
	public static JobResponse from(IngestionJob job) {
		return new JobResponse(
				job.jobId(),
				job.repo().fullName(),
				job.status(),
				job.submittedAt(),
				job.startedAt(),
				job.finishedAt(),
				job.summary(),
				job.errorMessage()
		);
	}
}
