package io.github.tarunipriya.gitcontext.api;

import io.github.tarunipriya.gitcontext.config.GitContextProperties;
import io.github.tarunipriya.gitcontext.indexing.VectorSearchResult;
import io.github.tarunipriya.gitcontext.indexing.VectorStore;
import io.github.tarunipriya.gitcontext.model.ChunkType;
import io.github.tarunipriya.gitcontext.model.RepoRef;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller providing semantic vector search and indexing status endpoints.
 */
@RestController
public class SearchController {

	private static final Logger log = LoggerFactory.getLogger(SearchController.class);

	private final VectorStore vectorStore;
	private final GitContextProperties properties;

	/**
	 * Creates a new {@link SearchController}.
	 *
	 * @param vectorStore vector store facade
	 * @param properties application configuration properties
	 */
	public SearchController(VectorStore vectorStore, GitContextProperties properties) {
		this.vectorStore = Objects.requireNonNull(vectorStore, "vectorStore must not be null");
		this.properties = Objects.requireNonNull(properties, "properties must not be null");
	}

	/**
	 * Executes a semantic vector search across chunks of the specified repository.
	 *
	 * @param owner repository owner
	 * @param name repository name
	 * @param q required query text (1–500 characters after trimming)
	 * @param k maximum number of hits (default 10, min 1, max 50)
	 * @param type optional comma-separated {@link ChunkType} filter values
	 * @param path optional path prefix filter
	 * @return search response containing query, limits, timings, and ranked hits
	 */
	@GetMapping("/api/repos/{owner}/{name}/search")
	public ResponseEntity<SearchResponse> search(
			@PathVariable String owner,
			@PathVariable String name,
			@RequestParam(name = "q", required = false) String q,
			@RequestParam(name = "k", defaultValue = "10") int k,
			@RequestParam(name = "type", required = false) String type,
			@RequestParam(name = "path", required = false) String path
	) {
		RepoRef repo = new RepoRef(owner, name);

		String validatedQuery = validateQuery(q);
		int validatedK = validateK(k);
		Set<ChunkType> types = parseChunkTypes(type);

		long pointsCount = vectorStore.countRepoPoints(repo);
		if (pointsCount == 0) {
			throw new RepoNotFoundException(repo.fullName());
		}

		VectorSearchResult searchResult = vectorStore.searchWithTimings(repo, validatedQuery, validatedK, types, path);

		log.info("Search repo='{}' k={} hits={} embedMillis={} searchMillis={} tookMillis={}",
				repo.fullName(), validatedK, searchResult.hits().size(),
				searchResult.embedMillis(), searchResult.searchMillis(), searchResult.tookMillis());

		List<SearchHitItemResponse> hitResponses = searchResult.hits().stream()
				.map(SearchHitItemResponse::from)
				.toList();

		SearchResponse response = new SearchResponse(
				validatedQuery,
				validatedK,
				searchResult.tookMillis(),
				searchResult.embedMillis(),
				searchResult.searchMillis(),
				hitResponses
		);

		return ResponseEntity.ok(response);
	}

	/**
	 * Returns point count and target collection name for the specified repository.
	 *
	 * @param owner repository owner
	 * @param name repository name
	 * @return indexing status detailing points stored in Qdrant
	 */
	@GetMapping("/api/repos/{owner}/{name}/index-status")
	public ResponseEntity<IndexStatusResponse> getIndexStatus(
			@PathVariable String owner,
			@PathVariable String name
	) {
		RepoRef repo = new RepoRef(owner, name);
		long pointsCount = vectorStore.countRepoPoints(repo);
		String collection = properties.qdrant().collection();
		return ResponseEntity.ok(new IndexStatusResponse(repo.fullName(), collection, pointsCount));
	}

	private String validateQuery(String q) {
		if (q == null || q.trim().isEmpty()) {
			throw new IllegalArgumentException("Query parameter 'q' must not be blank and must be between 1 and 500 characters");
		}
		String trimmed = q.trim();
		if (trimmed.length() > 500) {
			throw new IllegalArgumentException("Query parameter 'q' must not exceed 500 characters (length: " + trimmed.length() + ")");
		}
		return trimmed;
	}

	private int validateK(int k) {
		if (k < 1 || k > 50) {
			throw new IllegalArgumentException("Query parameter 'k' must be between 1 and 50 (got " + k + ")");
		}
		return k;
	}

	private Set<ChunkType> parseChunkTypes(String typeParam) {
		if (typeParam == null || typeParam.isBlank()) {
			return Set.of();
		}
		Set<ChunkType> result = new LinkedHashSet<>();
		String[] parts = typeParam.split(",");
		for (String rawPart : parts) {
			String part = rawPart.trim();
			if (part.isEmpty()) {
				continue;
			}
			try {
				result.add(ChunkType.valueOf(part.toUpperCase(Locale.ROOT)));
			} catch (IllegalArgumentException ex) {
				String allowed = Arrays.stream(ChunkType.values())
						.map(Enum::name)
						.collect(Collectors.joining(", "));
				throw new IllegalArgumentException("Unknown chunk type: '" + part + "'. Allowed values are: [" + allowed + "]");
			}
		}
		return Collections.unmodifiableSet(result);
	}
}
