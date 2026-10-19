package io.github.tarunipriya.gitcontext.retrieval;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class TestFileFilterTest {

	@ParameterizedTest
	@ValueSource(strings = {
			"src/test/java/com/example/MyService.java",
			"test/com/example/MyService.java",
			"src\\test\\java\\com\\example\\MyService.java",
			"backend/src/tests/unit/test_app.py",
			"tests/unit/app.py",
			"src/main/java/com/example/UserServiceTest.java",
			"src/main/java/com/example/UserServiceTests.java",
			"backend/app_test.py",
			"frontend/src/App.test.js",
			"frontend/src/App.test.ts",
			"frontend/src/App.spec.js",
			"frontend/src/App.spec.ts"
	})
	@DisplayName("Identifies test paths matching all required test patterns")
	void testIdentifiesTestPaths(String path) {
		assertThat(TestFileFilter.isTestPath(path))
				.as("Path '%s' should be detected as a test path", path)
				.isTrue();
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"src/main/java/com/example/MyService.java",
			"backend/src/app.py",
			"frontend/src/App.tsx",
			"src/main/testing/TestRunner.java",
			"src/main/java/com/example/ContestLeaderboard.java",
			"README.md",
			"backend/pom.xml"
	})
	@DisplayName("Identifies non-test paths as valid application code")
	void testIdentifiesNonTestPaths(String path) {
		assertThat(TestFileFilter.isTestPath(path))
				.as("Path '%s' should not be detected as a test path", path)
				.isFalse();
	}

	@Test
	@DisplayName("Returns false for null or blank paths")
	void testNullAndBlankPaths() {
		assertThat(TestFileFilter.isTestPath(null)).isFalse();
		assertThat(TestFileFilter.isTestPath("")).isFalse();
		assertThat(TestFileFilter.isTestPath("   ")).isFalse();
	}
}
