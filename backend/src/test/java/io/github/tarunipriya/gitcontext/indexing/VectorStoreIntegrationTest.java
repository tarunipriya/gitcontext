package io.github.tarunipriya.gitcontext.indexing;

import io.github.tarunipriya.gitcontext.config.GitContextProperties;
import io.github.tarunipriya.gitcontext.indexing.qdrant.QdrantClient;
import io.github.tarunipriya.gitcontext.model.Chunk;
import io.github.tarunipriya.gitcontext.model.ChunkMetadata;
import io.github.tarunipriya.gitcontext.model.ChunkType;
import io.github.tarunipriya.gitcontext.model.RepoRef;
import io.github.tarunipriya.gitcontext.model.SourceType;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.qdrant.QdrantContainer;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class VectorStoreIntegrationTest {

	@Container
	static final QdrantContainer qdrant = new QdrantContainer("qdrant/qdrant:v1.13.4");

	private VectorStore vectorStore;
	private QdrantClient qdrantClient;
	private GitContextProperties properties;
	private final RepoRef testRepo = RepoRef.parse("testowner/gitcontext-test");

	@BeforeEach
	void setUp() {
		String qdrantUrl = "http://" + qdrant.getHost() + ":" + qdrant.getMappedPort(6333);
		properties = new GitContextProperties(
				null,
				Path.of("./data/repos"),
				qdrantUrl,
				null,
				null,
				null,
				null
		);

		RestClient restClient = RestClient.builder().baseUrl(qdrantUrl).build();
		qdrantClient = new QdrantClient(restClient, qdrantUrl, 128);
		LocalBgeEmbeddingService embeddingService = new LocalBgeEmbeddingService(properties);
		ChunkPointMapper mapper = new ChunkPointMapper();
		vectorStore = new VectorStore(qdrantClient, embeddingService, mapper, properties);
	}

	@Test
	@DisplayName("index stores chunks, search retrieves most relevant hit, and deleteStale removes old runs")
	void testIndexSearchAndDeleteStale() {
		Chunk rateLimitChunk = createChunk(
				"doc-1",
				0,
				"src/main/java/GitHubClient.java",
				"handleRateLimits",
				ChunkType.METHOD,
				"The GitHub client checks remaining rate limits and sleeps when the quota is exceeded.",
				10,
				25
		);

		Chunk markdownChunk = createChunk(
				"doc-2",
				0,
				"src/main/java/MarkdownChunker.java",
				"parseHeadings",
				ChunkType.METHOD,
				"Markdown headings are parsed by matching leading hashes into section boundaries.",
				30,
				50
		);

		Chunk cloneChunk = createChunk(
				"doc-3",
				0,
				"src/main/java/RepositoryCloner.java",
				"cloneOrUpdate",
				ChunkType.METHOD,
				"Clones a remote repository or fetches new commits using JGit working tree.",
				1,
				40
		);

		// 1. Index run-1
		IndexSummary summary1 = vectorStore.index(testRepo, "run-1", List.of(rateLimitChunk, markdownChunk, cloneChunk));
		assertThat(summary1.count()).isEqualTo(3);
		assertThat(summary1.embedMillis()).isGreaterThanOrEqualTo(0);
		assertThat(summary1.upsertMillis()).isGreaterThanOrEqualTo(0);

		// 2. Search returns expected chunk first
		List<SearchHit> searchHits = vectorStore.search(
				testRepo,
				"how does the app handle GitHub API rate limits?",
				3,
				Set.of(ChunkType.METHOD),
				"src/main/java"
		);

		assertThat(searchHits).isNotEmpty();
		SearchHit topHit = searchHits.get(0);
		assertThat(topHit.symbolName()).isEqualTo("handleRateLimits");
		assertThat(topHit.path()).isEqualTo("src/main/java/GitHubClient.java");
		assertThat(topHit.chunkType()).isEqualTo(ChunkType.METHOD);
		assertThat(topHit.score()).isGreaterThan(0.5);

		// Verify count in Qdrant
		long totalPoints = qdrantClient.count(properties.qdrant().collection(), null);
		assertThat(totalPoints).isEqualTo(3);

		// 3. Index run-2 with fewer chunks and prune stale run-1 points
		IndexSummary summary2 = vectorStore.index(testRepo, "run-2", List.of(rateLimitChunk, markdownChunk));
		assertThat(summary2.count()).isEqualTo(2);

		vectorStore.deleteStale(testRepo, "run-2");

		// Stale points from run-1 are deleted, only run-2 points remain
		long run1Count = qdrantClient.count(properties.qdrant().collection(),
				Map.of("must", List.of(Map.of("key", "runId", "match", Map.of("value", "run-1")))));
		long run2Count = qdrantClient.count(properties.qdrant().collection(),
				Map.of("must", List.of(Map.of("key", "runId", "match", Map.of("value", "run-2")))));

		assertThat(run1Count).isEqualTo(0);
		assertThat(run2Count).isEqualTo(2);
	}

	private Chunk createChunk(
			String docId,
			int index,
			String path,
			String symbolName,
			ChunkType chunkType,
			String content,
			int startLine,
			int endLine
	) {
		ChunkMetadata metadata = new ChunkMetadata(
				testRepo.normalizedKey(),
				SourceType.CODE,
				path,
				"java",
				"https://github.com/" + testRepo.fullName() + "/blob/main/" + path,
				"commit123",
				"author",
				null,
				chunkType,
				symbolName,
				null,
				null,
				startLine,
				endLine,
				50
		);
		String text = "// " + path + ":" + startLine + "-" + endLine + "\n" + content;
		return new Chunk(docId, index, text, content, metadata);
	}
}
