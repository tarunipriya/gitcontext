package io.github.tarunipriya.gitcontext.chunking;

import io.github.tarunipriya.gitcontext.config.GitContextProperties;
import io.github.tarunipriya.gitcontext.model.Chunk;
import io.github.tarunipriya.gitcontext.model.ChunkType;
import io.github.tarunipriya.gitcontext.model.Document;
import io.github.tarunipriya.gitcontext.model.DocumentMetadata;
import io.github.tarunipriya.gitcontext.model.SourceType;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RecordChunkerTest {

	private final TokenCounter tokenCounter = new ApproximateTokenCounter();
	private final ContextHeaderBuilder headerBuilder = new ContextHeaderBuilder();

	private Document createPrDoc(String content, int prNumber, String title) {
		DocumentMetadata meta = new DocumentMetadata(
				"owner/repo",
				SourceType.PULL_REQUEST,
				null,
				"https://github.com/owner/repo/pull/" + prNumber,
				"author1",
				null,
				null,
				prNumber,
				null,
				null,
				title,
				null
		);
		return Document.create(content, meta, String.valueOf(prNumber));
	}

	@Test
	@DisplayName("Short PR produces exactly 1 chunk with PR header and no line numbers")
	void testShortPrProducesOneChunk() {
		GitContextProperties props = new GitContextProperties(
				null, null, "http://localhost:6333", null, null, null,
				new GitContextProperties.ChunkingProperties(512, 40, 5, 60)
		);
		RecordChunker chunker = new RecordChunker(props, tokenCounter, headerBuilder);

		String content = "This pull request fixes a critical race condition in the cache layer.";
		Document doc = createPrDoc(content, 42, "Fix cache race condition");

		List<Chunk> chunks = chunker.chunk(doc);

		assertThat(chunks).hasSize(1);
		Chunk chunk = chunks.get(0);
		assertThat(chunk.metadata().chunkType()).isEqualTo(ChunkType.PULL_REQUEST);
		assertThat(chunk.metadata().startLine()).isNull();
		assertThat(chunk.metadata().endLine()).isNull();
		assertThat(chunk.text()).startsWith("PR #42: Fix cache race condition");
		assertThat(chunk.content()).isEqualTo(content);
	}

	@Test
	@DisplayName("Long PR split on paragraphs with header repeated across each chunk")
	void testLongPrSplitOnParagraphs() {
		// Low maxChunkTokens = 30 to force splitting across paragraphs
		GitContextProperties props = new GitContextProperties(
				null, null, "http://localhost:6333", null, null, null,
				new GitContextProperties.ChunkingProperties(30, 10, 2, 20)
		);
		RecordChunker chunker = new RecordChunker(props, tokenCounter, headerBuilder);

		String content = """
				First paragraph detailing motivation for authentication restructuring and OAuth provider additions.

				Second paragraph outlining breaking changes in user endpoints and migration steps for v2.

				Third paragraph with verification test plans and deployment checklist for staging environments.
				""";
		Document doc = createPrDoc(content, 99, "Restructure Auth");

		List<Chunk> chunks = chunker.chunk(doc);

		assertThat(chunks.size()).isGreaterThanOrEqualTo(2);
		for (Chunk chunk : chunks) {
			assertThat(chunk.metadata().chunkType()).isEqualTo(ChunkType.PULL_REQUEST);
			assertThat(chunk.text()).startsWith("PR #99: Restructure Auth");
			assertThat(chunk.content()).doesNotContain("PR #99: Restructure Auth");
			assertThat(chunk.metadata().startLine()).isNull();
			assertThat(chunk.metadata().endLine()).isNull();
		}
	}
}
