package io.github.tarunipriya.gitcontext.indexing;

import io.github.tarunipriya.gitcontext.indexing.qdrant.QdrantPoint;
import io.github.tarunipriya.gitcontext.indexing.qdrant.QdrantScoredPoint;
import io.github.tarunipriya.gitcontext.model.Chunk;
import io.github.tarunipriya.gitcontext.model.ChunkMetadata;
import io.github.tarunipriya.gitcontext.model.ChunkType;
import io.github.tarunipriya.gitcontext.model.Hashing;
import io.github.tarunipriya.gitcontext.model.RepoRef;
import io.github.tarunipriya.gitcontext.model.SourceType;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChunkPointMapperTest {

	private final ChunkPointMapper mapper = new ChunkPointMapper();

	@Test
	@DisplayName("chunkIdToUuid produces valid and deterministic RFC 4122 UUID strings")
	void testChunkIdToUuidDeterministicAndValid() {
		String chunkId = Hashing.sha256("doc-123:0");
		String uuid1 = ChunkPointMapper.chunkIdToUuid(chunkId);
		String uuid2 = ChunkPointMapper.chunkIdToUuid(chunkId);

		assertThat(uuid1)
				.isNotNull()
				.isEqualTo(uuid2)
				.matches("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");

		UUID parsed = UUID.fromString(uuid1);
		assertThat(parsed).isNotNull();
		assertThat(parsed.toString()).isEqualTo(uuid1);

		// Different chunkId produces different UUID
		String otherChunkId = Hashing.sha256("doc-123:1");
		String otherUuid = ChunkPointMapper.chunkIdToUuid(otherChunkId);
		assertThat(otherUuid).isNotEqualTo(uuid1);
	}

	@Test
	@DisplayName("chunkIdToUuid throws on invalid or too short input")
	void testChunkIdToUuidInvalidInput() {
		assertThatThrownBy(() -> ChunkPointMapper.chunkIdToUuid(null))
				.isInstanceOf(NullPointerException.class);

		assertThatThrownBy(() -> ChunkPointMapper.chunkIdToUuid("too-short"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("at least 32 hex characters");
	}

	@Test
	@DisplayName("toPoint and toSearchHit round-trip all chunk metadata attributes accurately")
	void testPayloadRoundTripToSearchHit() {
		RepoRef repo = RepoRef.parse("TestOwner/TestRepo");
		ChunkMetadata metadata = new ChunkMetadata(
				"testowner/testrepo",
				SourceType.CODE,
				"src/main/java/io/github/tarunipriya/GitHubClient.java",
				"java",
				"https://github.com/testowner/testrepo/blob/abc1234/src/main/java/io/github/tarunipriya/GitHubClient.java#L15-L42",
				"abc1234567890abcdef",
				"taruni",
				Instant.parse("2026-03-01T10:00:00Z"),
				ChunkType.METHOD,
				"handleRateLimits",
				"GitHubClient",
				"Section > Sub",
				15,
				42,
				128
		);

		Chunk chunk = new Chunk(
				"doc-456",
				2,
				"// GitHubClient.java\nvoid handleRateLimits() {}",
				"void handleRateLimits() {}",
				metadata
		);

		float[] vector = new float[]{0.15f, -0.42f, 0.88f};
		String runId = "run-2026-03";

		QdrantPoint point = mapper.toPoint(chunk, vector, repo, runId);

		assertThat(point.id()).isEqualTo(ChunkPointMapper.chunkIdToUuid(chunk.id()));
		assertThat(point.vector()).isEqualTo(vector);
		assertThat(point.payload())
				.containsEntry("repo", "testowner/testrepo")
				.containsEntry("runId", "run-2026-03")
				.containsEntry("chunkId", chunk.id())
				.containsEntry("documentId", "doc-456")
				.containsEntry("chunkIndex", 2)
				.containsEntry("sourceType", "CODE")
				.containsEntry("chunkType", "METHOD")
				.containsEntry("path", "src/main/java/io/github/tarunipriya/GitHubClient.java")
				.containsEntry("language", "java")
				.containsEntry("symbolName", "handleRateLimits")
				.containsEntry("parentSymbol", "GitHubClient")
				.containsEntry("headingPath", "Section > Sub")
				.containsEntry("startLine", 15)
				.containsEntry("endLine", 42)
				.containsEntry("tokenCount", 128)
				.containsEntry("text", "// GitHubClient.java\nvoid handleRateLimits() {}")
				.containsEntry("content", "void handleRateLimits() {}");

		QdrantScoredPoint scoredPoint = new QdrantScoredPoint(point.id(), 0.945, point.payload());
		SearchHit hit = mapper.toSearchHit(scoredPoint);

		assertThat(hit.id()).isEqualTo(point.id());
		assertThat(hit.score()).isEqualTo(0.945);
		assertThat(hit.repo()).isEqualTo("testowner/testrepo");
		assertThat(hit.runId()).isEqualTo("run-2026-03");
		assertThat(hit.chunkId()).isEqualTo(chunk.id());
		assertThat(hit.documentId()).isEqualTo("doc-456");
		assertThat(hit.chunkIndex()).isEqualTo(2);
		assertThat(hit.sourceType()).isEqualTo(SourceType.CODE);
		assertThat(hit.chunkType()).isEqualTo(ChunkType.METHOD);
		assertThat(hit.path()).isEqualTo("src/main/java/io/github/tarunipriya/GitHubClient.java");
		assertThat(hit.language()).isEqualTo("java");
		assertThat(hit.symbolName()).isEqualTo("handleRateLimits");
		assertThat(hit.parentSymbol()).isEqualTo("GitHubClient");
		assertThat(hit.headingPath()).isEqualTo("Section > Sub");
		assertThat(hit.startLine()).isEqualTo(15);
		assertThat(hit.endLine()).isEqualTo(42);
		assertThat(hit.tokenCount()).isEqualTo(128);
		assertThat(hit.url()).isEqualTo(metadata.url());
		assertThat(hit.commitSha()).isEqualTo("abc1234567890abcdef");
		assertThat(hit.text()).isEqualTo(chunk.text());
		assertThat(hit.content()).isEqualTo(chunk.content());
	}
}
