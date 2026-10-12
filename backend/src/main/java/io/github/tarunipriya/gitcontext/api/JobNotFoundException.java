package io.github.tarunipriya.gitcontext.api;

import java.util.UUID;

/**
 * Thrown when an ingestion job with the specified identifier is not found.
 */
public class JobNotFoundException extends RuntimeException {

	public JobNotFoundException(UUID jobId) {
		super("Ingestion job not found: " + jobId);
	}
}
