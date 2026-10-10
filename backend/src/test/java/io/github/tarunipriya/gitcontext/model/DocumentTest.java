package io.github.tarunipriya.gitcontext.model;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

class DocumentTest {

	private final DocumentMetadata standardMetadata = new DocumentMetadata(
			"owner/repo",
			SourceType.CODE,
			"src/main/java/App.java",
			"https://github.com/owner/repo/blob/main/src/main/java/App.java",
			"octocat",
			Instant.parse("2026-10-10T10:00:00Z"),
			"a1b2c3d4e5f67890",
			null,
			null,
			"java",
			"App.java",
			Map.of("category", "backend")
	);

	@Test
	@DisplayName("Same key gives the same id even when content changes")
	void testSameKeyGivesSameId() {
		Document docV1 = Document.create("public class App { int v = 1; }", standardMetadata, "src/main/java/App.java");
		Document docV2 = Document.create("public class App { int v = 2; }", standardMetadata, "src/main/java/App.java");

		assertThat(docV1.id()).isEqualTo(docV2.id());
		assertThat(docV1.contentHash()).isNotEqualTo(docV2.contentHash());
	}

	@Test
	@DisplayName("Changed content changes contentHash, while identical content gives identical contentHash")
	void testContentHashBehavior() {
		Document doc1 = Document.create("content A", standardMetadata, "file1.txt");
		Document doc2 = Document.create("content B", standardMetadata, "file1.txt");
		Document doc3 = Document.create("content A", standardMetadata, "file2.txt");

		assertThat(doc1.contentHash()).isNotEqualTo(doc2.contentHash());
		assertThat(doc1.contentHash()).isEqualTo(doc3.contentHash());
		assertThat(doc1.id()).isNotEqualTo(doc3.id());
	}

	@Test
	@DisplayName("Different source types with the same key give different ids")
	void testDifferentSourceTypesGiveDifferentIds() {
		DocumentMetadata codeMeta = new DocumentMetadata("owner/repo", SourceType.CODE, "README.md", null, null, null, null, null, null, "md", null, null);
		DocumentMetadata docMeta = new DocumentMetadata("owner/repo", SourceType.DOC, "README.md", null, null, null, null, null, null, "md", null, null);

		Document codeDoc = Document.create("# README", codeMeta, "README.md");
		Document docDoc = Document.create("# README", docMeta, "README.md");

		assertThat(codeDoc.id()).isNotEqualTo(docDoc.id());
		assertThat(codeDoc.contentHash()).isEqualTo(docDoc.contentHash());
	}

	@Test
	@DisplayName("Different repositories with the same key give different ids")
	void testDifferentReposGiveDifferentIds() {
		DocumentMetadata repo1Meta = new DocumentMetadata("owner1/repo", SourceType.CODE, "App.java", null, null, null, null, null, null, "java", null, null);
		DocumentMetadata repo2Meta = new DocumentMetadata("owner2/repo", SourceType.CODE, "App.java", null, null, null, null, null, null, "java", null, null);

		Document doc1 = Document.create("class App {}", repo1Meta, "App.java");
		Document doc2 = Document.create("class App {}", repo2Meta, "App.java");

		assertThat(doc1.id()).isNotEqualTo(doc2.id());
	}

	@Test
	@DisplayName("Null and blank required metadata fields are rejected")
	void testMetadataValidation() {
		assertThatNullPointerException()
				.isThrownBy(() -> new DocumentMetadata(null, SourceType.CODE, null, null, null, null, null, null, null, null, null, null))
				.withMessageContaining("repo must not be null");

		assertThatIllegalArgumentException()
				.isThrownBy(() -> new DocumentMetadata("   ", SourceType.CODE, null, null, null, null, null, null, null, null, null, null))
				.withMessageContaining("repo must not be blank");

		assertThatNullPointerException()
				.isThrownBy(() -> new DocumentMetadata("owner/repo", null, null, null, null, null, null, null, null, null, null, null))
				.withMessageContaining("sourceType must not be null");
	}

	@Test
	@DisplayName("Document.create validates arguments against null")
	void testDocumentCreateValidation() {
		assertThatNullPointerException()
				.isThrownBy(() -> Document.create(null, standardMetadata, "key"))
				.withMessageContaining("content must not be null");

		assertThatNullPointerException()
				.isThrownBy(() -> Document.create("content", null, "key"))
				.withMessageContaining("metadata must not be null");

		assertThatNullPointerException()
				.isThrownBy(() -> Document.create("content", standardMetadata, null))
				.withMessageContaining("key must not be null");
	}

	@Test
	@DisplayName("Extra metadata map is immutable and null-safe")
	void testExtraMapImmutability() {
		DocumentMetadata metaWithNull = new DocumentMetadata("owner/repo", SourceType.CODE, null, null, null, null, null, null, null, null, null, null);
		assertThat(metaWithNull.extra()).isNotNull().isEmpty();
		assertThatThrownBy(() -> metaWithNull.extra().put("foo", "bar"))
				.isInstanceOf(UnsupportedOperationException.class);

		Map<String, String> mutable = new HashMap<>();
		mutable.put("env", "staging");
		DocumentMetadata metaWithMap = new DocumentMetadata("owner/repo", SourceType.CODE, null, null, null, null, null, null, null, null, null, mutable);

		mutable.put("extra_key", "extra_val");
		assertThat(metaWithMap.extra()).containsOnly(entry("env", "staging"));
		assertThatThrownBy(() -> metaWithMap.extra().put("another", "val"))
				.isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	@DisplayName("Path separators in metadata are normalised to POSIX / format")
	void testPathSeparatorNormalisation() {
		DocumentMetadata meta = new DocumentMetadata(
				"owner/repo",
				SourceType.CODE,
				"src\\main\\java\\io\\App.java",
				null, null, null, null, null, null, null, null, null
		);

		assertThat(meta.path()).isEqualTo("src/main/java/io/App.java");
	}
}
