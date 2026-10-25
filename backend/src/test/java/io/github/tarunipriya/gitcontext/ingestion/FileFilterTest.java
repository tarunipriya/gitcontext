package io.github.tarunipriya.gitcontext.ingestion;

import io.github.tarunipriya.gitcontext.config.GitContextProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class FileFilterTest {

	@TempDir
	Path tempDir;

	private FileFilter fileFilter;

	@BeforeEach
	void setUp() {
		GitContextProperties properties = new GitContextProperties(
				null,
				tempDir,
				"http://localhost:6333",
				null,
				null,
				null
		);
		fileFilter = new FileFilter(properties);
	}

	@Test
	@DisplayName("Accepts normal code and documentation files")
	void testAcceptsValidFiles() throws IOException {
		Path javaFile = tempDir.resolve("src/main/java/App.java");
		Files.createDirectories(javaFile.getParent());
		Files.writeString(javaFile, "public class App {}");

		Path docFile = tempDir.resolve("README.md");
		Files.writeString(docFile, "# Title");

		assertThat(fileFilter.shouldIngest(tempDir, javaFile)).isTrue();
		assertThat(fileFilter.shouldIngest(tempDir, docFile)).isTrue();
	}

	@Test
	@DisplayName("Rejects files in excluded directories")
	void testRejectsExcludedDirectories() throws IOException {
		Path nodeModuleFile = tempDir.resolve("node_modules/package/index.js");
		Files.createDirectories(nodeModuleFile.getParent());
		Files.writeString(nodeModuleFile, "console.log(1);");

		Path gitFile = tempDir.resolve(".git/config");
		Files.createDirectories(gitFile.getParent());
		Files.writeString(gitFile, "[core]");

		Path targetFile = tempDir.resolve("target/classes/App.class");
		Files.createDirectories(targetFile.getParent());
		Files.writeString(targetFile, "compiled code");

		assertThat(fileFilter.shouldIngest(tempDir, nodeModuleFile)).isFalse();
		assertThat(fileFilter.shouldIngest(tempDir, gitFile)).isFalse();
		assertThat(fileFilter.shouldIngest(tempDir, targetFile)).isFalse();
	}

	@Test
	@DisplayName("Rejects lock files and minified assets")
	void testRejectsLockAndMinifiedFiles() throws IOException {
		Path lock1 = tempDir.resolve("package-lock.json");
		Files.writeString(lock1, "{}");

		Path lock2 = tempDir.resolve("yarn.lock");
		Files.writeString(lock2, "");

		Path minJs = tempDir.resolve("dist/bundle.min.js");
		Files.createDirectories(minJs.getParent());
		Files.writeString(minJs, "var a=1;");

		Path minCss = tempDir.resolve("style.min.css");
		Files.writeString(minCss, "body{margin:0;}");

		assertThat(fileFilter.shouldIngest(tempDir, lock1)).isFalse();
		assertThat(fileFilter.shouldIngest(tempDir, lock2)).isFalse();
		assertThat(fileFilter.shouldIngest(tempDir, minJs)).isFalse();
		assertThat(fileFilter.shouldIngest(tempDir, minCss)).isFalse();
	}

	@Test
	@DisplayName("Rejects binary files with NUL byte in the first 8 KB")
	void testRejectsBinaryFiles() throws IOException {
		Path binaryFile = tempDir.resolve("icon.png");
		byte[] binaryData = new byte[]{0x47, 0x49, 0x46, 0x38, 0x00, 0x01, 0x02};
		Files.write(binaryFile, binaryData);

		assertThat(fileFilter.shouldIngest(tempDir, binaryFile)).isFalse();
	}

	@Test
	@DisplayName("Rejects files exceeding maxFileSizeKb")
	void testRejectsOversizedFiles() throws IOException {
		Path largeFile = tempDir.resolve("large.txt");
		byte[] largeData = new byte[600 * 1024]; // 600 KB > 512 KB default
		for (int i = 0; i < largeData.length; i++) {
			largeData[i] = 'A';
		}
		Files.write(largeFile, largeData);

		assertThat(fileFilter.shouldIngest(tempDir, largeFile)).isFalse();
	}

	@Test
	@DisplayName("Rejects files with disallowed extensions")
	void testRejectsDisallowedExtensions() throws IOException {
		Path exeFile = tempDir.resolve("program.exe");
		Files.writeString(exeFile, "MZ header");

		Path unknownFile = tempDir.resolve("data.xyz");
		Files.writeString(unknownFile, "custom data");

		assertThat(fileFilter.shouldIngest(tempDir, exeFile)).isFalse();
		assertThat(fileFilter.shouldIngest(tempDir, unknownFile)).isFalse();
	}

	@Test
	@DisplayName("Rejects files matching custom minified pattern when configured")
	void testRejectsCustomMinifiedPattern() throws IOException {
		GitContextProperties propertiesWithCustomPattern = new GitContextProperties(
				null,
				tempDir,
				"http://localhost:6333",
				null,
				new GitContextProperties.IngestionProperties(
						512, 500, 4000,
						null, null, null, null, null,
						java.util.List.of("*.bundle.js")
				),
				null
		);
		FileFilter customFilter = new FileFilter(propertiesWithCustomPattern);

		Path bundleFile = tempDir.resolve("dist/app.bundle.js");
		Files.createDirectories(bundleFile.getParent());
		Files.writeString(bundleFile, "console.log('bundled');");

		Path normalFile = tempDir.resolve("src/app.js");
		Files.createDirectories(normalFile.getParent());
		Files.writeString(normalFile, "console.log('normal');");

		assertThat(customFilter.shouldIngest(tempDir, bundleFile)).isFalse();
		assertThat(customFilter.shouldIngest(tempDir, normalFile)).isTrue();
	}

	@Test
	@DisplayName("Rejects root eval/ files by default prefix configuration")
	void testRejectsRootEvalFile() throws IOException {
		Path evalDataset = tempDir.resolve("eval/datasets/smoke.jsonl");
		Files.createDirectories(evalDataset.getParent());
		Files.writeString(evalDataset, "{\"id\":\"c1\",\"question\":\"q\",\"expectedPaths\":[\"a.java\"],\"expectedFacts\":[],\"category\":\"gen\",\"answerable\":true}");

		Path evalReadme = tempDir.resolve("eval/README.md");
		Files.writeString(evalReadme, "# Eval Docs");

		assertThat(fileFilter.shouldIngest(tempDir, evalDataset)).isFalse();
		assertThat(fileFilter.shouldIngest(tempDir, evalReadme)).isFalse();
	}

	@Test
	@DisplayName("Accepts nested src/eval/ files since prefix is matched from repo root only")
	void testAcceptsNestedEvalFile() throws IOException {
		Path nestedFile = tempDir.resolve("src/eval/Foo.java");
		Files.createDirectories(nestedFile.getParent());
		Files.writeString(nestedFile, "package src.eval; public class Foo {}");

		assertThat(fileFilter.shouldIngest(tempDir, nestedFile)).isTrue();
	}

	@Test
	@DisplayName("Rejects root eval files specified with Windows-style backslashes")
	void testRejectsWindowsStyleBackslashEvalPath() throws IOException {
		Path evalDir = tempDir.resolve("eval").resolve("cases");
		Files.createDirectories(evalDir);
		Path file = evalDir.resolve("test.jsonl");
		Files.writeString(file, "{}");

		// Pass file with backslashes
		Path backslashPath = Path.of(tempDir.toString() + "\\eval\\cases\\test.jsonl");
		assertThat(fileFilter.shouldIngest(tempDir, backslashPath)).isFalse();
	}

	@Test
	@DisplayName("Rejects files matching custom path prefix configured via properties")
	void testRejectsCustomPrefixViaProperties() throws IOException {
		GitContextProperties customProperties = new GitContextProperties(
				null,
				tempDir,
				"http://localhost:6333",
				null,
				new GitContextProperties.IngestionProperties(
						512, 500, 4000,
						null,
						java.util.List.of("benchmark/", "./fixtures/"),
						null, null, null, null
				),
				null
		);
		FileFilter customFilter = new FileFilter(customProperties);

		Path benchmarkFile = tempDir.resolve("benchmark/cases.json");
		Files.createDirectories(benchmarkFile.getParent());
		Files.writeString(benchmarkFile, "{}");

		Path fixtureFile = tempDir.resolve("fixtures/data.json");
		Files.createDirectories(fixtureFile.getParent());
		Files.writeString(fixtureFile, "{}");

		Path srcFile = tempDir.resolve("src/benchmark/Case.java");
		Files.createDirectories(srcFile.getParent());
		Files.writeString(srcFile, "public class Case {}");

		assertThat(customFilter.shouldIngest(tempDir, benchmarkFile)).isFalse();
		assertThat(customFilter.shouldIngest(tempDir, fixtureFile)).isFalse();
		assertThat(customFilter.shouldIngest(tempDir, srcFile)).isTrue();
	}

	@Test
	@DisplayName("Empty prefix list excludes nothing extra from root")
	void testEmptyPrefixListExcludesNothingExtra() throws IOException {
		GitContextProperties emptyPrefixProps = new GitContextProperties(
				null,
				tempDir,
				"http://localhost:6333",
				null,
				new GitContextProperties.IngestionProperties(
						512, 500, 4000,
						null,
						java.util.List.of(),
						null, null, null, null
				),
				null
		);
		FileFilter emptyPrefixFilter = new FileFilter(emptyPrefixProps);

		Path evalJava = tempDir.resolve("eval/Test.java");
		Files.createDirectories(evalJava.getParent());
		Files.writeString(evalJava, "public class Test {}");

		assertThat(emptyPrefixFilter.shouldIngest(tempDir, evalJava)).isTrue();
	}

	@Test
	@DisplayName("isExcludedPath identifies excluded root path prefixes")
	void testIsExcludedPathPrefixes() {
		assertThat(fileFilter.isExcludedPath("eval/datasets/smoke.jsonl")).isTrue();
		assertThat(fileFilter.isExcludedPath("eval/README.md")).isTrue();
		assertThat(fileFilter.isExcludedPath("eval\\results\\summary.json")).isTrue();
		assertThat(fileFilter.isExcludedPath("./eval/cases.json")).isTrue();
		assertThat(fileFilter.isExcludedPath("/eval/cases.json")).isTrue();

		// Nested eval directory must NOT be excluded
		assertThat(fileFilter.isExcludedPath("src/eval/Foo.java")).isFalse();
		assertThat(fileFilter.isExcludedPath("backend/src/main/java/evaluation/EvalRunner.java")).isFalse();
	}

	@Test
	@DisplayName("isExcludedPath identifies excluded directories, lock files, and minified patterns")
	void testIsExcludedPathDirectoriesAndPatterns() {
		// Excluded directories
		assertThat(fileFilter.isExcludedPath("node_modules/pkg/index.js")).isTrue();
		assertThat(fileFilter.isExcludedPath(".git/HEAD")).isTrue();
		assertThat(fileFilter.isExcludedPath("target/classes/App.class")).isTrue();
		assertThat(fileFilter.isExcludedPath("sub/node_modules/foo.js")).isTrue();

		// Lock files
		assertThat(fileFilter.isExcludedPath("package-lock.json")).isTrue();
		assertThat(fileFilter.isExcludedPath("yarn.lock")).isTrue();
		assertThat(fileFilter.isExcludedPath("pnpm-lock.yaml")).isTrue();

		// Minified patterns
		assertThat(fileFilter.isExcludedPath("bundle.min.js")).isTrue();
		assertThat(fileFilter.isExcludedPath("dist/app.min.js")).isTrue();
		assertThat(fileFilter.isExcludedPath("style.min.css")).isTrue();

		// Normal code, documentation, and configuration files must not be excluded
		assertThat(fileFilter.isExcludedPath("src/main/java/App.java")).isFalse();
		assertThat(fileFilter.isExcludedPath("README.md")).isFalse();
		assertThat(fileFilter.isExcludedPath("pom.xml")).isFalse();
		assertThat(fileFilter.isExcludedPath("application.yml")).isFalse();

		// Null or blank paths are treated as excluded
		assertThat(fileFilter.isExcludedPath(null)).isTrue();
		assertThat(fileFilter.isExcludedPath("")).isTrue();
		assertThat(fileFilter.isExcludedPath("   ")).isTrue();
	}
}
