package io.github.tarunipriya.gitcontext.api;

import io.github.tarunipriya.gitcontext.config.GitContextProperties;
import io.github.tarunipriya.gitcontext.indexing.VectorStore;
import io.github.tarunipriya.gitcontext.indexing.qdrant.QdrantUnavailableException;
import io.github.tarunipriya.gitcontext.model.ChunkType;
import io.github.tarunipriya.gitcontext.model.RepoRef;
import io.github.tarunipriya.gitcontext.retrieval.HybridSearchResult;
import io.github.tarunipriya.gitcontext.retrieval.HybridSearchService;
import io.github.tarunipriya.gitcontext.retrieval.SearchMode;
import io.github.tarunipriya.gitcontext.retrieval.keyword.KeywordIndex;
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
 * REST controller providing repository search across vector, keyword, and hybrid modes, and indexing status.
 */
@RestController
public class SearchController {

	private static final Logger log = LoggerFactory.getLogger(SearchController.class);

	private final HybridSearchService hybridSearchService;
	private final VectorStore vectorStore;
	private final KeywordIndex keywordIndex;
	private final GitContextProperties properties;

	/**
	 * Creates a new {@link SearchController}.
	 *
	 * @param hybridSearchService unified hybrid search service
	 * @param vectorStore vector store facade
	 * @param keywordIndex Lucene keyword index facade
	 * @param properties application configuration properties
	 */
	public SearchController(
			HybridSearchService hybridSearchService,
			VectorStore vectorStore,
			KeywordIndex keywordIndex,
			GitContextProperties properties
	) {
		this.hybridSearchService = Objects.requireNonNull(hybridSearchService, "hybridSearchService must not be null");
		this.vectorStore = Objects.requireNonNull(vectorStore, "vectorStore must not be null");
		this.keywordIndex = Objects.requireNonNull(keywordIndex, "keywordIndex must not be null");
		this.properties = Objects.requireNonNull(properties, "properties must not be null");
	}

	/**
	 * Executes a search across repository chunks using the requested mode (vector, keyword, or hybrid).
	 *
	 * @param owner repository owner
	 * @param name repository name
	 * @param q required query text (1–500 characters after trimming)
	 * @param k maximum number of hits (default 10, min 1, max 50)
	 * @param mode search mode: vector, keyword, or hybrid (case-insensitive, default from config)
	 * @param type optional comma-separated {@link ChunkType} filter values
	 * @param path optional path prefix filter
	 * @param rerank optional boolean requesting cross-encoder reranking (defaults to gitcontext.rerank.enabled)
	 * @param excludeTests optional boolean to filter out test files and directories (default: false)
	 * @return search response containing query, limits, mode, warnings, timings, and ranked hits
	 */
	@GetMapping("/api/repos/{owner}/{name}/search")
	public ResponseEntity<SearchResponse> search(
			@PathVariable String owner,
			@PathVariable String name,
			@RequestParam(name = "q", required = false) String q,
			@RequestParam(name = "k", defaultValue = "10") int k,
			@RequestParam(name = "mode", required = false) String mode,
			@RequestParam(name = "type", required = false) String type,
			@RequestParam(name = "path", required = false) String path,
			@RequestParam(name = "rerank", required = false) Boolean rerank,
			@RequestParam(name = "excludeTests", defaultValue = "false") boolean excludeTests
	) {
		RepoRef repo = new RepoRef(owner, name);

		String validatedQuery = validateQuery(q);
		int validatedK = validateK(k);
		SearchMode searchMode = parseSearchMode(mode);
		Set<ChunkType> types = parseChunkTypes(type);
		boolean effectiveRerank = (rerank != null) ? rerank : properties.rerank().enabled();

		// Check repository existence in both indexes
		boolean keywordExists = keywordIndex.exists(repo);
		boolean vectorExists = false;
		boolean vectorCheckFailed = false;
		QdrantUnavailableException qdrantEx = null;

		try {
			vectorExists = vectorStore.countRepoPoints(repo) > 0;
		} catch (QdrantUnavailableException ex) {
			vectorCheckFailed = true;
			qdrantEx = ex;
		}

		// 503 behavior: only when the chosen mode cannot run at all
		if (searchMode == SearchMode.VECTOR && vectorCheckFailed) {
			throw qdrantEx;
		}
		if (searchMode == SearchMode.KEYWORD && !keywordExists && vectorExists) {
			throw new IllegalStateException("Keyword index missing for repository " + repo.fullName());
		}
		if (searchMode == SearchMode.HYBRID && vectorCheckFailed && !keywordExists) {
			throw qdrantEx;
		}

		// Return 404 only if the repo exists in neither index
		if (!keywordExists && !vectorExists && !vectorCheckFailed) {
			throw new RepoNotFoundException(repo.fullName());
		}

		HybridSearchResult searchResult = hybridSearchService.search(
				repo,
				validatedQuery,
				validatedK,
				types,
				path,
				searchMode,
				effectiveRerank,
				excludeTests
		);

		log.info("Search repo='{}' mode={} k={} reranked={} hits={} tookMillis={}",
				repo.fullName(), searchResult.mode(), validatedK, searchResult.reranked(),
				searchResult.hits().size(), searchResult.tookMillis());

		List<SearchHitItemResponse> hitResponses = searchResult.hits().stream()
				.map(SearchHitItemResponse::from)
				.toList();

		SearchResponse response = new SearchResponse(
				validatedQuery,
				validatedK,
				searchResult.mode(),
				searchResult.reranked(),
				searchResult.tookMillis(),
				searchResult.vectorMillis(),
				searchResult.keywordMillis(),
				searchResult.fusionMillis(),
				searchResult.rerankMillis(),
				0L,
				searchResult.vectorMillis(),
				searchResult.warnings(),
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

	private SearchMode parseSearchMode(String modeParam) {
		if (modeParam == null || modeParam.isBlank()) {
			return properties.hybrid().defaultMode();
		}
		try {
			return SearchMode.valueOf(modeParam.trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			String allowed = Arrays.stream(SearchMode.values())
					.map(Enum::name)
					.collect(Collectors.joining(", "));
			throw new IllegalArgumentException("Unknown search mode: '" + modeParam + "'. Allowed values are: [" + allowed + "]");
		}
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
