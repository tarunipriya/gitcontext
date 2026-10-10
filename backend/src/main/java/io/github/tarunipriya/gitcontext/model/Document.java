package io.github.tarunipriya.gitcontext.model;

import java.util.Objects;

/**
 * The core document representation in GitContext.
 *
 * <h2>Identity vs Content Checksum</h2>
 * <p>GitContext differentiates between the document's stable identity ({@code id}) and its payload hash ({@code contentHash}):
 * <ul>
 *   <li><b>{@code id} (Identity-based):</b> Derived deterministically from {@code repo + ":" + sourceType + ":" + key}
 *       (e.g. {@code "owner/repo:CODE:src/Main.java"} or {@code "owner/repo:COMMIT:a1b2c3d"}). Because the identity remains
 *       constant across revisions of the same file or resource, downstream stores (such as Qdrant vector database and Lucene BM25 index)
 *       can perform idempotent upserts and updates whenever a repository is re-indexed.</li>
 *   <li><b>{@code contentHash} (Content-based):</b> Derived from the SHA-256 hash of the UTF-8 document {@code content}.
 *       During incremental re-indexing, comparing the current {@code contentHash} with the indexed record's hash allows the pipeline
 *       to instantly identify unchanged files and skip expensive re-chunking and re-embedding, while cleanly updating stale or modified files.</li>
 * </ul>
 *
 * @param id stable unique identifier derived from repository, source type, and unique key
 * @param content raw text content of the document
 * @param metadata structured metadata detailing the provenance and attributes of the document
 * @param contentHash SHA-256 hash of the document content for change detection
 */
public record Document(
		String id,
		String content,
		DocumentMetadata metadata,
		String contentHash
) {

	/**
	 * Compact constructor validating that no components are null.
	 */
	public Document {
		Objects.requireNonNull(id, "id must not be null");
		Objects.requireNonNull(content, "content must not be null");
		Objects.requireNonNull(metadata, "metadata must not be null");
		Objects.requireNonNull(contentHash, "contentHash must not be null");
	}

	/**
	 * Creates a new {@link Document} by computing the stable identity {@code id} from the metadata's
	 * repository and source type combined with the provided {@code key}, and computing {@code contentHash}
	 * from the UTF-8 {@code content}.
	 *
	 * @param content the raw text content of the document (must not be null)
	 * @param metadata the normalised metadata describing the document (must not be null)
	 * @param key the unique identifier of this entity within its source type (e.g. file path, commit SHA, PR number)
	 * @return a newly created {@link Document} with computed hashes
	 */
	public static Document create(String content, DocumentMetadata metadata, String key) {
		Objects.requireNonNull(content, "content must not be null");
		Objects.requireNonNull(metadata, "metadata must not be null");
		Objects.requireNonNull(key, "key must not be null");

		String identitySource = metadata.repo() + ":" + metadata.sourceType() + ":" + key;
		String id = Hashing.sha256(identitySource);
		String contentHash = Hashing.sha256(content);

		return new Document(id, content, metadata, contentHash);
	}
}
