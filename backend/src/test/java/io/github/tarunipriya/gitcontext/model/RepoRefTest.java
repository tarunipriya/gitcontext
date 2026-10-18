package io.github.tarunipriya.gitcontext.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class RepoRefTest {

	@ParameterizedTest
	@ValueSource(strings = {
			"owner/repo",
			"https://github.com/owner/repo",
			"https://github.com/owner/repo.git",
			"http://github.com/owner/repo",
			"http://github.com/owner/repo.git",
			"git@github.com:owner/repo.git",
			"git@github.com:owner/repo",
			"github.com/owner/repo",
			"owner/repo/"
	})
	@DisplayName("Parses valid GitHub repository reference formats")
	void testValidRepoRefFormats(String input) {
		RepoRef repoRef = RepoRef.parse(input);
		assertThat(repoRef.owner()).isEqualTo("owner");
		assertThat(repoRef.name()).isEqualTo("repo");
		assertThat(repoRef.fullName()).isEqualTo("owner/repo");
		assertThat(repoRef.httpsCloneUrl()).isEqualTo("https://github.com/owner/repo.git");
		assertThat(repoRef.webUrl()).isEqualTo("https://github.com/owner/repo");
		assertThat(repoRef.toString()).isEqualTo("owner/repo");
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"",
			"   ",
			"onlyone",
			"owner/repo/extra/path",
			"https://gitlab.com/owner/repo",
			"owner//repo",
			"/owner/repo/sub",
			"owner/repo with spaces"
	})
	@DisplayName("Rejects invalid repository formats with clear error message")
	void testInvalidRepoRefFormats(String invalidInput) {
		assertThatIllegalArgumentException()
				.isThrownBy(() -> RepoRef.parse(invalidInput));
	}

	@Test
	@DisplayName("Null input to parse throws IllegalArgumentException")
	void testNullParseThrows() {
		assertThatIllegalArgumentException()
				.isThrownBy(() -> RepoRef.parse(null));
	}

	@Test
	@DisplayName("Constructor enforces non-null and non-blank fields")
	void testConstructorValidation() {
		assertThatNullPointerException()
				.isThrownBy(() -> new RepoRef(null, "repo"));
		assertThatIllegalArgumentException()
				.isThrownBy(() -> new RepoRef("  ", "repo"));
		assertThatNullPointerException()
				.isThrownBy(() -> new RepoRef("owner", null));
		assertThatIllegalArgumentException()
				.isThrownBy(() -> new RepoRef("owner", "  "));
	}

	@Test
	@DisplayName("RepoRef equality and hashCode are case-insensitive while preserving submitted display name")
	void testCaseInsensitiveEquality() {
		RepoRef mixed = new RepoRef("TestOwner", "TestRepo");
		RepoRef lower = new RepoRef("testowner", "testrepo");
		RepoRef upper = new RepoRef("TESTOWNER", "TESTREPO");

		assertThat(mixed).isEqualTo(lower);
		assertThat(mixed).isEqualTo(upper);
		assertThat(mixed.hashCode()).isEqualTo(lower.hashCode());
		assertThat(mixed.hashCode()).isEqualTo(upper.hashCode());

		assertThat(mixed.normalizedKey()).isEqualTo("testowner/testrepo");
		assertThat(mixed.fullName()).isEqualTo("TestOwner/TestRepo");
		assertThat(upper.fullName()).isEqualTo("TESTOWNER/TESTREPO");
	}
}
