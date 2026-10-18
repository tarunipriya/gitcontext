package io.github.tarunipriya.gitcontext.indexing.qdrant.dto;

import java.util.Map;

/**
 * Parameters wrapper in collection configuration.
 */
public record QdrantCollectionParams(Map<String, Object> vectors) {
}
