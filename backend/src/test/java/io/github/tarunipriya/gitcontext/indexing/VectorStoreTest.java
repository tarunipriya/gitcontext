package io.github.tarunipriya.gitcontext.indexing;

import io.github.tarunipriya.gitcontext.config.GitContextProperties;
import io.github.tarunipriya.gitcontext.indexing.qdrant.QdrantClient;
import io.github.tarunipriya.gitcontext.indexing.qdrant.QdrantPoint;
import io.github.tarunipriya.gitcontext.indexing.qdrant.QdrantScoredPoint;
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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VectorStoreTest {

	@Mock
	private QdrantClient qdrantClient;

	@Mock
	private EmbeddingService embeddingService;

	@Mock
	private ChunkPointMapper chunkPointMapper;

	private GitContextProperties properties;
	private VectorStore vectorStore;
	private final RepoRef repo = RepoRef.parse("testowner/testrepo");

	@BeforeEach
	void setUp() {
		properties = new GitContextProperties(
				null,
				Path.of("./data/repos"),
				"http://localhost:6333",
				null,
				null,
				null,
				null
		);
		vectorStore = new VectorStore(qdrantClient, embeddingService, chunkPointMapper, properties);
	}

	@Test
	@DisplayName("index coordinates ensureCollection, embedding, point mapping, and upsert")
	void testIndexCoordinatesFlow() {
		when(embeddingService.dimension()).thenReturn(384);
		when(embeddingService.embedDocuments(List.of("text 1"))).thenReturn(List.of(new float[]{0.1f}));
		QdrantPoint point = new QdrantPoint("uuid-1", new float[]{0.1f}, Map.of("repo", "testowner/testrepo"));
		when(chunkPointMapper.toPoint(any(), any(), any(), any())).thenReturn(point);

		ChunkMetadata meta = new ChunkMetadata("testowner/testrepo", SourceType.CODE, "path.java", "java", null, null, null, null, ChunkType.METHOD, null, null, null, 1, 10, 20);
		Chunk chunk = new Chunk("doc-1", 0, "text 1", "raw 1", meta);

		IndexSummary summary = vectorStore.index(repo, "run-100", List.of(chunk));

		assertThat(summary.count()).isEqualTo(1);
		verify(qdrantClient).ensureCollection("gitcontext_chunks", 384);
		verify(qdrantClient).upsert(eq("gitcontext_chunks"), eq(List.of(point)), eq(128));
	}

	@Test
	@DisplayName("search embeds query, queries client, filters by path prefix, and orders by score")
	void testSearchFlow() {
		float[] queryVector = new float[]{0.2f, 0.4f};
		when(embeddingService.embedQuery("search query")).thenReturn(queryVector);

		QdrantScoredPoint sp1 = new QdrantScoredPoint("p1", 0.95, Map.of());
		QdrantScoredPoint sp2 = new QdrantScoredPoint("p2", 0.75, Map.of());
		when(qdrantClient.search(eq("gitcontext_chunks"), eq(queryVector), any(), anyInt()))
				.thenReturn(List.of(sp1, sp2));

		SearchHit h1 = new SearchHit("p1", 0.95, "testowner/testrepo", "run-1", "c1", "d1", 0, SourceType.CODE, ChunkType.METHOD, "backend/src/Foo.java", "java", "foo", null, null, 1, 10, 20, null, null, "text", "raw");
		SearchHit h2 = new SearchHit("p2", 0.75, "testowner/testrepo", "run-1", "c2", "d1", 1, SourceType.CODE, ChunkType.METHOD, "frontend/src/Bar.js", "javascript", "bar", null, null, 1, 10, 20, null, null, "text", "raw");
		when(chunkPointMapper.toSearchHit(sp1)).thenReturn(h1);
		when(chunkPointMapper.toSearchHit(sp2)).thenReturn(h2);

		List<SearchHit> hits = vectorStore.search(repo, "search query", 5, Set.of(ChunkType.METHOD), "backend/src");

		assertThat(hits).hasSize(1);
		assertThat(hits.get(0).path()).isEqualTo("backend/src/Foo.java");
		assertThat(hits.get(0).score()).isEqualTo(0.95);
	}

	@Test
	@DisplayName("deleteStale targets points with matching repo but differing runId")
	@SuppressWarnings("unchecked")
	void testDeleteStale() {
		vectorStore.deleteStale(repo, "run-new");

		ArgumentCaptor<Map<String, Object>> filterCaptor = ArgumentCaptor.forClass(Map.class);
		verify(qdrantClient).deleteByFilter(eq("gitcontext_chunks"), filterCaptor.capture());

		Map<String, Object> filter = filterCaptor.getValue();
		assertThat(filter).containsKey("must");
		assertThat(filter).containsKey("must_not");

		List<Map<String, Object>> must = (List<Map<String, Object>>) filter.get("must");
		List<Map<String, Object>> mustNot = (List<Map<String, Object>>) filter.get("must_not");

		assertThat(must).contains(Map.of("key", "repo", "match", Map.of("value", "testowner/testrepo")));
		assertThat(mustNot).contains(Map.of("key", "runId", "match", Map.of("value", "run-new")));
	}

	@Test
	@DisplayName("deleteRepo removes all points for the repo")
	@SuppressWarnings("unchecked")
	void testDeleteRepo() {
		vectorStore.deleteRepo(repo);

		ArgumentCaptor<Map<String, Object>> filterCaptor = ArgumentCaptor.forClass(Map.class);
		verify(qdrantClient).deleteByFilter(eq("gitcontext_chunks"), filterCaptor.capture());

		Map<String, Object> filter = filterCaptor.getValue();
		List<Map<String, Object>> must = (List<Map<String, Object>>) filter.get("must");
		assertThat(must).contains(Map.of("key", "repo", "match", Map.of("value", "testowner/testrepo")));
	}
}
