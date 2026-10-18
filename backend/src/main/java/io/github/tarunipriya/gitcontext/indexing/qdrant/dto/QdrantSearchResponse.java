package io.github.tarunipriya.gitcontext.indexing.qdrant.dto;

import java.util.List;

/**
 * Qdrant search results response.
 */
public record QdrantSearchResponse(List<QdrantSearchResultItem> result, String status) {
}
