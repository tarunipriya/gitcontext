package io.github.tarunipriya.gitcontext.indexing.qdrant.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Top-level response from Qdrant scroll API.
 *
 * @param result scroll result payload
 * @param status response status string (e.g. "ok")
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record QdrantScrollResponse(
		QdrantScrollResult result,
		String status
) {
}
