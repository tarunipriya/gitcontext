package io.github.tarunipriya.gitcontext.retrieval.keyword;

import io.github.tarunipriya.gitcontext.config.GitContextProperties;
import io.github.tarunipriya.gitcontext.indexing.SearchHit;
import io.github.tarunipriya.gitcontext.model.Chunk;
import io.github.tarunipriya.gitcontext.model.ChunkMetadata;
import io.github.tarunipriya.gitcontext.model.ChunkType;
import io.github.tarunipriya.gitcontext.model.DocumentMetadata;
import io.github.tarunipriya.gitcontext.model.RepoRef;
import io.github.tarunipriya.gitcontext.model.SourceType;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

class KeywordIndexTest {

	@TempDir
	Path tempDir;

	private KeywordIndex keywordIndex;
	private CodeAnalyzer analyzer;
	private final RepoRef repo = new RepoRef("testowner", "testrepo");

	@BeforeEach
	void setUp() {
		analyzer = new CodeAnalyzer();
		GitContextProperties.KeywordProperties keywordProps = new GitContextProperties.KeywordProperties(
				tempDir.resolve("lucene"),
				3.0,
				1.5,
				1.0
		);
		GitContextProperties props = new GitContextProperties(
				null,
				tempDir.resolve("repos"),
				"http://localhost:6333",
				null,
				null,
				null,
				null,
				null,
				null,
				keywordProps,
				null
		);
		keywordIndex = new KeywordIndex(props, analyzer);
	}

	@AfterEach
	void tearDown() {
		if (keywordIndex != null) {
			keywordIndex.close();
		}
		if (analyzer != null) {
			analyzer.close();
		}
	}

	private Chunk createChunk(
			String docId,
			int index,
			String text,
			String content,
			ChunkType type,
			String path,
			String symbolName,
			String parentSymbol
	) {
		ChunkMetadata chunkMeta = new ChunkMetadata(
				repo.normalizedKey(),
				SourceType.CODE,
				path,
				"java",
				"https://github.com/testowner/testrepo/blob/main/" + path,
				"sha123",
				"author",
				Instant.now(),
				type,
				symbolName,
				parentSymbol,
				null,
				1,
				20,
				50
		);
		return new Chunk(docId, index, text, content, chunkMeta);
	}

	@Test
	@DisplayName("Query 'rate limit' ranks a chunk with symbolName checkRateLimit first due to symbol boost")
	void search_rateLimit_ranksCheckRateLimitFirst() {
		// Chunk 1: Mentions rate and limit in text, but has a generic symbolName
		Chunk genericChunk = createChunk(
				"doc1",
				0,
				"// Method doing some general work\npublic void doWork() {\n  // check the rate limit here\n  int limit = 100;\n}",
				"public void doWork() { ... }",
				ChunkType.METHOD,
				"src/main/java/Worker.java",
				"doWork",
				"Worker"
		);

		// Chunk 2: Has symbolName checkRateLimit boosted at 3.0
		Chunk symbolChunk = createChunk(
				"doc2",
				0,
				"// Rate limiting handler\npublic void checkRateLimit() {\n  // checks limit\n}",
				"public void checkRateLimit() { ... }",
				ChunkType.METHOD,
				"src/main/java/RateLimiter.java",
				"checkRateLimit",
				"RateLimiter"
		);

		keywordIndex.build(repo, List.of(genericChunk, symbolChunk));

		List<SearchHit> hits = keywordIndex.search(repo, "rate limit", 5, null, null);

		assertThat(hits).hasSize(2);
		assertThat(hits.get(0).symbolName()).isEqualTo("checkRateLimit");
		assertThat(hits.get(0).score()).isGreaterThan(hits.get(1).score());
	}

	@Test
	@DisplayName("Exact log message finds its matching chunk")
	void search_exactLogMessage_findsChunk() {
		Chunk chunk = createChunk(
				"doc3",
				0,
				"log.error(\"GitHub API rate limit exhausted, backing off...\");",
				"log.error(\"GitHub API rate limit exhausted, backing off...\");",
				ChunkType.METHOD,
				"src/main/java/GitHubClient.java",
				"getPaged",
				"GitHubClient"
		);

		keywordIndex.build(repo, List.of(chunk));

		List<SearchHit> hits = keywordIndex.search(repo, "GitHub API rate limit exhausted", 5, null, null);

		assertThat(hits).isNotEmpty();
		assertThat(hits.get(0).chunkId()).isEqualTo(chunk.id());
		assertThat(hits.get(0).path()).isEqualTo("src/main/java/GitHubClient.java");
	}

	@Test
	@DisplayName("Query full of special characters does not throw parse exception")
	void search_specialCharacters_doesNotThrow() {
		Chunk chunk = createChunk(
				"doc4",
				0,
				"int x = foo(bar); [x] \"y\" && z:",
				"int x = foo(bar); [x] \"y\" && z:",
				ChunkType.METHOD,
				"src/main/java/Special.java",
				"specialMethod",
				"Special"
		);

		keywordIndex.build(repo, List.of(chunk));

		assertThatNoException().isThrownBy(() -> {
			List<SearchHit> hits = keywordIndex.search(repo, "foo(bar) [x] \"y\" && z:", 5, null, null);
			assertThat(hits).isNotNull();
		});
	}

