package io.github.tarunipriya.gitcontext.indexing.qdrant;

import io.github.tarunipriya.gitcontext.config.GitContextProperties;
import io.github.tarunipriya.gitcontext.indexing.qdrant.dto.QdrantCollectionInfo;
import io.github.tarunipriya.gitcontext.indexing.qdrant.dto.QdrantCountResponse;
import io.github.tarunipriya.gitcontext.indexing.qdrant.dto.QdrantSearchResponse;
import io.github.tarunipriya.gitcontext.indexing.qdrant.dto.QdrantSearchResultItem;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Low-level HTTP client interfacing with the Qdrant vector database REST API via Spring {@link RestClient}.
 *
 * <p>Handles collection lifecycle, keyword payload index creation, batched point upserts,
 * filtered similarity searches, and automatic exception translation for network and HTTP errors.
 */
@Component
public class QdrantClient {

	private static final Logger log = LoggerFactory.getLogger(QdrantClient.class);
	private static final List<String> KEYWORD_INDEX_FIELDS = List.of(
			"repo", "path", "chunkType", "sourceType", "language", "runId"
	);

	private final RestClient restClient;
	private final String baseUrl;
	private final int defaultUpsertBatchSize;

	/**
	 * Creates a new {@link QdrantClient} configured with the given {@link RestClient.Builder}
	 * and strongly-typed application properties.
	 *
	 * @param restClientBuilder Spring RestClient builder
	 * @param properties application configuration properties
	 */
	@org.springframework.beans.factory.annotation.Autowired
	public QdrantClient(RestClient.Builder restClientBuilder, GitContextProperties properties) {
		Objects.requireNonNull(restClientBuilder, "restClientBuilder must not be null");
		Objects.requireNonNull(properties, "properties must not be null");
		this.baseUrl = properties.qdrant().url();
		this.defaultUpsertBatchSize = properties.qdrant().upsertBatchSize();
		this.restClient = restClientBuilder
				.baseUrl(this.baseUrl)
				.build();
	}

	/**
	 * Overloaded constructor allowing direct injection of pre-configured {@link RestClient} instances.
	 *
	 * @param restClient pre-built RestClient
	 * @param baseUrl base URL of the target Qdrant instance
	 * @param defaultUpsertBatchSize default batch size for upserting points
	 */
	public QdrantClient(RestClient restClient, String baseUrl, int defaultUpsertBatchSize) {
		this.restClient = Objects.requireNonNull(restClient, "restClient must not be null");
		this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl must not be null");
		this.defaultUpsertBatchSize = defaultUpsertBatchSize > 0 ? defaultUpsertBatchSize : 128;
	}

	/**
	 * Ensures that the specified collection exists with Cosine distance metric and matching vector dimension.
	 *
	 * <p>If the collection is missing, creates it and provisions keyword payload indexes on
	 * {@code repo}, {@code path}, {@code chunkType}, {@code sourceType}, {@code language}, and {@code runId}.
	 * If the collection exists with a differing vector dimension, throws an {@link IllegalStateException}.
	 *
	 * @param collectionName name of the collection
	 * @param dimension expected vector dimension size (e.g. 384)
	 * @throws IllegalStateException if the collection exists with a different vector size
	 * @throws QdrantUnavailableException if Qdrant cannot be reached
	 */
	public void ensureCollection(String collectionName, int dimension) {
		Objects.requireNonNull(collectionName, "collectionName must not be null");
		Integer existingDimension = null;

		try {
			QdrantCollectionInfo responseInfo = execute(() -> restClient.get()
					.uri("/collections/{name}", collectionName)
					.retrieve()
					.body(QdrantCollectionInfo.class));

			if (responseInfo != null) {
				int size = responseInfo.extractVectorSize();
				if (size > 0) {
					existingDimension = size;
				}
			}
		} catch (QdrantException ex) {
			if (ex.getCause() instanceof RestClientResponseException rcre && rcre.getStatusCode().value() == 404) {
				existingDimension = null;
			} else {
				throw ex;
			}
		}

		if (existingDimension == null) {
			log.info("Creating Qdrant collection '{}' with dimension {} and Cosine distance", collectionName, dimension);
			Map<String, Object> vectorsConfig = Map.of("size", dimension, "distance", "Cosine");
			Map<String, Object> createReq = Map.of("vectors", vectorsConfig);

			execute(() -> restClient.put()
					.uri("/collections/{name}", collectionName)
					.contentType(MediaType.APPLICATION_JSON)
					.body(createReq)
					.retrieve()
					.toBodilessEntity());

			createPayloadIndexes(collectionName);
		} else if (existingDimension != dimension) {
			throw new IllegalStateException("Qdrant collection '" + collectionName + "' already exists with vector size " +
					existingDimension + ", but expected dimension " + dimension + ".");
		} else {
			log.debug("Qdrant collection '{}' already exists with matching dimension {}", collectionName, dimension);
			createPayloadIndexes(collectionName);
		}
	}

