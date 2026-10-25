package io.github.tarunipriya.gitcontext.indexing.qdrant.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Map;

/**
 * An individual point item in a Qdrant scroll response.
 *
 * @param id identifier of the point (UUID or integer)
 * @param payload metadata payload map
 * @param vector dense embedding vector (when requested)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record QdrantScrollPoint(
		Object id,
		Map<String, Object> payload,
		float[] vector
) {
}
