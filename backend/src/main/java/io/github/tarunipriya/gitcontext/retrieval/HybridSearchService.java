package io.github.tarunipriya.gitcontext.retrieval;

import io.github.tarunipriya.gitcontext.config.GitContextProperties;
import io.github.tarunipriya.gitcontext.indexing.SearchHit;
import io.github.tarunipriya.gitcontext.indexing.VectorSearchResult;
import io.github.tarunipriya.gitcontext.indexing.VectorStore;
import io.github.tarunipriya.gitcontext.indexing.qdrant.QdrantUnavailableException;
import io.github.tarunipriya.gitcontext.model.ChunkType;
import io.github.tarunipriya.gitcontext.model.RepoRef;
import io.github.tarunipriya.gitcontext.retrieval.keyword.KeywordIndex;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Orchestrates multi-modal repository search across dense vector similarity, sparse BM25 keyword matching,
 * and Reciprocal Rank Fusion (RRF) hybrid retrieval with graceful degradation.
 */
@Service
public class HybridSearchService {

	private static final Logger log = LoggerFactory.getLogger(HybridSearchService.class);

	private final VectorStore vectorStore;
	private final KeywordIndex keywordIndex;
	private final GitContextProperties properties;

	/**
	 * Creates a new {@link HybridSearchService}.
	 *
	 * @param vectorStore vector store facade for Qdrant
	 * @param keywordIndex Lucene BM25 keyword index facade
	 * @param properties application configuration properties
	 */
	public HybridSearchService(
			VectorStore vectorStore,
			KeywordIndex keywordIndex,
			GitContextProperties properties
	) {
		this.vectorStore = Objects.requireNonNull(vectorStore, "vectorStore must not be null");
		this.keywordIndex = Objects.requireNonNull(keywordIndex, "keywordIndex must not be null");
		this.properties = Objects.requireNonNull(properties, "properties must not be null");
	}

	/**
	 * Searches chunks belonging to the specified repository using the designated search mode.
	 *
	 * @param repo repository reference
	 * @param query search query or question
	 * @param k maximum number of hits to return
	 * @param types optional filter for structural chunk types
	 * @param pathPrefix optional file path prefix filter
	 * @param mode search strategy (VECTOR, KEYWORD, or HYBRID; defaults from config if null)
	 * @return aggregated search outcome with hits, active mode, warnings, and latency breakdown
	 */
	public HybridSearchResult search(
			RepoRef repo,
			String query,
			int k,
			Set<ChunkType> types,
			String pathPrefix,
			SearchMode mode
	) {
		Objects.requireNonNull(repo, "repo must not be null");
		Objects.requireNonNull(query, "query must not be null");

		SearchMode effectiveMode = (mode != null) ? mode : properties.hybrid().defaultMode();
		if (k <= 0) {
			return new HybridSearchResult(List.of(), effectiveMode, List.of(), 0L, 0L, 0L, 0L);
		}

		long totalStart = System.currentTimeMillis();

		return switch (effectiveMode) {
			case VECTOR -> executeVectorSearch(repo, query, k, types, pathPrefix, totalStart);
			case KEYWORD -> executeKeywordSearch(repo, query, k, types, pathPrefix, totalStart);
			case HYBRID -> executeHybridSearch(repo, query, k, types, pathPrefix, totalStart);
		};
	}

	private HybridSearchResult executeVectorSearch(
			RepoRef repo,
			String query,
			int k,
			Set<ChunkType> types,
			String pathPrefix,
			long totalStart
	) {
		VectorSearchResult result = vectorStore.searchWithTimings(repo, query, k, types, pathPrefix);
		long vectorMillis = result.searchMillis() + result.embedMillis();

		List<HybridSearchHit> hits = new ArrayList<>(result.hits().size());
		for (int i = 0; i < result.hits().size(); i++) {
			SearchHit hit = result.hits().get(i);
			hits.add(new HybridSearchHit(
					hit,
					null,
					hit.score(),
					i + 1,
					null,
					null
			));
		}

		long tookMillis = System.currentTimeMillis() - totalStart;
		return new HybridSearchResult(hits, SearchMode.VECTOR, List.of(), vectorMillis, 0L, 0L, tookMillis);
	}

