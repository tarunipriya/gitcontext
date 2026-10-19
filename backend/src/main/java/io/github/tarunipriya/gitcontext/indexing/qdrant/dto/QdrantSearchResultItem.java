package io.github.tarunipriya.gitcontext.indexing.qdrant.dto;

import java.util.Map;

/**
 * An individual hit item in a Qdrant search response.
 */
public record QdrantSearchResultItem(String id, double score, Map<String, Object> payload) {
}
