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

	@Test
	@DisplayName("Sets citation URL with single line anchor for single line CODE chunk")
	void testCitationUrl_singleLine() {
		DocumentMetadata docMeta = new DocumentMetadata(
				"owner/repo",
				SourceType.CODE,
				"src/Main.java",
				"https://github.com/owner/repo/blob/sha123/src/Main.java",
				"alice",
				null,
				"sha123",
				null,
				null,
				"java",
				"Main.java",
				null
		);

		ChunkMetadata meta = ChunkMetadata.from(docMeta, ChunkType.METHOD, 10, "main", null, null, 12, 12);
		assertThat(meta.url()).isEqualTo("https://github.com/owner/repo/blob/sha123/src/Main.java#L12");
	}

	@Test
	@DisplayName("Sets citation URL with range anchor for multi-line CODE chunk")
	void testCitationUrl_range() {
		DocumentMetadata docMeta = new DocumentMetadata(
				"owner/repo",
				SourceType.CODE,
				"src/Main.java",
				"https://github.com/owner/repo/blob/sha123/src/Main.java",
				"alice",
				null,
				"sha123",
				null,
				null,
				"java",
				"Main.java",
				null
		);

		ChunkMetadata meta = ChunkMetadata.from(docMeta, ChunkType.METHOD, 50, "execute", null, null, 147, 192);
		assertThat(meta.url()).isEqualTo("https://github.com/owner/repo/blob/sha123/src/Main.java#L147-L192");
	}

	@Test
	@DisplayName("Constructs blob URL when docMeta.url() is null for CODE chunk")
	void testCitationUrl_computedFromRepoAndShaWhenUrlNull() {
		DocumentMetadata docMeta = new DocumentMetadata(
				"owner/repo",
				SourceType.DOC,
				"docs/readme.md",
				null,
				"alice",
				null,
				"sha999",
				null,
				null,
				"markdown",
				"readme.md",
				null
		);

		ChunkMetadata meta = ChunkMetadata.from(docMeta, ChunkType.SECTION, 20, null, null, "Setup", 5, 15);
		assertThat(meta.url()).isEqualTo("https://github.com/owner/repo/blob/sha999/docs/readme.md#L5-L15");
	}

	@Test
	@DisplayName("Preserves original document URL for COMMIT, PR, and ISSUE chunks")
	void testCitationUrl_preservesNonFileUrls() {
		DocumentMetadata commitDoc = new DocumentMetadata(
				"owner/repo",
				SourceType.COMMIT,
				null,
				"https://github.com/owner/repo/commit/sha123",
				"alice",
				null,
				"sha123",
				null,
				null,
				null,
				"feat: new thing",
				null
		);
		ChunkMetadata commitMeta = ChunkMetadata.from(commitDoc, ChunkType.COMMIT, 10, null, null, null, null, null);
		assertThat(commitMeta.url()).isEqualTo("https://github.com/owner/repo/commit/sha123");

		DocumentMetadata prDoc = new DocumentMetadata(
				"owner/repo",
				SourceType.PULL_REQUEST,
				null,
				"https://github.com/owner/repo/pull/42",
				"alice",
				null,
				null,
				42,
				null,
				null,
				"PR 42",
				null
		);
		ChunkMetadata prMeta = ChunkMetadata.from(prDoc, ChunkType.PULL_REQUEST, 10, null, null, null, null, null);
		assertThat(prMeta.url()).isEqualTo("https://github.com/owner/repo/pull/42");
	}
}
