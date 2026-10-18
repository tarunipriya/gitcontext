package io.github.tarunipriya.gitcontext.indexing.qdrant;

import java.util.Map;

/**
 * A scored point returned from a Qdrant similarity search.
 *
 * @param id point UUID string
 * @param score similarity score
 * @param payload metadata payload mapping
 */
public record QdrantScoredPoint(
		String id,
		double score,
		Map<String, Object> payload
) {
}
