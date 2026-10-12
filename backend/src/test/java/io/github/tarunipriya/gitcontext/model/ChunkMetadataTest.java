package io.github.tarunipriya.gitcontext.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChunkMetadataTest {

	@Test
	@DisplayName("Validates required fields in compact constructor")
	void testRequiredFieldsValidation() {
		assertThatThrownBy(() -> new ChunkMetadata(
				null, SourceType.CODE, "path", "java", null, null, null, null,
				ChunkType.CLASS, null, null, null, 1, 10, 5
		)).isInstanceOf(NullPointerException.class).hasMessageContaining("repo must not be null");

		assertThatThrownBy(() -> new ChunkMetadata(
				"   ", SourceType.CODE, "path", "java", null, null, null, null,
				ChunkType.CLASS, null, null, null, 1, 10, 5
		)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("repo must not be blank");

		assertThatThrownBy(() -> new ChunkMetadata(
				"owner/repo", null, "path", "java", null, null, null, null,
				ChunkType.CLASS, null, null, null, 1, 10, 5
		)).isInstanceOf(NullPointerException.class).hasMessageContaining("sourceType must not be null");

		assertThatThrownBy(() -> new ChunkMetadata(
				"owner/repo", SourceType.CODE, "path", "java", null, null, null, null,
				null, null, null, null, 1, 10, 5
		)).isInstanceOf(NullPointerException.class).hasMessageContaining("chunkType must not be null");

		assertThatThrownBy(() -> new ChunkMetadata(
				"owner/repo", SourceType.CODE, "path", "java", null, null, null, null,
				ChunkType.CLASS, null, null, null, 1, 10, -1
		)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("tokenCount must be non-negative");
	}

	@Test
	@DisplayName("Validates line numbers: 1-based and endLine >= startLine")
	void testLineNumberValidation() {
		assertThatThrownBy(() -> new ChunkMetadata(
				"owner/repo", SourceType.CODE, "path", "java", null, null, null, null,
				ChunkType.CLASS, null, null, null, 0, 10, 5
		)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("startLine must be >= 1");

		assertThatThrownBy(() -> new ChunkMetadata(
				"owner/repo", SourceType.CODE, "path", "java", null, null, null, null,
				ChunkType.CLASS, null, null, null, 10, 5, 5
		)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("endLine cannot be less than startLine");
	}

	@Test
	@DisplayName("Normalises backslashes in path to POSIX forward slashes")
	void testPathNormalization() {
		ChunkMetadata meta = new ChunkMetadata(
				"owner/repo", SourceType.CODE, "src\\main\\java\\App.java", "java", null, null, null, null,
				ChunkType.CLASS, null, null, null, 1, 10, 5
		);
		assertThat(meta.path()).isEqualTo("src/main/java/App.java");
	}
}
