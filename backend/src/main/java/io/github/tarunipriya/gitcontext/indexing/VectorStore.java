package io.github.tarunipriya.gitcontext.indexing;

import io.github.tarunipriya.gitcontext.config.GitContextProperties;
import io.github.tarunipriya.gitcontext.indexing.qdrant.QdrantClient;
import io.github.tarunipriya.gitcontext.indexing.qdrant.QdrantException;
import io.github.tarunipriya.gitcontext.indexing.qdrant.QdrantPoint;
import io.github.tarunipriya.gitcontext.indexing.qdrant.QdrantScoredPoint;
import io.github.tarunipriya.gitcontext.model.Chunk;
import io.github.tarunipriya.gitcontext.model.ChunkType;
import io.github.tarunipriya.gitcontext.model.RepoRef;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;

/**
 * High-level facade for indexing chunk vectors into Qdrant and executing filtered similarity searches.
 *
 * <p>Handles collection provisioning, batch document embedding, chunk point mapping,
 * query embedding, client-side metadata filtering, and stale index point pruning.
 */
@Component
public class VectorStore {

	private static final Logger log = LoggerFactory.getLogger(VectorStore.class);

	private final QdrantClient qdrantClient;
	private final EmbeddingService embeddingService;
	private final ChunkPointMapper chunkPointMapper;
	private final GitContextProperties properties;

	/**
	 * Creates a new {@link VectorStore} facade.
	 *
	 * @param qdrantClient low-level Qdrant REST client
	 * @param embeddingService embedding generator
	 * @param chunkPointMapper point and search hit converter
	 * @param properties application configuration properties
	 */
	public VectorStore(
			QdrantClient qdrantClient,
			EmbeddingService embeddingService,
			ChunkPointMapper chunkPointMapper,
			GitContextProperties properties
	) {
		this.qdrantClient = Objects.requireNonNull(qdrantClient, "qdrantClient must not be null");
		this.embeddingService = Objects.requireNonNull(embeddingService, "embeddingService must not be null");
		this.chunkPointMapper = Objects.requireNonNull(chunkPointMapper, "chunkPointMapper must not be null");
		this.properties = Objects.requireNonNull(properties, "properties must not be null");
	}

	/**
	 * Indexes a list of chunks for a repository under a specific run identifier.
	 *
	 * <p>Ensures the target collection exists, embeds the {@link Chunk#text()} representations
	 * in batches, maps them to vector points with metadata payloads, and upserts them into Qdrant.
	 *
	 * @param repo repository reference
	 * @param runId indexing run identifier
	 * @param chunks chunks to index
	 * @return execution summary detailing point counts and elapsed durations
	 */
	/**
	 * Represents an existing point's cached embedding hash and vector.
	 */
	private record ExistingPoint(String embedHash, float[] vector) {
	}

