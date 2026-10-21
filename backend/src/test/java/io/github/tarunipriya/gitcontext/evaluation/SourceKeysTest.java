package io.github.tarunipriya.gitcontext.evaluation;

import io.github.tarunipriya.gitcontext.indexing.SearchHit;
import io.github.tarunipriya.gitcontext.model.ChunkType;
import io.github.tarunipriya.gitcontext.model.DocumentMetadata;
import io.github.tarunipriya.gitcontext.model.SourceType;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class SourceKeysTest {

	private DocumentMetadata createDocMeta(
			SourceType sourceType,
			String path,
			String url,
			String commitSha,
			Integer prNumber,
			Integer issueNumber
	) {
		return new DocumentMetadata(
				"test/repo",
				sourceType,
				path,
				url,
				"author",
				null,
				commitSha,
				prNumber,
				issueNumber,
				"java",
				"Title",
				Map.of()
		);
	}

	private SearchHit createHit(
			SourceType sourceType,
			String path,
			String url,
			String commitSha
	) {
		return new SearchHit(
				"hit-id",
				0.95,
				"test/repo",
				"run-1",
				"chunk-1",
				"doc-1",
				0,
				sourceType,
				ChunkType.SECTION,
				path,
				"java",
				"symbol",
				null,
				null,
				1,
				10,
				50,
				url,
				commitSha,
				"chunk text",
				"raw content"
		);
	}

	@Nested
	@DisplayName("SourceKeys.of(DocumentMetadata)")
	class DocumentMetadataTests {

		@Test
		@DisplayName("Returns normalized path when path is present and non-blank")
		void returnsNormalizedPath() {
			DocumentMetadata meta = createDocMeta(SourceType.CODE, ".\\src\\main\\App.java", null, null, null, null);
			assertThat(SourceKeys.of(meta)).isEqualTo("src/main/App.java");

			DocumentMetadata doc = createDocMeta(SourceType.DOC, "/docs/README.md", null, null, null, null);
			assertThat(SourceKeys.of(doc)).isEqualTo("docs/README.md");
		}

		@Test
		@DisplayName("Returns commit key with first 7 lowercase hex chars for COMMIT")
		void returnsCommitKey() {
			DocumentMetadata shortSha = createDocMeta(SourceType.COMMIT, null, null, "AbCdEf1", null, null);
			assertThat(SourceKeys.of(shortSha)).isEqualTo("commit:abcdef1");

			DocumentMetadata longSha = createDocMeta(
					SourceType.COMMIT, null, null, "4F7B2C9D0123456789ABCDEF0123456789ABCDEF", null, null);
			assertThat(SourceKeys.of(longSha)).isEqualTo("commit:4f7b2c9");
		}

		@Test
		@DisplayName("Returns null for COMMIT with missing or too-short commitSha")
		void returnsNullForCommitWithShortOrMissingSha() {
			DocumentMetadata noSha = createDocMeta(SourceType.COMMIT, null, null, null, null, null);
			assertThat(SourceKeys.of(noSha)).isNull();

			DocumentMetadata shortSha = createDocMeta(SourceType.COMMIT, null, null, "abc", null, null);
			assertThat(SourceKeys.of(shortSha)).isNull();
		}

		@Test
		@DisplayName("Returns pr key for PULL_REQUEST using prNumber")
		void returnsPrKeyFromPrNumber() {
			DocumentMetadata meta = createDocMeta(SourceType.PULL_REQUEST, null, "https://github.com/foo/bar/pull/16", null, 16, null);
			assertThat(SourceKeys.of(meta)).isEqualTo("pr:16");
		}

		@Test
		@DisplayName("Returns pr key for PULL_REQUEST parsed from url when prNumber is null")
		void returnsPrKeyFromUrlFallback() {
			DocumentMetadata meta = createDocMeta(SourceType.PULL_REQUEST, null, "https://github.com/foo/bar/pull/42", null, null, null);
			assertThat(SourceKeys.of(meta)).isEqualTo("pr:42");
		}

		@Test
		@DisplayName("Returns null for PULL_REQUEST when neither prNumber nor valid url is available")
		void returnsNullForPrWithoutNumberOrUrl() {
			DocumentMetadata noNumberOrUrl = createDocMeta(SourceType.PULL_REQUEST, null, null, null, null, null);
			assertThat(SourceKeys.of(noNumberOrUrl)).isNull();

			DocumentMetadata invalidUrl = createDocMeta(SourceType.PULL_REQUEST, null, "https://github.com/foo/bar", null, null, null);
			assertThat(SourceKeys.of(invalidUrl)).isNull();
		}

		@Test
		@DisplayName("Returns issue key for ISSUE using issueNumber")
		void returnsIssueKeyFromIssueNumber() {
			DocumentMetadata meta = createDocMeta(SourceType.ISSUE, null, "https://github.com/foo/bar/issues/7", null, null, 7);
			assertThat(SourceKeys.of(meta)).isEqualTo("issue:7");
		}

		@Test
		@DisplayName("Returns issue key for ISSUE parsed from url when issueNumber is null")
		void returnsIssueKeyFromUrlFallback() {
			DocumentMetadata meta = createDocMeta(SourceType.ISSUE, null, "https://github.com/foo/bar/issues/88", null, null, null);
			assertThat(SourceKeys.of(meta)).isEqualTo("issue:88");
		}

		@Test
		@DisplayName("Returns null for ISSUE when neither issueNumber nor valid url is available")
		void returnsNullForIssueWithoutNumberOrUrl() {
			DocumentMetadata noNumberOrUrl = createDocMeta(SourceType.ISSUE, null, null, null, null, null);
			assertThat(SourceKeys.of(noNumberOrUrl)).isNull();

			DocumentMetadata invalidUrl = createDocMeta(SourceType.ISSUE, null, "https://github.com/foo/bar", null, null, null);
			assertThat(SourceKeys.of(invalidUrl)).isNull();
		}

		@Test
		@DisplayName("Returns null for null metadata or metadata with no path and unhandled source type")
		void returnsNullForNullOrEmpty() {
			assertThat(SourceKeys.of((DocumentMetadata) null)).isNull();

			DocumentMetadata codeNoPath = createDocMeta(SourceType.CODE, null, null, null, null, null);
			assertThat(SourceKeys.of(codeNoPath)).isNull();
		}
	}

	@Nested
	@DisplayName("SourceKeys.of(SearchHit)")
	class SearchHitTests {

		@Test
		@DisplayName("Returns normalized path when path is present and non-blank")
		void returnsNormalizedPath() {
			SearchHit hit = createHit(SourceType.CODE, ".\\src\\main\\App.java", null, null);
			assertThat(SourceKeys.of(hit)).isEqualTo("src/main/App.java");

			SearchHit docHit = createHit(SourceType.DOC, "/docs/README.md", null, null);
			assertThat(SourceKeys.of(docHit)).isEqualTo("docs/README.md");
		}

		@Test
		@DisplayName("Returns commit key with first 7 lowercase hex chars for COMMIT")
		void returnsCommitKey() {
			SearchHit shortSha = createHit(SourceType.COMMIT, null, null, "AbCdEf1");
			assertThat(SourceKeys.of(shortSha)).isEqualTo("commit:abcdef1");

			SearchHit longSha = createHit(SourceType.COMMIT, null, null, "4F7B2C9D0123456789ABCDEF0123456789ABCDEF");
			assertThat(SourceKeys.of(longSha)).isEqualTo("commit:4f7b2c9");
		}

		@Test
		@DisplayName("Returns null for COMMIT with missing or too-short commitSha")
		void returnsNullForCommitWithShortOrMissingSha() {
			SearchHit noSha = createHit(SourceType.COMMIT, null, null, null);
			assertThat(SourceKeys.of(noSha)).isNull();

			SearchHit shortSha = createHit(SourceType.COMMIT, null, null, "12345");
			assertThat(SourceKeys.of(shortSha)).isNull();
		}

		@Test
		@DisplayName("Returns pr key for PULL_REQUEST parsed from url")
		void returnsPrKeyFromUrl() {
			SearchHit hit = createHit(SourceType.PULL_REQUEST, null, "https://github.com/tarunipriya/gitcontext/pull/16", null);
			assertThat(SourceKeys.of(hit)).isEqualTo("pr:16");

			SearchHit hitWithFiles = createHit(SourceType.PULL_REQUEST, null, "https://github.com/foo/bar/pull/105/files", null);
			assertThat(SourceKeys.of(hitWithFiles)).isEqualTo("pr:105");
		}

		@Test
		@DisplayName("Returns null for PULL_REQUEST when url is missing or does not match /pull/(\\d+)")
		void returnsNullForPrWithMissingOrNonMatchingUrl() {
			SearchHit missingUrl = createHit(SourceType.PULL_REQUEST, null, null, null);
			assertThat(SourceKeys.of(missingUrl)).isNull();

			SearchHit nonMatching = createHit(SourceType.PULL_REQUEST, null, "https://github.com/foo/bar/commits/123", null);
			assertThat(SourceKeys.of(nonMatching)).isNull();
		}

		@Test
		@DisplayName("Returns issue key for ISSUE parsed from url")
		void returnsIssueKeyFromUrl() {
			SearchHit hit = createHit(SourceType.ISSUE, null, "https://github.com/tarunipriya/gitcontext/issues/42", null);
			assertThat(SourceKeys.of(hit)).isEqualTo("issue:42");

			SearchHit hitWithAnchor = createHit(SourceType.ISSUE, null, "https://github.com/foo/bar/issues/3#issuecomment-99", null);
			assertThat(SourceKeys.of(hitWithAnchor)).isEqualTo("issue:3");
		}

		@Test
		@DisplayName("Returns null for ISSUE when url is missing or does not match /issues/(\\d+)")
		void returnsNullForIssueWithMissingOrNonMatchingUrl() {
			SearchHit missingUrl = createHit(SourceType.ISSUE, null, null, null);
			assertThat(SourceKeys.of(missingUrl)).isNull();

			SearchHit nonMatching = createHit(SourceType.ISSUE, null, "https://github.com/foo/bar", null);
			assertThat(SourceKeys.of(nonMatching)).isNull();
		}

		@Test
		@DisplayName("Returns null for null SearchHit or SearchHit with no path and unhandled source type")
		void returnsNullForNullOrEmpty() {
			assertThat(SourceKeys.of((SearchHit) null)).isNull();

			SearchHit codeNoPath = createHit(SourceType.CODE, null, null, null);
			assertThat(SourceKeys.of(codeNoPath)).isNull();
		}
	}

	@Nested
	@DisplayName("SourceKeys.normalizeExpected(String)")
	class NormalizeExpectedTests {

		@Test
		@DisplayName("Normalizes file paths via normalizePath")
		void normalizesFilePaths() {
			assertThat(SourceKeys.normalizeExpected("src/main/App.java")).isEqualTo("src/main/App.java");
			assertThat(SourceKeys.normalizeExpected(".\\src\\main\\App.java")).isEqualTo("src/main/App.java");
			assertThat(SourceKeys.normalizeExpected("./src/util/Helper.java")).isEqualTo("src/util/Helper.java");
			assertThat(SourceKeys.normalizeExpected("/src/config/Config.java")).isEqualTo("src/config/Config.java");
		}

		@Test
		@DisplayName("Normalizes commit source keys with short or full SHAs")
		void normalizesCommitKeys() {
			assertThat(SourceKeys.normalizeExpected("commit:abc1234")).isEqualTo("commit:abc1234");
			assertThat(SourceKeys.normalizeExpected("COMMIT:ABC1234")).isEqualTo("commit:abc1234");
			assertThat(SourceKeys.normalizeExpected("  commit:  4F7B2C9D0123456789ABCDEF0123456789ABCDEF  "))
					.isEqualTo("commit:4f7b2c9");
		}

		@Test
		@DisplayName("Normalizes pull request source keys")
		void normalizesPrKeys() {
			assertThat(SourceKeys.normalizeExpected("pr:16")).isEqualTo("pr:16");
			assertThat(SourceKeys.normalizeExpected("PR:16")).isEqualTo("pr:16");
			assertThat(SourceKeys.normalizeExpected("  pr:  42  ")).isEqualTo("pr:42");
			assertThat(SourceKeys.normalizeExpected("PR: 100")).isEqualTo("pr:100");
		}

		@Test
		@DisplayName("Normalizes issue source keys")
		void normalizesIssueKeys() {
			assertThat(SourceKeys.normalizeExpected("issue:3")).isEqualTo("issue:3");
			assertThat(SourceKeys.normalizeExpected("ISSUE:3")).isEqualTo("issue:3");
			assertThat(SourceKeys.normalizeExpected("  issue:  88  ")).isEqualTo("issue:88");
			assertThat(SourceKeys.normalizeExpected("Issue: 5")).isEqualTo("issue:5");
		}

		@Test
		@DisplayName("Rejects null or blank expected keys")
		void rejectsNullOrBlank() {
			assertThatNullPointerException()
					.isThrownBy(() -> SourceKeys.normalizeExpected(null))
					.withMessageContaining("key must not be null");

			assertThatIllegalArgumentException()
					.isThrownBy(() -> SourceKeys.normalizeExpected(""))
					.withMessageContaining("must not be blank");

			assertThatIllegalArgumentException()
					.isThrownBy(() -> SourceKeys.normalizeExpected("   "))
					.withMessageContaining("must not be blank");
		}

		@Test
		@DisplayName("Rejects invalid PR source keys")
		void rejectsInvalidPrKeys() {
			assertThatIllegalArgumentException()
					.isThrownBy(() -> SourceKeys.normalizeExpected("pr:abc"))
					.withMessageContaining("Invalid PR source key");

			assertThatIllegalArgumentException()
					.isThrownBy(() -> SourceKeys.normalizeExpected("pr:"))
					.withMessageContaining("Invalid PR source key");

			assertThatIllegalArgumentException()
					.isThrownBy(() -> SourceKeys.normalizeExpected("pr:-1"))
					.withMessageContaining("Invalid PR source key");

			assertThatIllegalArgumentException()
					.isThrownBy(() -> SourceKeys.normalizeExpected("pr:0"))
					.withMessageContaining("Invalid PR source key");

			assertThatIllegalArgumentException()
					.isThrownBy(() -> SourceKeys.normalizeExpected("pr:12abc"))
					.withMessageContaining("Invalid PR source key");
		}

		@Test
		@DisplayName("Rejects invalid issue source keys")
		void rejectsInvalidIssueKeys() {
			assertThatIllegalArgumentException()
					.isThrownBy(() -> SourceKeys.normalizeExpected("issue:abc"))
					.withMessageContaining("Invalid issue source key");

			assertThatIllegalArgumentException()
					.isThrownBy(() -> SourceKeys.normalizeExpected("issue:"))
					.withMessageContaining("Invalid issue source key");

			assertThatIllegalArgumentException()
					.isThrownBy(() -> SourceKeys.normalizeExpected("issue:-5"))
					.withMessageContaining("Invalid issue source key");

			assertThatIllegalArgumentException()
					.isThrownBy(() -> SourceKeys.normalizeExpected("issue:0"))
					.withMessageContaining("Invalid issue source key");
		}

		@Test
		@DisplayName("Rejects invalid commit source keys")
		void rejectsInvalidCommitKeys() {
			// Less than 7 hex chars
			assertThatIllegalArgumentException()
					.isThrownBy(() -> SourceKeys.normalizeExpected("commit:abc"))
					.withMessageContaining("Invalid commit source key");

			assertThatIllegalArgumentException()
					.isThrownBy(() -> SourceKeys.normalizeExpected("commit:"))
					.withMessageContaining("Invalid commit source key");

			// Non-hex chars
			assertThatIllegalArgumentException()
					.isThrownBy(() -> SourceKeys.normalizeExpected("commit:xyz1234"))
					.withMessageContaining("Invalid commit source key");

			assertThatIllegalArgumentException()
					.isThrownBy(() -> SourceKeys.normalizeExpected("commit:123456g"))
					.withMessageContaining("Invalid commit source key");
		}
	}
}
