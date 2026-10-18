package io.github.tarunipriya.gitcontext.indexing.qdrant.dto;

/**
 * Qdrant count response.
 */
public record QdrantCountResponse(QdrantCountResult result, String status) {
}
