package io.github.tarunipriya.gitcontext.ingestion;

import io.github.tarunipriya.gitcontext.config.GitContextProperties;
import io.github.tarunipriya.gitcontext.ingestion.RepositoryCloner.ClonedRepo;
import io.github.tarunipriya.gitcontext.model.Document;
import io.github.tarunipriya.gitcontext.model.RepoRef;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.internal.storage.file.WindowCache;
import org.eclipse.jgit.storage.file.WindowCacheConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class CommitIngestorTest {

	@TempDir
	Path tempDir;

	private Path repoDir;
	private Git git;
	private CommitIngestor commitIngestor;
	private RepoRef repoRef;
	private ClonedRepo clonedRepo;

	@BeforeEach
	void setUp() throws Exception {
		repoDir = tempDir.resolve("repo");
		Files.createDirectories(repoDir);

		git = Git.init().setDirectory(repoDir.toFile()).setInitialBranch("main").call();

		GitContextProperties properties = new GitContextProperties(
				null,
				tempDir,
				"http://localhost:6333",
				null,
				null,
				null
		);
		FileFilter fileFilter = new FileFilter(properties);
		commitIngestor = new CommitIngestor(properties, fileFilter);

		repoRef = new RepoRef("testowner", "testrepo");
		clonedRepo = new ClonedRepo(repoRef, repoDir, "main", "dummy");
	}

	@AfterEach
	void tearDown() {
		if (git != null) {
			git.close();
			git.getRepository().close();
		}
		WindowCache.reconfigure(new WindowCacheConfig());
	}

	@Test
	@DisplayName("Commit touching only eval/ has no eval content in document text and includes excluded paths note")
	void testCommitTouchingOnlyEval() throws Exception {
		writeFile("eval/datasets/smoke.jsonl", "{\"id\":\"eval-101\",\"question\":\"What is the secret leak?\",\"expectedFacts\":[\"leak\"]}");
		git.add().addFilepattern(".").call();
		git.commit().setMessage("test: add evaluation benchmark dataset").call();

		List<Document> documents = commitIngestor.ingest(clonedRepo);
		assertThat(documents).hasSize(1);

		Document doc = documents.get(0);
		String content = doc.content();

		// Commit message must be present
		assertThat(content).contains("test: add evaluation benchmark dataset");

		// Note must be present
		assertThat(content).contains("(changes limited to excluded paths)");

		// No eval file names, paths, or diff contents should leak into document text
		assertThat(content).doesNotContain("eval/datasets/smoke.jsonl");
		assertThat(content).doesNotContain("What is the secret leak?");
		assertThat(content).doesNotContain("Files changed:");

		// filesChanged counter in metadata must be 0
		assertThat(doc.metadata().extra()).containsEntry("filesChanged", "0");
	}

	@Test
	@DisplayName("Mixed commit (eval/ + src/) includes only src/ diff and file entry, omitting eval/")
	void testMixedCommitIncludesOnlySrc() throws Exception {
		writeFile("eval/datasets/test.jsonl", "{\"id\":\"secret-eval\",\"question\":\"Leak?\"}");
		writeFile("src/main/java/App.java", "package com.example;\npublic class App {\n    int active = 100;\n}");
		git.add().addFilepattern(".").call();
		git.commit().setMessage("feat: add core app and test suite").call();

		List<Document> documents = commitIngestor.ingest(clonedRepo);
		assertThat(documents).hasSize(1);

		Document doc = documents.get(0);
		String content = doc.content();

		// Header and commit message
		assertThat(content).contains("feat: add core app and test suite");
		assertThat(content).contains("Files changed:");

		// src/ entry and diff must be present
		assertThat(content).contains("src/main/java/App.java");
		assertThat(content).contains("int active = 100;");

		// eval/ content must NOT be present
		assertThat(content).doesNotContain("eval/datasets/test.jsonl");
		assertThat(content).doesNotContain("secret-eval");
		assertThat(content).doesNotContain("Leak?");

		// The "(changes limited to excluded paths)" note must NOT be present since src/ was included
		assertThat(content).doesNotContain("(changes limited to excluded paths)");

		// filesChanged counter reflects only the 1 non-excluded file
		assertThat(doc.metadata().extra()).containsEntry("filesChanged", "1");
	}

	@Test
	@DisplayName("Nested src/eval/Foo.java path is not excluded from commit diffs")
	void testNestedEvalPathNotExcluded() throws Exception {
		writeFile("src/eval/Foo.java", "package src.eval;\npublic class Foo {\n    void evaluate() {}\n}");
		git.add().addFilepattern(".").call();
		git.commit().setMessage("feat: add nested eval utility").call();

		List<Document> documents = commitIngestor.ingest(clonedRepo);
		assertThat(documents).hasSize(1);

		Document doc = documents.get(0);
		String content = doc.content();

		assertThat(content).contains("feat: add nested eval utility");
		assertThat(content).contains("Files changed:");
		assertThat(content).contains("src/eval/Foo.java");
		assertThat(content).contains("void evaluate()");
		assertThat(content).doesNotContain("(changes limited to excluded paths)");
		assertThat(doc.metadata().extra()).containsEntry("filesChanged", "1");
	}

	@Test
	@DisplayName("Commit deleting an eval/ file is also excluded and annotated with note")
	void testDeleteEvalFileIsExcluded() throws Exception {
		// Initial commit with an eval file
		writeFile("eval/old.jsonl", "{\"id\":\"old\"}");
		git.add().addFilepattern(".").call();
		git.commit().setMessage("chore: add old eval file").call();

		// Second commit deletes the eval file
		git.rm().addFilepattern("eval/old.jsonl").call();
		git.commit().setMessage("chore: delete old eval file").call();

		List<Document> documents = commitIngestor.ingest(clonedRepo);
		assertThat(documents).hasSize(2);

		Document deleteDoc = documents.get(0);
		assertThat(deleteDoc.metadata().title()).isEqualTo("chore: delete old eval file");
		assertThat(deleteDoc.content()).contains("(changes limited to excluded paths)");
		assertThat(deleteDoc.content()).doesNotContain("eval/old.jsonl");
		assertThat(deleteDoc.metadata().extra()).containsEntry("filesChanged", "0");
	}

	@Test
	@DisplayName("Commit touching only lock files or excluded directories is also annotated with note")
	void testCommitTouchingOnlyLockFilesOrExcludedDirs() throws Exception {
		writeFile("package-lock.json", "{\"name\":\"test\"}");
		writeFile("node_modules/pkg/index.js", "console.log(1);");
		git.add().addFilepattern(".").call();
		git.commit().setMessage("chore: update dependencies and locks").call();

		List<Document> documents = commitIngestor.ingest(clonedRepo);
		assertThat(documents).hasSize(1);

		Document doc = documents.get(0);
		assertThat(doc.content()).contains("chore: update dependencies and locks");
		assertThat(doc.content()).contains("(changes limited to excluded paths)");
		assertThat(doc.content()).doesNotContain("package-lock.json");
		assertThat(doc.content()).doesNotContain("node_modules");
		assertThat(doc.metadata().extra()).containsEntry("filesChanged", "0");
	}

	private void writeFile(String relativePath, String content) throws IOException {
		Path path = repoDir.resolve(relativePath);
		Files.createDirectories(path.getParent());
		Files.writeString(path, content);
	}
}
