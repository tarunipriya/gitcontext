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
}