	/**
	 * Indexes a list of chunks for a repository under a specific run identifier.
	 *
	 * <p>Ensures the target collection exists. When {@code gitcontext.indexing.reuse-embeddings} is enabled,
	 * queries existing repository points via {@link QdrantClient#scroll} and reuses cached embeddings for unchanged
	 * chunks, embedding only new or modified chunks. All points are upserted with the new {@code runId}.
	 *
	 * @param repo repository reference
	 * @param runId indexing run identifier
	 * @param chunks chunks to index
	 * @return execution summary detailing point counts and elapsed durations
	 */
	public IndexSummary index(RepoRef repo, String runId, List<Chunk> chunks) {
		Objects.requireNonNull(repo, "repo must not be null");
		Objects.requireNonNull(runId, "runId must not be null");
		Objects.requireNonNull(chunks, "chunks must not be null");

		String collection = properties.qdrant().collection();
		qdrantClient.ensureCollection(collection, embeddingService.dimension());

		if (chunks.isEmpty()) {
			return new IndexSummary(0, 0, 0, 0L, 0L);
		}

		log.info("Indexing {} chunks for {} (runId: {})", chunks.size(), repo.fullName(), runId);

		boolean reuse = properties.indexing() != null && properties.indexing().reuseEmbeddings();
		Map<String, ExistingPoint> existingPoints = new HashMap<>();

		if (reuse) {
			try {
				Map<String, Object> filter = Map.of(
						"must", List.of(Map.of("key", "repo", "match", Map.of("value", repo.normalizedKey())))
				);
				List<QdrantPoint> scrolled = qdrantClient.scroll(
						collection,
						filter,
						true,
						properties.qdrant().upsertBatchSize()
				);
				for (QdrantPoint p : scrolled) {
					if (p.id() != null && p.payload() != null) {
						Object hashObj = p.payload().get("embedHash");
						String hash = hashObj != null ? hashObj.toString() : null;
						existingPoints.put(p.id(), new ExistingPoint(hash, p.vector()));
					}
				}
			} catch (Exception ex) {
				log.warn("Failed to scroll existing points for incremental re-indexing of {}: {}. Falling back to full embedding.",
						repo.fullName(), ex.getMessage());
				existingPoints.clear();
			}
		}

		String modelId = embeddingService.modelId();
		int expectedDimension = embeddingService.dimension();

		List<float[]> vectors = new ArrayList<>(Collections.nCopies(chunks.size(), (float[]) null));
		List<String> embedHashes = new ArrayList<>(chunks.size());
		List<Chunk> chunksToEmbed = new ArrayList<>();
		List<Integer> indicesToEmbed = new ArrayList<>();
		int reusedCount = 0;

		for (int i = 0; i < chunks.size(); i++) {
			Chunk chunk = chunks.get(i);
			String embedHash = ChunkPointMapper.computeEmbedHash(modelId, chunk.text());
			embedHashes.add(embedHash);

			String pointId = ChunkPointMapper.chunkIdToUuid(chunk.id());
			ExistingPoint existing = existingPoints.get(pointId);

			if (reuse && existing != null
					&& Objects.equals(existing.embedHash(), embedHash)
					&& existing.vector() != null
					&& existing.vector().length == expectedDimension) {
				vectors.set(i, existing.vector());
				reusedCount++;
			} else {
				chunksToEmbed.add(chunk);
				indicesToEmbed.add(i);
			}
		}

		long embedStart = System.currentTimeMillis();
		List<float[]> newVectors;
		if (!chunksToEmbed.isEmpty()) {
			List<String> textsToEmbed = chunksToEmbed.stream().map(Chunk::text).toList();
			newVectors = embeddingService.embedDocuments(textsToEmbed);
		} else {
			newVectors = embeddingService.embedDocuments(List.of());
		}
		long embedMillis = System.currentTimeMillis() - embedStart;

		for (int j = 0; j < chunksToEmbed.size(); j++) {
			int chunkIndex = indicesToEmbed.get(j);
			vectors.set(chunkIndex, newVectors.get(j));
		}

		int embeddedCount = chunksToEmbed.size();
		log.info("Embedded {} of {} chunks ({} reused) in {} ms", embeddedCount, chunks.size(), reusedCount, embedMillis);

		List<QdrantPoint> points = new ArrayList<>(chunks.size());
		for (int i = 0; i < chunks.size(); i++) {
			points.add(chunkPointMapper.toPoint(chunks.get(i), vectors.get(i), repo, runId, embedHashes.get(i)));
		}

		long upsertStart = System.currentTimeMillis();
		qdrantClient.upsert(collection, points, properties.qdrant().upsertBatchSize());
		long upsertMillis = System.currentTimeMillis() - upsertStart;

		log.info("Indexed {} chunks for {} in {} ms (embed: {} ms, upsert: {} ms)",
				chunks.size(), repo.fullName(), embedMillis + upsertMillis, embedMillis, upsertMillis);

		return new IndexSummary(chunks.size(), embeddedCount, reusedCount, embedMillis, upsertMillis);
	}

	/**
	 * Searches the vector store for chunks matching the query string within the specified repository.
	 *
	 * @param repo repository to query
	 * @param query search query or question
	 * @param k maximum number of hits to return
	 * @param types optional filter for structural chunk types (null or empty for all)
	 * @param pathPrefix optional file path prefix filter (client-side applied)


	/**
	 * Searches the vector store for chunks matching the query string within the specified repository.
	 *
	 * @param repo repository to query
	 * @param query search query or question
	 * @param k maximum number of hits to return
	 * @param types optional filter for structural chunk types (null or empty for all)
	 * @param pathPrefix optional file path prefix filter (client-side applied)
	 * @return list of search hits sorted in descending order of similarity score
	 */
	public List<SearchHit> search(RepoRef repo, String query, int k, Set<ChunkType> types, String pathPrefix) {
		return searchWithTimings(repo, query, k, types, pathPrefix).hits();
	}

