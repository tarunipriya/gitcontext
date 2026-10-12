package io.github.tarunipriya.gitcontext.api;

/**
 * Thrown when no completed ingestion exists for a requested repository.
 */
public class RepoNotFoundException extends RuntimeException {

	public RepoNotFoundException(String repoFullName) {
		super("No completed ingestion found for repository: " + repoFullName);
	}
}