	/**
	 * Creates keyword payload indexes on predefined metadata fields.
	 */
	private void createPayloadIndexes(String collectionName) {
		for (String field : KEYWORD_INDEX_FIELDS) {
			try {
				Map<String, Object> indexReq = Map.of("field_name", field, "field_schema", "keyword");
				execute(() -> restClient.put()
						.uri("/collections/{name}/index", collectionName)
						.contentType(MediaType.APPLICATION_JSON)
						.body(indexReq)
						.retrieve()
						.toBodilessEntity());
			} catch (Exception ex) {
				log.debug("Index creation note on field '{}' for collection '{}': {}", field, collectionName, ex.getMessage());
			}
		}
	}

	/**
	 * Upserts a list of vector points into the target collection using the default batch size.
	 *
	 * @param collectionName name of the collection
	 * @param points points to insert or replace
	 */
	public void upsert(String collectionName, List<QdrantPoint> points) {
		upsert(collectionName, points, defaultUpsertBatchSize);
	}

	/**
	 * Upserts a list of vector points into the target collection in chunks of {@code upsertBatchSize} with wait=true.
	 *
	 * @param collectionName name of the collection
	 * @param points points to insert or replace
	 * @param upsertBatchSize batch size per HTTP request
	 */
	public void upsert(String collectionName, List<QdrantPoint> points, int upsertBatchSize) {
		Objects.requireNonNull(collectionName, "collectionName must not be null");
		Objects.requireNonNull(points, "points must not be null");
		if (points.isEmpty()) {
			return;
		}

		int batchSize = upsertBatchSize > 0 ? upsertBatchSize : defaultUpsertBatchSize;
		for (int i = 0; i < points.size(); i += batchSize) {
			int end = Math.min(i + batchSize, points.size());
			List<QdrantPoint> batch = points.subList(i, end);
			Map<String, Object> req = Map.of("points", batch);

			execute(() -> restClient.put()
					.uri("/collections/{name}/points?wait=true", collectionName)
					.contentType(MediaType.APPLICATION_JSON)
					.body(req)
					.retrieve()
					.toBodilessEntity());
		}
	}

