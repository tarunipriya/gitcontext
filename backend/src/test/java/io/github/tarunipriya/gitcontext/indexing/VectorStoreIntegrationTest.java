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
	static final QdrantContainer qdrant = new QdrantContainer("qdrant/qdrant:v1.19.2");

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
		assertThat(summary1.embeddedChunks()).isEqualTo(3);
		assertThat(summary1.reusedChunks()).isEqualTo(0);
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

		// Type filter that excludes METHOD should return empty
		List<SearchHit> classHits = vectorStore.search(
				testRepo,
				"how does the app handle GitHub API rate limits?",
				3,
				Set.of(ChunkType.CLASS),
				"src/main/java"
		);
		assertThat(classHits).isEmpty();

		// Path prefix that doesn't match should return empty
		List<SearchHit> nonMatchingPathHits = vectorStore.search(
				testRepo,
				"how does the app handle GitHub API rate limits?",
				3,
				Set.of(ChunkType.METHOD),
				"src/test/java"
		);
		assertThat(nonMatchingPathHits).isEmpty();

		// Test searchWithTimings
		VectorSearchResult timedResult = vectorStore.searchWithTimings(
				testRepo,
				"how does the app handle GitHub API rate limits?",
				3,
				Set.of(ChunkType.METHOD),
				"src/main/java"
		);
		assertThat(timedResult.hits()).isNotEmpty();
		assertThat(timedResult.embedMillis()).isGreaterThanOrEqualTo(0);
		assertThat(timedResult.searchMillis()).isGreaterThanOrEqualTo(0);
		assertThat(timedResult.tookMillis()).isGreaterThanOrEqualTo(0);

		// Verify count in Qdrant via countRepoPoints
		long repoPoints = vectorStore.countRepoPoints(testRepo);
		assertThat(repoPoints).isEqualTo(3);

		long totalPoints = qdrantClient.count(properties.qdrant().collection(), null);
		assertThat(totalPoints).isEqualTo(3);

		// 3. Index run-2 with fewer chunks and prune stale run-1 points
		IndexSummary summary2 = vectorStore.index(testRepo, "run-2", List.of(rateLimitChunk, markdownChunk));
		assertThat(summary2.count()).isEqualTo(2);
		assertThat(summary2.embeddedChunks()).isEqualTo(0);
		assertThat(summary2.reusedChunks()).isEqualTo(2);

		vectorStore.deleteStale(testRepo, "run-2");

		// Stale points from run-1 are deleted, only run-2 points remain
		long run1Count = qdrantClient.count(properties.qdrant().collection(),
				Map.of("must", List.of(Map.of("key", "runId", "match", Map.of("value", "run-1")))));
		long run2Count = qdrantClient.count(properties.qdrant().collection(),
				Map.of("must", List.of(Map.of("key", "runId", "match", Map.of("value", "run-2")))));

		assertThat(run1Count).isEqualTo(0);
		assertThat(run2Count).isEqualTo(2);
		assertThat(vectorStore.countRepoPoints(testRepo)).isEqualTo(2);
	}

	@Test
	@DisplayName("incremental re-indexing reuses unchanged embeddings, re-embeds changed chunk, and removes deleted points")
	void testIncrementalReindexingWorkflow() {
		RepoRef repo = RepoRef.parse("testowner/incremental-test");

		Chunk chunk1 = createChunkForRepo(
				repo,
				"doc-1",
				0,
				"src/main/java/ServiceA.java",
				"doWork",
				ChunkType.METHOD,
				"Service A processes background ingestion events reliably.",
				1,
				20
		);

		Chunk chunk2 = createChunkForRepo(
				repo,
				"doc-2",
				0,
				"src/main/java/ServiceB.java",
				"sendNotification",
				ChunkType.METHOD,
				"Service B sends alert notifications to administrators.",
				10,
				30
		);

		Chunk chunk3 = createChunkForRepo(
				repo,
				"doc-3",
				0,
				"src/main/java/ServiceC.java",
				"cleanup",
				ChunkType.METHOD,
				"Service C purges temporary cache files periodically.",
				5,
				15
		);

		// 1. Initial indexing with run-1
		IndexSummary initialSummary = vectorStore.index(repo, "run-1", List.of(chunk1, chunk2, chunk3));
		assertThat(initialSummary.count()).isEqualTo(3);
		assertThat(initialSummary.embeddedChunks()).isEqualTo(3);
		assertThat(initialSummary.reusedChunks()).isEqualTo(0);

		List<SearchHit> initialHits = vectorStore.search(repo, "background ingestion events", 3, null, null);
		assertThat(initialHits).isNotEmpty();
		assertThat(initialHits.get(0).symbolName()).isEqualTo("doWork");

		// 2. Re-index the exact same chunks with run-2
		IndexSummary reindexSummary = vectorStore.index(repo, "run-2", List.of(chunk1, chunk2, chunk3));
		assertThat(reindexSummary.count()).isEqualTo(3);
		assertThat(reindexSummary.embeddedChunks()).isEqualTo(0);
		assertThat(reindexSummary.reusedChunks()).isEqualTo(3);

		// Verify search results are completely identical
		List<SearchHit> reindexHits = vectorStore.search(repo, "background ingestion events", 3, null, null);
		assertThat(reindexHits).hasSize(initialHits.size());
		for (int i = 0; i < initialHits.size(); i++) {
			assertThat(reindexHits.get(i).id()).isEqualTo(initialHits.get(i).id());
			assertThat(reindexHits.get(i).symbolName()).isEqualTo(initialHits.get(i).symbolName());
			assertThat(reindexHits.get(i).path()).isEqualTo(initialHits.get(i).path());
			assertThat(reindexHits.get(i).score()).isEqualTo(initialHits.get(i).score());
		}

		// 3. Change one chunk's text -> embeddedChunks == 1
		Chunk modifiedChunk1 = createChunkForRepo(
				repo,
				"doc-1",
				0,
				"src/main/java/ServiceA.java",
				"doWork",
				ChunkType.METHOD,
				"Service A processes high-throughput background ingestion events with backpressure.",
				1,
				25
		);

		IndexSummary changedSummary = vectorStore.index(repo, "run-3", List.of(modifiedChunk1, chunk2, chunk3));
		assertThat(changedSummary.count()).isEqualTo(3);
		assertThat(changedSummary.embeddedChunks()).isEqualTo(1);
		assertThat(changedSummary.reusedChunks()).isEqualTo(2);

		// 4. Remove a chunk (chunk3) and run deleteStale -> its point is gone
		IndexSummary removedSummary = vectorStore.index(repo, "run-4", List.of(modifiedChunk1, chunk2));
		assertThat(removedSummary.count()).isEqualTo(2);
		assertThat(removedSummary.embeddedChunks()).isEqualTo(0);
		assertThat(removedSummary.reusedChunks()).isEqualTo(2);

		vectorStore.deleteStale(repo, "run-4");

		assertThat(vectorStore.countRepoPoints(repo)).isEqualTo(2);

		long removedPointCount = qdrantClient.count(properties.qdrant().collection(),
				Map.of("must", List.of(
						Map.of("key", "repo", "match", Map.of("value", repo.normalizedKey())),
						Map.of("key", "chunkId", "match", Map.of("value", chunk3.id()))
				)));
		assertThat(removedPointCount).isEqualTo(0);
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
		return createChunkForRepo(testRepo, docId, index, path, symbolName, chunkType, content, startLine, endLine);
	}

	private Chunk createChunkForRepo(
			RepoRef repo,
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
				repo.normalizedKey(),
				SourceType.CODE,
				path,
				"java",
				"https://github.com/" + repo.fullName() + "/blob/main/" + path,
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
