package io.github.tarunipriya.gitcontext.model;

/**
 * Categorises the structural or semantic granularity of an ingested {@link Chunk}.
 */
public enum ChunkType {
	/**
	 * Class or interface declaration AST node.
	 */
	CLASS,

	/**
	 * Method declaration AST node.
	 */
	METHOD,

	/**
	 * Top-level or nested function declaration AST node.
	 */
	FUNCTION,

	/**
	 * Entire module, namespace, or file-level AST node.
	 */
	MODULE,

	/**
	 * Documentation section delineated by heading hierarchy.
	 */
	SECTION,

	/**
	 * Line-window fallback slice with line-level boundary preservation.
	 */
	LINES,

	/**
	 * Git commit record slice.
	 */
	COMMIT,

	/**
	 * GitHub Pull Request record slice.
	 */
	PULL_REQUEST,

	/**
	 * GitHub Issue record slice.
	 */
	ISSUE
}
