package io.github.tarunipriya.gitcontext.api;

import java.util.List;

/**
 * Generic paginated response wrapper.
 *
 * @param <T> type of items contained within the page
 * @param total total number of matching items before pagination
 * @param limit maximum number of items requested
 * @param offset zero-based starting offset
 * @param items paginated subset of items
 */
public record PageResponse<T>(
		int total,
		int limit,
		int offset,
		List<T> items
) {

	public PageResponse {
		items = items != null ? List.copyOf(items) : List.of();
	}
}
