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

class LineWindowChunkerTest {

	private final TokenCounter tokenCounter = new ApproximateTokenCounter();
	private final ContextHeaderBuilder headerBuilder = new ContextHeaderBuilder();
	private final GitContextProperties properties = new GitContextProperties(
			null, null, "http://localhost:6333", null, null, null,
			new GitContextProperties.ChunkingProperties(512, 40, 5, 20)
	);
	// Using fallbackWindowLines = 20, overlapLines = 5 for test
	private final LineWindowChunker chunker = new LineWindowChunker(properties, tokenCounter, headerBuilder);

	private Document createDoc(String content) {
		DocumentMetadata meta = new DocumentMetadata(
				"owner/repo",
				SourceType.CODE,
				"src/Main.java",
				null,
				null,
				null,
				null,
				null,
				null,
				"java",
				null,
				null
		);
		return Document.create(content, meta, "src/Main.java");
	}

	@Test
	@DisplayName("Window count and overlap calculation produces expected overlapping chunks")
	void testWindowCountAndOverlap() {
		// 35 lines. Window size 20, overlap 5 -> step 15.
		// Window 1: lines 1..20
		// Window 2: lines 16..35
		StringBuilder sb = new StringBuilder();
		for (int i = 1; i <= 35; i++) {
			sb.append("line ").append(i).append("\n");
		}
		Document doc = createDoc(sb.toString());
		List<Chunk> chunks = chunker.chunk(doc);

		assertThat(chunks).hasSize(2);
		Chunk c1 = chunks.get(0);
		Chunk c2 = chunks.get(1);

		assertThat(c1.metadata().chunkType()).isEqualTo(ChunkType.LINES);
		assertThat(c1.metadata().startLine()).isEqualTo(1);
		assertThat(c1.metadata().endLine()).isEqualTo(20);

		assertThat(c2.metadata().startLine()).isEqualTo(16);
		assertThat(c2.metadata().endLine()).isEqualTo(35);

		// Overlap is lines 16..20 (5 lines)
		assertThat(c1.content()).contains("line 16\nline 17\nline 18\nline 19\nline 20");
		assertThat(c2.content()).startsWith("line 16\nline 17\nline 18\nline 19\nline 20");
	}

	@Test
	@DisplayName("Exact 1-based line numbers are recorded on chunk metadata")
	void testExactLineNumbers() {
		StringBuilder sb = new StringBuilder();
		for (int i = 1; i <= 25; i++) {
			sb.append("line ").append(i).append("\n");
		}
		Document doc = createDoc(sb.toString());
		List<Chunk> chunks = chunker.chunk(doc);

		assertThat(chunks.get(0).metadata().startLine()).isEqualTo(1);
		assertThat(chunks.get(0).metadata().endLine()).isEqualTo(20);

		assertThat(chunks.get(1).metadata().startLine()).isEqualTo(16);
		assertThat(chunks.get(1).metadata().endLine()).isEqualTo(25);
	}

	@Test
	@DisplayName("A file smaller than one window produces exactly 1 chunk")
	void testSmallFileProducesOneChunk() {
		String content = "public class Foo {\n    int a = 1;\n    int b = 2;\n}\n";
		Document doc = createDoc(content);
		List<Chunk> chunks = chunker.chunk(doc);

		assertThat(chunks).hasSize(1);
		assertThat(chunks.get(0).metadata().startLine()).isEqualTo(1);
		assertThat(chunks.get(0).metadata().endLine()).isEqualTo(4);
		assertThat(chunks.get(0).metadata().chunkType()).isEqualTo(ChunkType.LINES);
	}

	@Test
	@DisplayName("Empty and blank documents produce no chunks")
	void testBlankFileProducesZeroChunks() {
		assertThat(chunker.chunk(createDoc(""))).isEmpty();
		assertThat(chunker.chunk(createDoc("   \n   \n\t"))).isEmpty();
	}

	@Test
	@DisplayName("Windows CRLF line endings are handled identically to Unix LF")
	void testWindowsCrlfLineEndings() {
		String crlfContent = "line 1\r\nline 2\r\nline 3\r\n";
		Document doc = createDoc(crlfContent);
		List<Chunk> chunks = chunker.chunk(doc);

		assertThat(chunks).hasSize(1);
		assertThat(chunks.get(0).metadata().startLine()).isEqualTo(1);
		assertThat(chunks.get(0).metadata().endLine()).isEqualTo(3);
		assertThat(chunks.get(0).content()).isEqualTo("line 1\nline 2\nline 3");
	}

	@Test
	@DisplayName("Shrinks window when lines exceed maxChunkTokens")
	void testShrinkWindowWhenTokensExceeded() {
		// Properties: maxChunkTokens = 20, fallbackWindowLines = 20
		GitContextProperties limitProps = new GitContextProperties(
				null, null, "http://localhost:6333", null, null, null,
				new GitContextProperties.ChunkingProperties(20, 5, 2, 20)
		);
		LineWindowChunker tokenChunker = new LineWindowChunker(limitProps, tokenCounter, headerBuilder);

		// 10 lines of 16 characters each (4 tokens per line).
		// A full 20-line window would be 80 tokens, exceeding maxChunkTokens 20.
		StringBuilder sb = new StringBuilder();
		for (int i = 1; i <= 10; i++) {
			sb.append("line ").append(String.format("%02d", i)).append(" 12345678\n");
		}
		Document doc = createDoc(sb.toString());
		List<Chunk> chunks = tokenChunker.chunk(doc);

		assertThat(chunks.size()).isGreaterThan(1);
		for (Chunk chunk : chunks) {
			assertThat(chunk.metadata().tokenCount()).isLessThanOrEqualTo(20);
			assertThat(chunk.metadata().chunkType()).isEqualTo(ChunkType.LINES);
		}
	}

	@Test
	@DisplayName("A single very long line is hard-split into pieces with identical line numbers")
	void testVeryLongLinesHardSplit() {
		// Properties: maxChunkTokens = 25
		GitContextProperties limitProps = new GitContextProperties(
				null, null, "http://localhost:6333", null, null, null,
				new GitContextProperties.ChunkingProperties(25, 5, 2, 20)
		);
		LineWindowChunker tokenChunker = new LineWindowChunker(limitProps, tokenCounter, headerBuilder);

		// Single line with 300 characters (~75 tokens)
		String longLine = "A".repeat(300);
		Document doc = createDoc(longLine);
		List<Chunk> chunks = tokenChunker.chunk(doc);

		assertThat(chunks.size()).isGreaterThanOrEqualTo(3);
		for (Chunk chunk : chunks) {
			assertThat(chunk.metadata().startLine()).isEqualTo(1);
			assertThat(chunk.metadata().endLine()).isEqualTo(1);
			assertThat(chunk.metadata().tokenCount()).isLessThanOrEqualTo(25);
		}
		// Content combined equals original line
		String combined = chunks.stream().map(Chunk::content).reduce("", String::concat);
		assertThat(combined).isEqualTo(longLine);
	}
}
