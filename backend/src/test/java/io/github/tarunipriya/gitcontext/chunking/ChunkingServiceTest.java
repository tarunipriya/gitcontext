package io.github.tarunipriya.gitcontext.chunking;

import io.github.tarunipriya.gitcontext.config.GitContextProperties;
import io.github.tarunipriya.gitcontext.model.Chunk;
import io.github.tarunipriya.gitcontext.model.ChunkType;
import io.github.tarunipriya.gitcontext.model.Document;
import io.github.tarunipriya.gitcontext.model.DocumentMetadata;
import io.github.tarunipriya.gitcontext.model.SourceType;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChunkingServiceTest {

	private final TokenCounter tokenCounter = new ApproximateTokenCounter();
	private final ContextHeaderBuilder headerBuilder = new ContextHeaderBuilder();
	private final GitContextProperties properties = new GitContextProperties(
			null, null, "http://localhost:6333", null, null, null,
			new GitContextProperties.ChunkingProperties(512, 40, 5, 60)
	);

	private final MarkdownChunker markdownChunker = new MarkdownChunker(properties, tokenCounter, headerBuilder);
	private final LineWindowChunker lineWindowChunker = new LineWindowChunker(properties, tokenCounter, headerBuilder);
	private final RecordChunker recordChunker = new RecordChunker(properties, tokenCounter, headerBuilder);

	// Even if LineWindowChunker is passed first in the list, ChunkingService ensures it's ordered last as fallback
	private final ChunkingService service = new ChunkingService(List.of(lineWindowChunker, markdownChunker, recordChunker));

	@Test
	@DisplayName("Routes each document to the first matching chunker in priority order")
	void testRoutingToRightChunker() {
		Document mdDoc = Document.create(
				"# Title\nSome markdown documentation content here.",
				new DocumentMetadata("owner/repo", SourceType.DOC, "README.md", null, null, null, null, null, null, "markdown", null, null),
				"README.md"
		);
		Document prDoc = Document.create(
				"PR body text describing bug fix.",
				new DocumentMetadata("owner/repo", SourceType.PULL_REQUEST, null, "https://github.com/pr/1", null, null, null, 1, null, null, "Bug fix", null),
				"1"
		);

		List<Chunk> chunks = service.chunkAll(List.of(mdDoc, prDoc));

		assertThat(chunks).hasSize(2);
		assertThat(chunks.get(0).metadata().chunkType()).isEqualTo(ChunkType.SECTION);
		assertThat(chunks.get(1).metadata().chunkType()).isEqualTo(ChunkType.PULL_REQUEST);
	}

	@Test
	@DisplayName("Fallback LineWindowChunker is used for unknown language code and doc files")
	void testFallbackUsedForUnknownLanguage() {
		Document unknownCodeDoc = Document.create(
				"fn main() {\n    println!(\"Hello\");\n}\n",
				new DocumentMetadata("owner/repo", SourceType.CODE, "src/main.rs", null, null, null, null, null, null, "rust", null, null),
				"src/main.rs"
		);

		List<Chunk> chunks = service.chunkAll(List.of(unknownCodeDoc));

		assertThat(chunks).hasSize(1);
		assertThat(chunks.get(0).metadata().chunkType()).isEqualTo(ChunkType.LINES);
	}

	@Test
	@DisplayName("Chunk IDs are deterministic and stable across repeated runs, and unique within a document")
	void testChunkIdsStableAndUnique() {
		Document multiChunkDoc = Document.create(
				"""
				# Section 1
				First section text with ample length and detailed descriptions to exceed forty tokens and form a standalone chunk for deterministic testing.
				More content here to easily guarantee the token count is well above forty tokens threshold.
				# Section 2
				Second section text with ample length and detailed descriptions to exceed forty tokens and form a standalone chunk for deterministic testing.
				More content here to easily guarantee the token count is well above forty tokens threshold.
				# Section 3
				Third section text with ample length and detailed descriptions to exceed forty tokens and form a standalone chunk for deterministic testing.
				More content here to easily guarantee the token count is well above forty tokens threshold.
				""",
				new DocumentMetadata("owner/repo", SourceType.DOC, "docs/arch.md", null, null, null, null, null, null, "markdown", null, null),
				"docs/arch.md"
		);

		List<Chunk> run1 = service.chunkAll(List.of(multiChunkDoc));
		List<Chunk> run2 = service.chunkAll(List.of(multiChunkDoc));

		assertThat(run1).hasSize(3);
		assertThat(run2).hasSize(3);

		// Unique within document
		Set<String> ids = new HashSet<>();
		for (Chunk chunk : run1) {
			assertThat(ids.add(chunk.id())).isTrue();
		}

		// Sequential chunk indexes
		assertThat(run1.get(0).chunkIndex()).isZero();
		assertThat(run1.get(1).chunkIndex()).isEqualTo(1);
		assertThat(run1.get(2).chunkIndex()).isEqualTo(2);

		// Stable across runs
		for (int i = 0; i < run1.size(); i++) {
			assertThat(run1.get(i).id()).isEqualTo(run2.get(i).id());
		}
	}

	@Test
	@DisplayName("When selected chunker throws, ChunkingService logs WARN and falls back to LineWindowChunker")
	void testChunkerThrowsFallsBackToLineWindowChunker() {
		Chunker throwingChunker = new Chunker() {
			@Override
			public boolean supports(Document document) {
				return "bad.md".equals(document.metadata().path());
			}

			@Override
			public List<Chunk> chunk(Document document) {
				throw new IllegalStateException("Simulated chunker failure");
			}
		};

		ChunkingService robustService = new ChunkingService(List.of(throwingChunker, lineWindowChunker));

		Document badDoc = Document.create(
				"line 1\nline 2\nline 3\n",
				new DocumentMetadata("owner/repo", SourceType.DOC, "bad.md", null, null, null, null, null, null, "markdown", null, null),
				"bad.md"
		);
		Document goodDoc = Document.create(
				"good code line\n",
				new DocumentMetadata("owner/repo", SourceType.CODE, "good.java", null, null, null, null, null, null, "java", null, null),
				"good.java"
		);

		List<Chunk> chunks = robustService.chunkAll(List.of(badDoc, goodDoc));

		assertThat(chunks).hasSize(2);
		// badDoc was successfully chunked by LineWindowChunker fallback
		assertThat(chunks.get(0).metadata().path()).isEqualTo("bad.md");
		assertThat(chunks.get(0).metadata().chunkType()).isEqualTo(ChunkType.LINES);
		assertThat(chunks.get(1).metadata().path()).isEqualTo("good.java");
		assertThat(chunks.get(1).metadata().chunkType()).isEqualTo(ChunkType.LINES);
	}
}