	@Test
	@DisplayName("Filtering by chunk type and path prefix restricts search results")
	void search_withTypeAndPathFilters() {
		Chunk methodChunk = createChunk(
				"doc5",
				0,
				"public void parseSections() {}",
				"public void parseSections() {}",
				ChunkType.METHOD,
				"src/main/java/Parser.java",
				"parseSections",
				"Parser"
		);

		Chunk classChunk = createChunk(
				"doc6",
				0,
				"public class Parser {}",
				"public class Parser {}",
				ChunkType.CLASS,
				"src/main/java/Parser.java",
				"Parser",
				null
		);

		Chunk testChunk = createChunk(
				"doc7",
				0,
				"void testParser() {}",
				"void testParser() {}",
				ChunkType.METHOD,
				"src/test/java/ParserTest.java",
				"testParser",
				"ParserTest"
		);

		keywordIndex.build(repo, List.of(methodChunk, classChunk, testChunk));

		// Filter for METHOD in src/main
		List<SearchHit> hits = keywordIndex.search(
				repo,
				"parser",
				5,
				Set.of(ChunkType.METHOD),
				"src/main"
		);

		assertThat(hits).hasSize(1);
		assertThat(hits.get(0).symbolName()).isEqualTo("parseSections");
		assertThat(hits.get(0).path()).isEqualTo("src/main/java/Parser.java");
	}

	@Test
	@DisplayName("Atomic rebuild completely replaces old index content")
	void build_atomicRebuild_replacesOldContent() {
		Chunk oldChunk = createChunk(
				"oldDoc",
				0,
				"old deprecated content that should disappear",
				"old",
				ChunkType.METHOD,
				"src/Old.java",
				"oldMethod",
				null
		);

		keywordIndex.build(repo, List.of(oldChunk));
		assertThat(keywordIndex.search(repo, "deprecated", 5, null, null)).hasSize(1);

		Chunk newChunk = createChunk(
				"newDoc",
				0,
				"brand new modern implementation",
				"new",
				ChunkType.METHOD,
				"src/New.java",
				"newMethod",
				null
		);

		keywordIndex.build(repo, List.of(newChunk));

		assertThat(keywordIndex.search(repo, "deprecated", 5, null, null)).isEmpty();
		assertThat(keywordIndex.search(repo, "modern", 5, null, null)).hasSize(1);
	}

	@Test
	@DisplayName("deleteRepo removes index directory and exists returns false")
	void deleteRepo_removesIndexAndExistsReturnsFalse() {
		Chunk chunk = createChunk(
				"doc8",
				0,
				"some content",
				"content",
				ChunkType.METHOD,
				"src/Test.java",
				"test",
				null
		);

		keywordIndex.build(repo, List.of(chunk));
		assertThat(keywordIndex.exists(repo)).isTrue();

		keywordIndex.deleteRepo(repo);
		assertThat(keywordIndex.exists(repo)).isFalse();
		assertThat(keywordIndex.search(repo, "content", 5, null, null)).isEmpty();
	}

	@Test
	@DisplayName("listIndexedRepos returns all valid repository indexes sorted by full name")
	void listIndexedRepos_returnsSortedRepositories() {
		RepoRef repoA = new RepoRef("alice", "alpha");
		RepoRef repoB = new RepoRef("bob", "beta");

		Chunk chunkA = createChunk("docA", 0, "alpha code", "alpha", ChunkType.METHOD, "Alpha.java", "testA", null);
		Chunk chunkB = createChunk("docB", 0, "beta code", "beta", ChunkType.METHOD, "Beta.java", "testB", null);

		keywordIndex.build(repoB, List.of(chunkB));
		keywordIndex.build(repoA, List.of(chunkA));

		List<RepoRef> indexed = keywordIndex.listIndexedRepos();
		assertThat(indexed).hasSize(2);
		assertThat(indexed.get(0).fullName()).isEqualTo("alice/alpha");
		assertThat(indexed.get(1).fullName()).isEqualTo("bob/beta");

		assertThat(keywordIndex.getDocumentCount(repoA)).isEqualTo(1);
		assertThat(keywordIndex.getLastModified(repoA)).isNotNull();
	}

	@Test
	@DisplayName("listIndexedRepos returns empty list when index dir does not exist")
	void listIndexedRepos_emptyBaseDir_returnsEmpty() {
		GitContextProperties.KeywordProperties nonExistentProps = new GitContextProperties.KeywordProperties(
				tempDir.resolve("does-not-exist"), 3.0, 1.5, 1.0
		);
		GitContextProperties props = new GitContextProperties(
				null, tempDir.resolve("repos"), "http://localhost:6333",
				null, null, null, null, null, null, nonExistentProps, null
		);
		KeywordIndex emptyIndex = new KeywordIndex(props, analyzer);
		assertThat(emptyIndex.listIndexedRepos()).isEmpty();
		assertThat(emptyIndex.getDocumentCount(repo)).isEqualTo(0);
		assertThat(emptyIndex.getLastModified(repo)).isNull();
	}
}
