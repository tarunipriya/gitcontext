package io.github.tarunipriya.gitcontext.ingestion;

/**
 * Lifecycle status of an asynchronous ingestion job.
 */
public enum JobStatus {
	QUEUED,
	RUNNING,
	COMPLETED,
	FAILED
}
