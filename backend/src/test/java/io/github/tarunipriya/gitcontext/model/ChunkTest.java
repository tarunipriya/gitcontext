package io.github.tarunipriya.gitcontext.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChunkTest {

	private ChunkMetadata createValidMeta() {
		return new ChunkMetadata(
				"owner/repo",
				SourceType.CODE,
				"src/Main.java",
				"java",
				null,
				null,
				null,
				null,
				ChunkType.CLASS,
				"Main",
				null,
				null,
				1,
				20,
				15
		);
	}

	@Test
	@DisplayName("Deterministic ID computation from documentId and chunkIndex")
	void testDeterministicId() {
		Chunk chunk = Chunk.of("doc-123", 0, "// Header\ncode", "code", createValidMeta());

		assertThat(chunk.id()).isEqualTo(Hashing.sha256("doc-123:0"));
		assertThat(chunk.documentId()).isEqualTo("doc-123");
		assertThat(chunk.chunkIndex()).isZero();
		assertThat(chunk.text()).isEqualTo("// Header\ncode");
		assertThat(chunk.content()).isEqualTo("code");
	}

	@Test
	@DisplayName("Compact constructor validates non-null components and non-negative chunkIndex")
	void testConstructorValidations() {
		ChunkMetadata meta = createValidMeta();

		assertThatThrownBy(() -> new Chunk("doc-1", -1, "text", "content", meta))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("chunkIndex must be non-negative");

		assertThatThrownBy(() -> new Chunk(null, "doc-1", 0, null, "content", meta))
				.isInstanceOf(NullPointerException.class)
				.hasMessageContaining("text must not be null");

		assertThatThrownBy(() -> new Chunk(null, "doc-1", 0, "text", null, meta))
				.isInstanceOf(NullPointerException.class)
				.hasMessageContaining("content must not be null");

		assertThatThrownBy(() -> new Chunk(null, "doc-1", 0, "text", "content", null))
				.isInstanceOf(NullPointerException.class)
				.hasMessageContaining("metadata must not be null");

		assertThatThrownBy(() -> new Chunk(null, null, 0, "text", "content", meta))
				.isInstanceOf(NullPointerException.class)
				.hasMessageContaining("documentId must not be null");
	}
}
