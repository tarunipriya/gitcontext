package io.github.tarunipriya.gitcontext.api;

/**
 * Status response detailing indexed vector points and collection metadata for a repository.
 *
 * @param repo repository full identifier ("owner/name")
 * @param collection Qdrant collection name
 * @param pointsCount number of indexed points stored in Qdrant for this repository
 */
public record IndexStatusResponse(
		String repo,
		String collection,
		long pointsCount
) {
	/**
	 * Alias for {@link #pointsCount()} for compatibility.
	 *
	 * @return points count
	 */
	public long points() {
		return pointsCount;
	}
}