	private HybridSearchResult executeKeywordSearch(
			RepoRef repo,
			String query,
			int k,
			Set<ChunkType> types,
			String pathPrefix,
			long totalStart
	) {
		long kStart = System.currentTimeMillis();
		List<SearchHit> keywordHits = keywordIndex.search(repo, query, k, types, pathPrefix);
		long keywordMillis = System.currentTimeMillis() - kStart;

		List<HybridSearchHit> hits = new ArrayList<>(keywordHits.size());
		for (int i = 0; i < keywordHits.size(); i++) {
			SearchHit hit = keywordHits.get(i);
			hits.add(new HybridSearchHit(
					hit,
					null,
					null,
					null,
					hit.score(),
					i + 1
			));
		}

		long tookMillis = System.currentTimeMillis() - totalStart;
		return new HybridSearchResult(hits, SearchMode.KEYWORD, List.of(), 0L, keywordMillis, 0L, tookMillis);
	}

	private HybridSearchResult executeHybridSearch(
			RepoRef repo,
			String query,
			int k,
			Set<ChunkType> types,
			String pathPrefix,
			long totalStart
	) {
		int candidates = properties.hybrid().candidates();
		int rrfK = properties.hybrid().rrfK();
		List<String> warnings = new ArrayList<>();

		// 1. Vector Candidate Retrieval
		List<SearchHit> vectorHits = List.of();
		long vectorMillis = 0L;
		boolean vectorFailed = false;

		long vStart = System.currentTimeMillis();
		try {
			vectorHits = vectorStore.search(repo, query, candidates, types, pathPrefix);
			vectorMillis = System.currentTimeMillis() - vStart;
		} catch (QdrantUnavailableException e) {
			vectorMillis = System.currentTimeMillis() - vStart;
			vectorFailed = true;
			String warn = "Vector search unavailable (Qdrant error: " + e.getMessage() + "); degraded to keyword-only search";
			log.warn(warn);
			warnings.add(warn);
		} catch (Exception e) {
			vectorMillis = System.currentTimeMillis() - vStart;
			vectorFailed = true;
			String warn = "Vector search failed unexpectedly: " + e.getMessage() + "; degraded to keyword-only search";
			log.warn(warn, e);
			warnings.add(warn);
		}

		// 2. Keyword Candidate Retrieval
		List<SearchHit> keywordHits = List.of();
		long keywordMillis = 0L;
		boolean keywordFailed = false;

		long kStart = System.currentTimeMillis();
		if (!keywordIndex.exists(repo)) {
			keywordMillis = System.currentTimeMillis() - kStart;
			keywordFailed = true;
			String warn = "Keyword index missing for repository " + repo.fullName() + "; degraded to vector-only search";
			log.warn(warn);
			warnings.add(warn);
		} else {
			try {
				keywordHits = keywordIndex.search(repo, query, candidates, types, pathPrefix);
				keywordMillis = System.currentTimeMillis() - kStart;
			} catch (Exception e) {
				keywordMillis = System.currentTimeMillis() - kStart;
				keywordFailed = true;
				String warn = "Keyword search failed unexpectedly: " + e.getMessage() + "; degraded to vector-only search";
				log.warn(warn, e);
				warnings.add(warn);
			}
		}

		// If both retrievers failed, rethrow QdrantUnavailableException if vector failed due to Qdrant
		if (vectorFailed && keywordFailed) {
			throw new QdrantUnavailableException(properties.qdrantUrl(),
					new IllegalStateException("Both vector and keyword search failed for " + repo.fullName()));
		}

		// 3. Reciprocal Rank Fusion
		long fusionStart = System.currentTimeMillis();
		List<HybridSearchHit> fusedHits = ReciprocalRankFusion.fuse(vectorHits, keywordHits, k, rrfK);
		long fusionMillis = System.currentTimeMillis() - fusionStart;

		long tookMillis = System.currentTimeMillis() - totalStart;

		return new HybridSearchResult(
				fusedHits,
				SearchMode.HYBRID,
				warnings,
				vectorMillis,
				keywordMillis,
				fusionMillis,
				tookMillis
		);
	}
}
