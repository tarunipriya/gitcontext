package io.github.tarunipriya.gitcontext.indexing.qdrant.dto;

import java.util.Map;

/**
 * Qdrant REST API collection information response.
 */
public record QdrantCollectionInfo(QdrantCollectionResult result, String status) {

	/**
	 * Extracts the vector dimension size from the collection parameters.
	 *
	 * @return dimension size, or -1 if not found
	 */
	public int extractVectorSize() {
		if (result == null || result.config() == null || result.config().params() == null) {
			return -1;
		}
		Map<String, Object> vectors = result.config().params().vectors();
		if (vectors == null) {
			return -1;
		}
		Object sizeObj = vectors.get("size");
		if (sizeObj instanceof Number n) {
			return n.intValue();
		}
		for (Object val : vectors.values()) {
			if (val instanceof Map<?, ?> inner) {
				Object innerSize = inner.get("size");
				if (innerSize instanceof Number n) {
					return n.intValue();
				}
			}
		}
		return -1;
	}
}
