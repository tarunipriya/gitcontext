package io.github.tarunipriya.gitcontext.indexing;

import io.github.tarunipriya.gitcontext.indexing.qdrant.QdrantPoint;
import io.github.tarunipriya.gitcontext.indexing.qdrant.QdrantScoredPoint;
import io.github.tarunipriya.gitcontext.model.Chunk;
import io.github.tarunipriya.gitcontext.model.ChunkType;
import io.github.tarunipriya.gitcontext.model.RepoRef;
import io.github.tarunipriya.gitcontext.model.SourceType;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Maps between internal {@link Chunk} models and Qdrant vector database points and search hits.
 *
 * <p>Converts SHA-256 chunk identifiers into deterministic, valid UUID strings and normalises
 * repository identifiers and metadata attributes for vector store indexing and query retrieval.
 */
@Component
public class ChunkPointMapper {

	/**
	 * Converts a chunk identifier (typically 64-character SHA-256 hex) to a deterministic UUID
	 * using its first 32 hex characters formatted as {@code 8-4-4-4-12}.
	 *
	 * @param chunkId raw chunk identifier string
	 * @return formatted, RFC 4122 compliant UUID string
	 * @throws IllegalArgumentException if the chunk identifier has fewer than 32 hex characters
	 */
	public static String chunkIdToUuid(String chunkId) {
		Objects.requireNonNull(chunkId, "chunkId must not be null");
		String clean = chunkId.replace("-", "").toLowerCase(Locale.ROOT);
		if (clean.length() < 32) {
			throw new IllegalArgumentException("chunkId must have at least 32 hex characters, got " + clean.length());
		}
		String hex32 = clean.substring(0, 32);
		String formatted = hex32.substring(0, 8) + "-" +
				hex32.substring(8, 12) + "-" +
				hex32.substring(12, 16) + "-" +
				hex32.substring(16, 20) + "-" +
				hex32.substring(20, 32);
		return UUID.fromString(formatted).toString();
	}

	/**
	 * Converts an ingested {@link Chunk} and its vector embedding into a {@link QdrantPoint}.
	 *
	 * @param chunk chunk data and metadata
	 * @param vector dense embedding vector
	 * @param repo repository reference
	 * @param runId unique indexing run identifier
	 * @return Qdrant vector point with full metadata payload
	 */
	public QdrantPoint toPoint(Chunk chunk, float[] vector, RepoRef repo, String runId) {
		Objects.requireNonNull(chunk, "chunk must not be null");
		Objects.requireNonNull(vector, "vector must not be null");
		Objects.requireNonNull(repo, "repo must not be null");
		Objects.requireNonNull(runId, "runId must not be null");

		String pointId = chunkIdToUuid(chunk.id());
		Map<String, Object> payload = toPayload(chunk, repo.normalizedKey(), runId);
		return new QdrantPoint(pointId, vector, payload);
	}

	/**
	 * Constructs the metadata payload map for a given chunk.
	 */
	public Map<String, Object> toPayload(Chunk chunk, String normalizedRepoKey, String runId) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("repo", normalizedRepoKey);
		payload.put("runId", runId);
		payload.put("chunkId", chunk.id());
		payload.put("documentId", chunk.documentId());
		payload.put("chunkIndex", chunk.chunkIndex());
		payload.put("sourceType", chunk.metadata().sourceType().name());
		payload.put("chunkType", chunk.metadata().chunkType().name());

		if (chunk.metadata().path() != null) {
			payload.put("path", chunk.metadata().path());
		}
		if (chunk.metadata().language() != null) {
			payload.put("language", chunk.metadata().language());
		}
		if (chunk.metadata().symbolName() != null) {
			payload.put("symbolName", chunk.metadata().symbolName());
		}
		if (chunk.metadata().parentSymbol() != null) {
			payload.put("parentSymbol", chunk.metadata().parentSymbol());
		}
		if (chunk.metadata().headingPath() != null) {
			payload.put("headingPath", chunk.metadata().headingPath());
		}
		if (chunk.metadata().startLine() != null) {
			payload.put("startLine", chunk.metadata().startLine());
		}
		if (chunk.metadata().endLine() != null) {
			payload.put("endLine", chunk.metadata().endLine());
		}
		if (chunk.metadata().url() != null) {
			payload.put("url", chunk.metadata().url());
		}
		if (chunk.metadata().commitSha() != null) {
			payload.put("commitSha", chunk.metadata().commitSha());
		}

		payload.put("tokenCount", chunk.metadata().tokenCount());
		payload.put("text", chunk.text());
		payload.put("content", chunk.content());

		return payload;
	}

	/**
	 * Converts a {@link QdrantScoredPoint} back into a {@link SearchHit}.
	 *
	 * @param scoredPoint scored point from Qdrant search
	 * @return mapped search hit
	 */
	public SearchHit toSearchHit(QdrantScoredPoint scoredPoint) {
		Objects.requireNonNull(scoredPoint, "scoredPoint must not be null");
		return toSearchHit(scoredPoint.id(), scoredPoint.score(), scoredPoint.payload());
	}

	/**
	 * Maps an id, score, and payload map into a strongly-typed {@link SearchHit}.
	 *
	 * @param id Qdrant point UUID string
	 * @param score similarity score
	 * @param payload metadata payload mapping
	 * @return mapped search hit
	 */
	public SearchHit toSearchHit(String id, double score, Map<String, Object> payload) {
		Objects.requireNonNull(id, "id must not be null");
		Objects.requireNonNull(payload, "payload must not be null");

		String repo = (String) payload.get("repo");
		String runId = (String) payload.get("runId");
		String chunkId = (String) payload.get("chunkId");
		String documentId = (String) payload.get("documentId");
		int chunkIndex = asInt(payload.get("chunkIndex"), 0);

		SourceType sourceType = null;
		Object stObj = payload.get("sourceType");
		if (stObj != null) {
			sourceType = SourceType.valueOf(stObj.toString());
		}

		ChunkType chunkType = null;
		Object ctObj = payload.get("chunkType");
		if (ctObj != null) {
			chunkType = ChunkType.valueOf(ctObj.toString());
		}

		String path = (String) payload.get("path");
		String language = (String) payload.get("language");
		String symbolName = (String) payload.get("symbolName");
		String parentSymbol = (String) payload.get("parentSymbol");
		String headingPath = (String) payload.get("headingPath");
		Integer startLine = asInteger(payload.get("startLine"));
		Integer endLine = asInteger(payload.get("endLine"));
		int tokenCount = asInt(payload.get("tokenCount"), 0);
		String url = (String) payload.get("url");
		String commitSha = (String) payload.get("commitSha");
		String text = (String) payload.get("text");
		String content = (String) payload.get("content");

		return new SearchHit(
				id,
				score,
				repo,
				runId,
				chunkId,
				documentId,
				chunkIndex,
				sourceType,
				chunkType,
				path,
				language,
				symbolName,
				parentSymbol,
				headingPath,
				startLine,
				endLine,
				tokenCount,
				url,
				commitSha,
				text,
				content
		);
	}

	private static int asInt(Object value, int fallback) {
		if (value instanceof Number n) {
			return n.intValue();
		}
		if (value instanceof String s) {
			try {
				return Integer.parseInt(s);
			} catch (NumberFormatException ignored) {
			}
		}
		return fallback;
	}

	private static Integer asInteger(Object value) {
		if (value instanceof Number n) {
			return n.intValue();
		}
		if (value instanceof String s) {
			try {
				return Integer.parseInt(s);
			} catch (NumberFormatException ignored) {
			}
		}
		return null;
	}
}
