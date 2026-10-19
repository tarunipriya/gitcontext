package io.github.tarunipriya.gitcontext.ingestion;

import io.github.tarunipriya.gitcontext.chunking.ChunkingService;
import io.github.tarunipriya.gitcontext.indexing.IndexSummary;
import io.github.tarunipriya.gitcontext.indexing.VectorStore;
import io.github.tarunipriya.gitcontext.indexing.qdrant.QdrantUnavailableException;
import io.github.tarunipriya.gitcontext.ingestion.RepositoryCloner.ClonedRepo;
import io.github.tarunipriya.gitcontext.ingestion.github.GitHubApiException;
import io.github.tarunipriya.gitcontext.ingestion.github.IssueIngestor;
import io.github.tarunipriya.gitcontext.ingestion.github.PullRequestIngestor;
import io.github.tarunipriya.gitcontext.model.Chunk;
import io.github.tarunipriya.gitcontext.model.ChunkMetadata;
import io.github.tarunipriya.gitcontext.model.ChunkType;
import io.github.tarunipriya.gitcontext.model.Document;
import io.github.tarunipriya.gitcontext.model.DocumentMetadata;
import io.github.tarunipriya.gitcontext.model.RepoRef;
import io.github.tarunipriya.gitcontext.model.SourceType;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IngestionServiceTest {

	@Mock
	private RepositoryCloner repositoryCloner;

	@Mock
	private FileIngestor fileIngestor;

	@Mock
	private CommitIngestor commitIngestor;

	@Mock
	private PullRequestIngestor pullRequestIngestor;

	@Mock
	private IssueIngestor issueIngestor;

	@Mock
	private ChunkingService chunkingService;

	@Mock
	private VectorStore vectorStore;

	@Mock
	private io.github.tarunipriya.gitcontext.retrieval.keyword.KeywordIndex keywordIndex;

	private IngestionService ingestionService;
	private final RepoRef repoRef = new RepoRef("testowner", "testrepo");

	@BeforeEach
	void setUp() {
		ingestionService = new IngestionService(
				repositoryCloner,
				fileIngestor,
				commitIngestor,
				pullRequestIngestor,
				issueIngestor,
				chunkingService,
				vectorStore,
				keywordIndex
		);
	}

	@Test
	@DisplayName("Happy path returns combined documents, correct counts by source type, and chunking results")
	void happyPath_returnsCombinedDocumentsAndCorrectCounts() {
		String headSha = "0123456789abcdef0123456789abcdef01234567";
		ClonedRepo clonedRepo = new ClonedRepo(repoRef, Path.of("mock/path"), headSha, "main");
		when(repositoryCloner.cloneOrUpdate(repoRef)).thenReturn(clonedRepo);

		Document codeDoc1 = createDoc(SourceType.CODE, "src/App.java", "code 1");
		Document codeDoc2 = createDoc(SourceType.CODE, "src/Util.java", "code 2");
		Document commitDoc = createDoc(SourceType.COMMIT, "sha1", "commit message");
		Document prDoc = createDoc(SourceType.PULL_REQUEST, "pr-1", "pr content");
		Document issueDoc = createDoc(SourceType.ISSUE, "issue-1", "issue content");

		when(fileIngestor.ingest(clonedRepo)).thenReturn(List.of(codeDoc1, codeDoc2));
		when(commitIngestor.ingest(clonedRepo)).thenReturn(List.of(commitDoc));
		when(pullRequestIngestor.ingest(repoRef)).thenReturn(List.of(prDoc));
		when(issueIngestor.ingest(repoRef)).thenReturn(List.of(issueDoc));

		Chunk chunk1 = Chunk.of("doc1", 0, "chunk 1 text", "chunk 1 content",
				ChunkMetadata.from(codeDoc1.metadata(), ChunkType.CLASS, 30, "App", null, null, 1, 10));
		Chunk chunk2 = Chunk.of("doc2", 0, "chunk 2 text", "chunk 2 content",
				ChunkMetadata.from(codeDoc2.metadata(), ChunkType.METHOD, 50, "util", "Util", null, 5, 20));
		when(chunkingService.chunkAll(any())).thenReturn(List.of(chunk1, chunk2));
		when(vectorStore.index(eq(repoRef), any(), any())).thenReturn(new IndexSummary(2, 12L, 24L));

		IngestionResult result = ingestionService.ingest(repoRef);

		assertThat(result.repo()).isEqualTo(repoRef);
		assertThat(result.headCommitSha()).isEqualTo(headSha);
		assertThat(result.documents()).containsExactly(codeDoc1, codeDoc2, commitDoc, prDoc, issueDoc);
		assertThat(result.countsBySourceType().get(SourceType.CODE)).isEqualTo(2);
		assertThat(result.countsBySourceType().get(SourceType.COMMIT)).isEqualTo(1);
		assertThat(result.countsBySourceType().get(SourceType.PULL_REQUEST)).isEqualTo(1);
		assertThat(result.countsBySourceType().get(SourceType.ISSUE)).isEqualTo(1);

		// Chunks and metrics
		assertThat(result.chunks()).containsExactly(chunk1, chunk2);
		assertThat(result.totalChunks()).isEqualTo(2);
		assertThat(result.chunkCountsByType().get(ChunkType.CLASS)).isEqualTo(1);
		assertThat(result.chunkCountsByType().get(ChunkType.METHOD)).isEqualTo(1);
		assertThat(result.avgChunkTokens()).isEqualTo(40.0);
		assertThat(result.maxChunkTokens()).isEqualTo(50);

		// Indexing metrics
		assertThat(result.indexedPoints()).isEqualTo(2);
		assertThat(result.embedMillis()).isEqualTo(12L);
		assertThat(result.upsertMillis()).isEqualTo(24L);
		verify(vectorStore).index(eq(repoRef), any(), eq(List.of(chunk1, chunk2)));
		verify(vectorStore).deleteStale(eq(repoRef), any());
		verify(keywordIndex).build(eq(repoRef), eq(List.of(chunk1, chunk2)));

		assertThat(result.warnings()).isEmpty();
		assertThat(result.stageDurationsMillis()).containsKeys("clone", "files", "commits", "pullRequests", "issues", "chunking", "indexing", "keywordIndexing");
		assertThat(result.totalDurationMillis()).isGreaterThanOrEqualTo(0);
	}

	@Test
	@DisplayName("Indexing failure becomes a warning, preserves chunks, and deleteStale is NOT called")
	void indexingFailure_becomesWarningAndPreservesChunksAndSkipsDeleteStale() {
		String headSha = "0123456789abcdef0123456789abcdef01234567";
		ClonedRepo clonedRepo = new ClonedRepo(repoRef, Path.of("mock/path"), headSha, "main");
		when(repositoryCloner.cloneOrUpdate(repoRef)).thenReturn(clonedRepo);

		Document codeDoc = createDoc(SourceType.CODE, "src/App.java", "code 1");
		when(fileIngestor.ingest(clonedRepo)).thenReturn(List.of(codeDoc));
		when(commitIngestor.ingest(clonedRepo)).thenReturn(List.of());
		when(pullRequestIngestor.ingest(repoRef)).thenReturn(List.of());
		when(issueIngestor.ingest(repoRef)).thenReturn(List.of());

		Chunk chunk = Chunk.of("doc1", 0, "chunk 1 text", "chunk 1 content",
				ChunkMetadata.from(codeDoc.metadata(), ChunkType.CLASS, 30, "App", null, null, 1, 10));
		when(chunkingService.chunkAll(any())).thenReturn(List.of(chunk));

		when(vectorStore.index(eq(repoRef), any(), any()))
				.thenThrow(new QdrantUnavailableException("http://localhost:6333", new RuntimeException("Connection refused")));

		IngestionResult result = ingestionService.ingest(repoRef);

		assertThat(result.documents()).containsExactly(codeDoc);
		assertThat(result.chunks()).containsExactly(chunk);
		assertThat(result.indexedPoints()).isZero();
		assertThat(result.embedMillis()).isZero();
		assertThat(result.upsertMillis()).isZero();
		assertThat(result.warnings()).hasSize(1);
		assertThat(result.warnings().getFirst())
				.contains("Indexing skipped:")
				.contains("search will not include this run");

		verify(vectorStore).index(eq(repoRef), any(), eq(List.of(chunk)));
		verify(vectorStore, never()).deleteStale(any(), any());
	}

	@Test
	@DisplayName("Chunking failure becomes a warning and preserves documents with zero chunks")
	void chunkingFailure_becomesWarningAndPreservesDocs() {
		String headSha = "0123456789abcdef0123456789abcdef01234567";
		ClonedRepo clonedRepo = new ClonedRepo(repoRef, Path.of("mock/path"), headSha, "main");
		when(repositoryCloner.cloneOrUpdate(repoRef)).thenReturn(clonedRepo);

		Document fileDoc = createDoc(SourceType.CODE, "src/App.java", "code");
		when(fileIngestor.ingest(clonedRepo)).thenReturn(List.of(fileDoc));
		when(commitIngestor.ingest(clonedRepo)).thenReturn(List.of());
		when(pullRequestIngestor.ingest(repoRef)).thenReturn(List.of());
		when(issueIngestor.ingest(repoRef)).thenReturn(List.of());

		when(chunkingService.chunkAll(any())).thenThrow(new RuntimeException("Tree-sitter unexpected parser panic"));
		when(vectorStore.index(eq(repoRef), any(), any())).thenReturn(new IndexSummary(0, 0L, 0L));

		IngestionResult result = ingestionService.ingest(repoRef);

		assertThat(result.documents()).containsExactly(fileDoc);
		assertThat(result.chunks()).isEmpty();
		assertThat(result.totalChunks()).isZero();
		assertThat(result.avgChunkTokens()).isEqualTo(0.0);
		assertThat(result.maxChunkTokens()).isZero();
		assertThat(result.warnings()).hasSize(1);
		assertThat(result.warnings().getFirst()).contains("Chunking stage failed unexpectedly: Tree-sitter unexpected parser panic");
	}

	@Test
	@DisplayName("GitHubApiException becomes a warning while keeping local documents")
	void gitHubFailure_becomesWarningAndPreservesLocalDocs() {
		String headSha = "abcdef0123456789abcdef0123456789abcdef01";
		ClonedRepo clonedRepo = new ClonedRepo(repoRef, Path.of("mock/path"), headSha, "main");
		when(repositoryCloner.cloneOrUpdate(repoRef)).thenReturn(clonedRepo);

		Document fileDoc = createDoc(SourceType.CODE, "src/Main.java", "main code");
		Document commitDoc = createDoc(SourceType.COMMIT, "sha99", "initial commit");
		Document issueDoc = createDoc(SourceType.ISSUE, "issue-12", "issue doc");

		when(fileIngestor.ingest(clonedRepo)).thenReturn(List.of(fileDoc));
		when(commitIngestor.ingest(clonedRepo)).thenReturn(List.of(commitDoc));
		when(pullRequestIngestor.ingest(repoRef)).thenThrow(new GitHubApiException(403, "GitHub API rate limit exceeded"));
		when(issueIngestor.ingest(repoRef)).thenReturn(List.of(issueDoc));
		when(chunkingService.chunkAll(any())).thenReturn(List.of());
		when(vectorStore.index(eq(repoRef), any(), any())).thenReturn(new IndexSummary(0, 0L, 0L));

		IngestionResult result = ingestionService.ingest(repoRef);

		assertThat(result.documents()).containsExactly(fileDoc, commitDoc, issueDoc);
		assertThat(result.countsBySourceType().get(SourceType.CODE)).isEqualTo(1);
		assertThat(result.countsBySourceType().get(SourceType.COMMIT)).isEqualTo(1);
		assertThat(result.countsBySourceType().get(SourceType.PULL_REQUEST)).isZero();
		assertThat(result.countsBySourceType().get(SourceType.ISSUE)).isEqualTo(1);
		assertThat(result.warnings()).hasSize(1);
		assertThat(result.warnings().getFirst()).contains("Pull requests stage failed: GitHub API rate limit exceeded");
	}

	@Test
	@DisplayName("Clone failure throws and prevents downstream stages from executing")
	void cloneFailure_throwsExceptionAndStopsPipeline() {
		when(repositoryCloner.cloneOrUpdate(repoRef)).thenThrow(new RuntimeException("Git clone failed"));

		assertThatThrownBy(() -> ingestionService.ingest(repoRef))
				.isInstanceOf(RuntimeException.class)
				.hasMessageContaining("Git clone failed");

		verifyNoInteractions(fileIngestor, commitIngestor, pullRequestIngestor, issueIngestor, chunkingService, vectorStore, keywordIndex);
	}

	@Test
	@DisplayName("Keyword indexing failure is caught, added as warning, and does not fail ingestion")
	void keywordIndexingFailure_becomesWarningAndPreservesIngestion() {
		String headSha = "0123456789abcdef0123456789abcdef01234567";
		ClonedRepo clonedRepo = new ClonedRepo(repoRef, Path.of("mock/path"), headSha, "main");
		when(repositoryCloner.cloneOrUpdate(repoRef)).thenReturn(clonedRepo);

		Document codeDoc = createDoc(SourceType.CODE, "src/App.java", "code 1");
		when(fileIngestor.ingest(clonedRepo)).thenReturn(List.of(codeDoc));
		when(commitIngestor.ingest(clonedRepo)).thenReturn(List.of());
		when(pullRequestIngestor.ingest(repoRef)).thenReturn(List.of());
		when(issueIngestor.ingest(repoRef)).thenReturn(List.of());

		Chunk chunk = Chunk.of("doc1", 0, "chunk 1 text", "chunk 1 content",
				ChunkMetadata.from(codeDoc.metadata(), ChunkType.CLASS, 30, "App", null, null, 1, 10));
		when(chunkingService.chunkAll(any())).thenReturn(List.of(chunk));
		when(vectorStore.index(eq(repoRef), any(), any())).thenReturn(new IndexSummary(1, 10L, 20L));

		org.mockito.Mockito.doThrow(new RuntimeException("Lucene index write failed"))
				.when(keywordIndex).build(eq(repoRef), any());

		IngestionResult result = ingestionService.ingest(repoRef);

		assertThat(result.documents()).containsExactly(codeDoc);
		assertThat(result.chunks()).containsExactly(chunk);
		assertThat(result.warnings()).hasSize(1);
		assertThat(result.warnings().getFirst()).contains("Keyword indexing skipped: Lucene index write failed");
		assertThat(result.stageDurationsMillis()).containsKey("keywordIndexing");
	}

	private Document createDoc(SourceType sourceType, String key, String content) {
		DocumentMetadata metadata = new DocumentMetadata(
				repoRef.fullName(),
				sourceType,
				key,
				"https://example.com/" + key,
				"author",
				Instant.now(),
				null,
				null,
				null,
				null,
				"Title " + key,
				Map.of()
		);
		return Document.create(content, metadata, key);
	}
}