	/**
	 * Performs a similarity search against the collection.
	 *
	 * @param collectionName name of the collection
	 * @param vector dense query embedding vector
	 * @param filter optional payload filter map (must/must_not/should conditions)
	 * @param limit maximum number of results to return
	 * @return list of scored points containing point id, score, and payload
	 */
	public List<QdrantScoredPoint> search(String collectionName, float[] vector, Map<String, Object> filter, int limit) {
		Objects.requireNonNull(collectionName, "collectionName must not be null");
		Objects.requireNonNull(vector, "vector must not be null");

		Map<String, Object> req = new LinkedHashMap<>();
		req.put("vector", vector);
		if (filter != null && !filter.isEmpty()) {
			req.put("filter", filter);
		}
		req.put("limit", Math.max(1, limit));
		req.put("with_payload", true);
		req.put("with_vector", false);

		QdrantSearchResponse response = execute(() -> restClient.post()
				.uri("/collections/{name}/points/search", collectionName)
				.contentType(MediaType.APPLICATION_JSON)
				.body(req)
				.retrieve()
				.body(QdrantSearchResponse.class));

		List<QdrantScoredPoint> results = new ArrayList<>();
		if (response != null && response.result() != null) {
			for (QdrantSearchResultItem item : response.result()) {
				results.add(new QdrantScoredPoint(
						item.id(),
						item.score(),
						item.payload() != null ? item.payload() : Map.of()
				));
			}
		}
		return results;
	}

	/**
	 * Deletes points matching the specified filter.
	 *
	 * @param collectionName name of the collection
	 * @param filter payload filter defining points to remove
	 */
	public void deleteByFilter(String collectionName, Map<String, Object> filter) {
		Objects.requireNonNull(collectionName, "collectionName must not be null");
		Objects.requireNonNull(filter, "filter must not be null");

		Map<String, Object> req = Map.of("filter", filter);
		execute(() -> restClient.post()
				.uri("/collections/{name}/points/delete?wait=true", collectionName)
				.contentType(MediaType.APPLICATION_JSON)
				.body(req)
				.retrieve()
				.toBodilessEntity());
	}

	/**
	 * Counts the number of points in a collection matching the given filter.
	 *
	 * @param collectionName name of the collection
	 * @param filter payload filter defining points to count (or null for all points)
	 * @return total matching points count
	 */
	public long count(String collectionName, Map<String, Object> filter) {
		Objects.requireNonNull(collectionName, "collectionName must not be null");

		Map<String, Object> req = new LinkedHashMap<>();
		if (filter != null && !filter.isEmpty()) {
			req.put("filter", filter);
		}
		req.put("exact", true);

		QdrantCountResponse response = execute(() -> restClient.post()
				.uri("/collections/{name}/points/count", collectionName)
				.contentType(MediaType.APPLICATION_JSON)
				.body(req)
				.retrieve()
				.body(QdrantCountResponse.class));

		if (response != null && response.result() != null) {
			return response.result().count();
		}
		return 0;
	}

	/**
	 * Checks whether the Qdrant service is reachable and responding.
	 *
	 * @throws QdrantUnavailableException if unreachable
	 */
	public void ping() {
		execute(() -> restClient.get()
				.uri("/collections")
				.retrieve()
				.toBodilessEntity());
	}

	/**
	 * Returns the configured base URL.
	 *
	 * @return Qdrant base URL
	 */
	public String baseUrl() {
		return baseUrl;
	}

	private <T> T execute(Supplier<T> action) {
		try {
			return action.get();
		} catch (ResourceAccessException ex) {
			throw new QdrantUnavailableException(baseUrl, ex);
		} catch (RestClientResponseException ex) {
			throw new QdrantException("Qdrant request failed with HTTP " + ex.getStatusCode().value() + ": " + ex.getResponseBodyAsString(), ex);
		} catch (Exception ex) {
			if (isConnectionException(ex)) {
				throw new QdrantUnavailableException(baseUrl, ex);
			}
			if (ex instanceof QdrantException qe) {
				throw qe;
			}
			if (ex instanceof QdrantUnavailableException que) {
				throw que;
			}
			throw new QdrantException("Unexpected error during Qdrant request: " + ex.getMessage(), ex);
		}
	}

	private static boolean isConnectionException(Throwable ex) {
		Throwable current = ex;
		while (current != null) {
			if (current instanceof java.net.ConnectException ||
					current instanceof java.net.SocketException ||
					current instanceof java.net.UnknownHostException ||
					current instanceof ResourceAccessException) {
				return true;
			}
			current = current.getCause();
		}
		return false;
	}
}
