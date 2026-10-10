package io.github.tarunipriya.gitcontext.model;

/**
 * Identifies the origin and type of content ingested from a repository.
 */
public enum SourceType {
	/**
	 * Source code file (e.g. .java, .py, .ts).
	 */
	CODE,

	/**
	 * Documentation file (e.g. README, architecture docs, markdown guides).
	 */
	DOC,

	/**
	 * Git commit history, author info, and commit message.
	 */
	COMMIT,

	/**
	 * Pull request metadata, description, comments, and reviews.
	 */
	PULL_REQUEST,

	/**
	 * Issue discussion (e.g. bug reports, incident-labelled tickets).
	 */
	ISSUE
}
