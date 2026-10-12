package io.github.tarunipriya.gitcontext.chunking;

import io.github.tarunipriya.gitcontext.model.SourceType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ContextHeaderBuilderTest {

	private final ContextHeaderBuilder builder = new ContextHeaderBuilder();

	@Test
	@DisplayName("Java vs Python comment styles use // and # respectively")
	void testCommentStyle() {
		String javaHeader = builder.buildFileHeader("src/Main.java", "java", "Main", "run", null, 10, 20);
		assertThat(javaHeader).startsWith("// File: src/Main.java");
		assertThat(javaHeader).contains("// Class/Parent: Main");
		assertThat(javaHeader).contains("// Symbol: run");
		assertThat(javaHeader).contains("// Lines: 10-20");

		String pythonHeader = builder.buildFileHeader("app/main.py", "python", "App", "start", null, 15, 30);
		assertThat(pythonHeader).startsWith("# File: app/main.py");
		assertThat(pythonHeader).contains("# Class/Parent: App");
		assertThat(pythonHeader).contains("# Symbol: start");
		assertThat(pythonHeader).contains("# Lines: 15-30");

		String yamlHeader = builder.buildFileHeader("ci.yml", "yaml", null, null, null, 1, 10);
		assertThat(yamlHeader).startsWith("# File: ci.yml");

		String defaultHeader = builder.buildFileHeader("unknown.xyz", null, null, null, null, 1, 5);
		assertThat(defaultHeader).startsWith("// File: unknown.xyz");
	}

	@Test
	@DisplayName("Only present fields are included in the generated context header")
	void testOnlyPresentFieldsIncluded() {
		// Only path and lines
		String pathAndLines = builder.buildFileHeader("README.md", "markdown", null, null, null, 1, 50);
		assertThat(pathAndLines).isEqualTo("// File: README.md\n// Lines: 1-50");
		assertThat(pathAndLines).doesNotContain("Class/Parent:");
		assertThat(pathAndLines).doesNotContain("Symbol:");
		assertThat(pathAndLines).doesNotContain("Section:");

		// Section and symbol present
		String sectionAndSymbol = builder.buildFileHeader(null, "java", null, "calculate", "Math > Add", null, null);
		assertThat(sectionAndSymbol).isEqualTo("// Symbol: calculate\n// Section: Math > Add");
		assertThat(sectionAndSymbol).doesNotContain("File:");
		assertThat(sectionAndSymbol).doesNotContain("Lines:");

		// All null/blank returns empty string
		String empty = builder.buildFileHeader(null, "java", null, null, null, null, null);
		assertThat(empty).isEmpty();
	}

	@Test
	@DisplayName("Commit, PR, and Issue headers format correctly with short SHA and titles")
	void testRecordHeaders() {
		String commitWithTitle = builder.buildRecordHeader(
				SourceType.COMMIT,
				"0123456789abcdef",
				null,
				null,
				"Initial commit"
		);
		assertThat(commitWithTitle).isEqualTo("Commit 0123456: Initial commit");

		String commitWithoutTitle = builder.buildRecordHeader(
				SourceType.COMMIT,
				"abcdef1",
				null,
				null,
				null
		);
		assertThat(commitWithoutTitle).isEqualTo("Commit abcdef1");

		String prWithTitle = builder.buildRecordHeader(
				SourceType.PULL_REQUEST,
				null,
				42,
				null,
				"Add authentication"
		);
		assertThat(prWithTitle).isEqualTo("PR #42: Add authentication");

		String prWithoutTitle = builder.buildRecordHeader(
				SourceType.PULL_REQUEST,
				null,
				42,
				null,
				null
		);
		assertThat(prWithoutTitle).isEqualTo("PR #42");

		String issueWithTitle = builder.buildRecordHeader(
				SourceType.ISSUE,
				null,
				null,
				101,
				"Crash on startup"
		);
		assertThat(issueWithTitle).isEqualTo("Issue #101: Crash on startup");

		String issueWithoutTitle = builder.buildRecordHeader(
				SourceType.ISSUE,
				null,
				null,
				101,
				null
		);
		assertThat(issueWithoutTitle).isEqualTo("Issue #101");
	}
}