	/**
	 * Searches the vector store for chunks matching the query string and returns hit results alongside execution timings.
	 *
	 * @param repo repository to query
	 * @param query search query or question
	 * @param k maximum number of hits to return
	 * @param types optional filter for structural chunk types (null or empty for all)
	 * @param pathPrefix optional file path prefix filter (client-side applied)
	 * @return search outcome containing hits and stage timings
	 */
	public VectorSearchResult searchWithTimings(RepoRef repo, String query, int k, Set<ChunkType> types, String pathPrefix) {
		Objects.requireNonNull(repo, "repo must not be null");
		Objects.requireNonNull(query, "query must not be null");
		if (k <= 0) {
			return new VectorSearchResult(List.of(), 0L, 0L, 0L);
		}

		long start = System.currentTimeMillis();

		long embedStart = System.currentTimeMillis();
		float[] queryVector = embeddingService.embedQuery(query);
		long embedMillis = System.currentTimeMillis() - embedStart;

		List<Map<String, Object>> mustConditions = new ArrayList<>();
		mustConditions.add(Map.of("key", "repo", "match", Map.of("value", repo.normalizedKey())));

		if (types != null && !types.isEmpty()) {
			if (types.size() == 1) {
				String typeName = types.iterator().next().name();
				mustConditions.add(Map.of("key", "chunkType", "match", Map.of("value", typeName)));
			} else {
				List<String> typeNames = types.stream().map(ChunkType::name).toList();
				mustConditions.add(Map.of("key", "chunkType", "match", Map.of("any", typeNames)));
			}
		}

		Map<String, Object> filter = Map.of("must", mustConditions);
		String collection = properties.qdrant().collection();

		int fetchLimit = (pathPrefix != null && !pathPrefix.isBlank()) ? Math.max(k * 4, 100) : k;

		long searchStart = System.currentTimeMillis();
		List<QdrantScoredPoint> scoredPoints = qdrantClient.search(collection, queryVector, filter, fetchLimit);
		long searchMillis = System.currentTimeMillis() - searchStart;

		List<SearchHit> hits = scoredPoints.stream()
				.map(chunkPointMapper::toSearchHit)
				.toList();

		if (pathPrefix != null && !pathPrefix.isBlank()) {
			String normalizedPrefix = pathPrefix.replace('\\', '/').trim();
			hits = hits.stream()
					.filter(hit -> hit.path() != null && hit.path().startsWith(normalizedPrefix))
					.toList();
		}

		List<SearchHit> sortedHits = hits.stream()
				.sorted(Comparator.comparingDouble(SearchHit::score).reversed())
				.limit(k)
				.toList();

		long tookMillis = System.currentTimeMillis() - start;

		return new VectorSearchResult(sortedHits, embedMillis, searchMillis, tookMillis);
	}

	/**
	 * Counts the total number of vector points currently stored in Qdrant for the given repository.
	 *
	 * @param repo repository identifier reference
	 * @return total point count for this repository in the collection
	 */
	public long countRepoPoints(RepoRef repo) {
		Objects.requireNonNull(repo, "repo must not be null");
		Map<String, Object> filter = Map.of(
				"must", List.of(Map.of("key", "repo", "match", Map.of("value", repo.normalizedKey())))
		);
		try {
			return qdrantClient.count(properties.qdrant().collection(), filter);
		} catch (QdrantException ex) {
			if (ex.getCause() instanceof RestClientResponseException rcre && rcre.getStatusCode().value() == 404) {
				return 0;
			}
			throw ex;
		}
	}

	/**
	 * Deletes stale points belonging to the given repository from older indexing runs.
	 *
	 * @param repo repository reference
	 * @param currentRunId the active run identifier to retain
	 */
	public void deleteStale(RepoRef repo, String currentRunId) {
		Objects.requireNonNull(repo, "repo must not be null");
		Objects.requireNonNull(currentRunId, "currentRunId must not be null");

		Map<String, Object> filter = Map.of(
				"must", List.of(Map.of("key", "repo", "match", Map.of("value", repo.normalizedKey()))),
				"must_not", List.of(Map.of("key", "runId", "match", Map.of("value", currentRunId)))
		);

		log.info("Deleting stale points for repository {} not matching runId {}", repo.fullName(), currentRunId);
		qdrantClient.deleteByFilter(properties.qdrant().collection(), filter);
	}

	/**
	 * Deletes all points belonging to the specified repository.
	 *
	 * @param repo repository reference
	 */
	public void deleteRepo(RepoRef repo) {
		Objects.requireNonNull(repo, "repo must not be null");

		Map<String, Object> filter = Map.of(
				"must", List.of(Map.of("key", "repo", "match", Map.of("value", repo.normalizedKey())))
		);

		log.info("Deleting all points for repository {}", repo.fullName());
		qdrantClient.deleteByFilter(properties.qdrant().collection(), filter);
	}
}
