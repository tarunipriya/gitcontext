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

class MarkdownChunkerTest {

	private final TokenCounter tokenCounter = new ApproximateTokenCounter();
	private final ContextHeaderBuilder headerBuilder = new ContextHeaderBuilder();
	private final GitContextProperties properties = new GitContextProperties(
			null, null, "http://localhost:6333", null, null, null,
			new GitContextProperties.ChunkingProperties(512, 40, 5, 60)
	);
	private final MarkdownChunker chunker = new MarkdownChunker(properties, tokenCounter, headerBuilder);

	private Document createDoc(String content) {
		DocumentMetadata meta = new DocumentMetadata(
				"owner/repo",
				SourceType.DOC,
				"docs/guide.md",
				null,
				null,
				null,
				null,
				null,
				null,
				"markdown",
				"Guide",
				null
		);
		return Document.create(content, meta, "docs/guide.md");
	}

	@Test
	@DisplayName("Heading hierarchy builds paths like 'Getting Started > Windows'")
	void testHeadingHierarchyPaths() {
		String markdown = """
				# Getting Started
				Intro text explaining setup requirements in thorough details for readers. We must ensure there is ample documentation here so that this section is not merged into subsequent sections due to minimum token size thresholds.
				## Windows
				Windows installation details and specific instructions for powershell terminal. We must ensure there is ample documentation here so that this section is not merged into subsequent sections due to minimum token size thresholds.
				## Linux
				Linux installation details and package manager instructions for apt and pacman. We must ensure there is ample documentation here so that this section is not merged into subsequent sections due to minimum token size thresholds.
				""";
		Document doc = createDoc(markdown);
		List<Chunk> chunks = chunker.chunk(doc);

		assertThat(chunks).hasSize(3);
		assertThat(chunks.get(0).metadata().headingPath()).isEqualTo("Getting Started");
		assertThat(chunks.get(1).metadata().headingPath()).isEqualTo("Getting Started > Windows");
		assertThat(chunks.get(2).metadata().headingPath()).isEqualTo("Getting Started > Linux");
	}

	@Test
	@DisplayName("'#' inside fenced code blocks is ignored and not treated as ATX heading")
	void testCodeFencesIgnored() {
		String markdown = """
				# Code Section
				Here is a code example with comments inside:
				```python
				# this is a python comment, not a heading
				# another python comment
				def hello():
				    pass
				```
				More text following the code block.
				""";
		Document doc = createDoc(markdown);
		List<Chunk> chunks = chunker.chunk(doc);

		assertThat(chunks).hasSize(1);
		assertThat(chunks.get(0).metadata().headingPath()).isEqualTo("Code Section");
		assertThat(chunks.get(0).content()).contains("# this is a python comment, not a heading");
	}

	@Test
	@DisplayName("Content before the first heading becomes a section with headingPath equal to fileName")
	void testPreambleBeforeFirstHeading() {
		String markdown = """
				Preamble documentation overview line one with sufficient length to be substantial and exceed forty tokens threshold comfortably.
				Preamble documentation overview line two with more details on the project to form an independent preamble chunk.
				# First Heading
				Content under first heading with sufficient length for testing sections and comfortably exceeding forty tokens threshold as well.
				More content under first heading to ensure this section remains completely standalone.
				""";
		Document doc = createDoc(markdown);
		List<Chunk> chunks = chunker.chunk(doc);

		assertThat(chunks).hasSize(2);
		Chunk preamble = chunks.get(0);
		assertThat(preamble.metadata().headingPath()).isEqualTo("guide.md");
		assertThat(preamble.metadata().startLine()).isEqualTo(1);
		assertThat(preamble.metadata().endLine()).isEqualTo(2);

		Chunk firstHeading = chunks.get(1);
		assertThat(firstHeading.metadata().headingPath()).isEqualTo("First Heading");
		assertThat(firstHeading.metadata().startLine()).isEqualTo(3);
		assertThat(firstHeading.metadata().endLine()).isEqualTo(5);
	}

	@Test
	@DisplayName("Correct 1-based startLine and endLine numbers are preserved")
	void testCorrectLineNumbers() {
		String markdown = """
				# Title
				Line 2 text with sufficient content to comfortably exceed the minimum chunk token threshold of forty tokens without getting merged.
				Line 3 text with additional content to ensure that this section remains standalone.
				# Second Title
				Line 5 text with sufficient content to comfortably exceed the minimum chunk token threshold of forty tokens without getting merged.
				Line 6 text with additional content to ensure that this section remains standalone.
				""";
		Document doc = createDoc(markdown);
		List<Chunk> chunks = chunker.chunk(doc);

		assertThat(chunks).hasSize(2);
		assertThat(chunks.get(0).metadata().startLine()).isEqualTo(1);
		assertThat(chunks.get(0).metadata().endLine()).isEqualTo(3);
		assertThat(chunks.get(1).metadata().startLine()).isEqualTo(4);
		assertThat(chunks.get(1).metadata().endLine()).isEqualTo(6);
	}

	@Test
	@DisplayName("Sections larger than maxChunkTokens are split into overlapping line windows keeping headingPath")
	void testLargeSectionSplitWithOverlap() {
		// Create a chunker with low maxChunkTokens = 30 and overlapLines = 2
		GitContextProperties smallProps = new GitContextProperties(
				null, null, "http://localhost:6333", null, null, null,
				new GitContextProperties.ChunkingProperties(30, 10, 2, 20)
		);
		MarkdownChunker smallChunker = new MarkdownChunker(smallProps, tokenCounter, headerBuilder);

		String markdown = """
				# Large Section
				Line 1: The quick brown fox jumps over the lazy dog repeatedly.
				Line 2: The quick brown fox jumps over the lazy dog repeatedly.
				Line 3: The quick brown fox jumps over the lazy dog repeatedly.
				Line 4: The quick brown fox jumps over the lazy dog repeatedly.
				Line 5: The quick brown fox jumps over the lazy dog repeatedly.
				Line 6: The quick brown fox jumps over the lazy dog repeatedly.
				""";
		Document doc = createDoc(markdown);
		List<Chunk> chunks = smallChunker.chunk(doc);

		assertThat(chunks.size()).isGreaterThan(1);
		for (Chunk chunk : chunks) {
			assertThat(chunk.metadata().headingPath()).isEqualTo("Large Section");
			assertThat(chunk.metadata().chunkType()).isEqualTo(ChunkType.SECTION);
		}

		// Verify overlap between first and second chunk
		Chunk first = chunks.get(0);
		Chunk second = chunks.get(1);
		assertThat(second.metadata().startLine()).isLessThanOrEqualTo(first.metadata().endLine());
	}

	@Test
	@DisplayName("Sections smaller than minChunkTokens are merged into adjacent sections combining line ranges")
	void testSmallSectionsMerged() {
		// Min tokens = 40. Short section has < 40 tokens.
		String markdown = """
				# Section One
				Tiny.
				# Section Two
				This section contains sufficient length and words to exceed the minimum chunk token requirement by a healthy margin.
				""";
		Document doc = createDoc(markdown);
		List<Chunk> chunks = chunker.chunk(doc);

		// "Section One" has very few tokens ("Tiny."), so it merges into Section Two
		assertThat(chunks).hasSize(1);
		Chunk merged = chunks.get(0);
		assertThat(merged.metadata().startLine()).isEqualTo(1);
		assertThat(merged.metadata().endLine()).isEqualTo(4);
		assertThat(merged.content()).contains("Tiny.");
		assertThat(merged.content()).contains("This section contains sufficient length");
	}
}
