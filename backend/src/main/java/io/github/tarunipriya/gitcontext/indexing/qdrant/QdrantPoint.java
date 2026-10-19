package io.github.tarunipriya.gitcontext.indexing.qdrant;

import java.util.Map;

/**
 * A vector point to be upserted into a Qdrant collection.
 *
 * @param id UUID string identifier
 * @param vector dense embedding vector
 * @param payload metadata key-value mapping
 */
public record QdrantPoint(
		String id,
		float[] vector,
		Map<String, Object> payload
) {
}
