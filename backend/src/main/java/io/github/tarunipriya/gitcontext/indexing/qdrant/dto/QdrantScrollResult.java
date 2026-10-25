package io.github.tarunipriya.gitcontext.indexing.qdrant.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Result object inside a Qdrant scroll response.
 *
 * @param points list of retrieved points in the current page
 * @param nextPageOffset offset cursor for fetching the next page, or null if complete
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record QdrantScrollResult(
		List<QdrantScrollPoint> points,
		@JsonProperty("next_page_offset") Object nextPageOffset
) {
}
