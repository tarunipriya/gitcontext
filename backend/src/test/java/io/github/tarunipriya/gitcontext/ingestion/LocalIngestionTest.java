package io.github.tarunipriya.gitcontext.ingestion;

import io.github.tarunipriya.gitcontext.config.GitContextProperties;
import io.github.tarunipriya.gitcontext.ingestion.RepositoryCloner.ClonedRepo;
import io.github.tarunipriya.gitcontext.model.Document;
import io.github.tarunipriya.gitcontext.model.RepoRef;
import io.github.tarunipriya.gitcontext.model.SourceType;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.internal.storage.file.WindowCache;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.WindowCacheConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class LocalIngestionTest {

	@TempDir
	Path tempDir;

	private Path sourceRepoDir;
	private Path reposDir;
	private Git sourceGit;
	private GitContextProperties properties;
	private RepositoryCloner repositoryCloner;
	private FileFilter fileFilter;
	private FileIngestor fileIngestor;
	private CommitIngestor commitIngestor;

	private RevCommit commit1;
	private RevCommit commit2;
	private RevCommit commit3;

	@BeforeEach
	void setUp() throws Exception {
		sourceRepoDir = tempDir.resolve("source-repo");
		reposDir = tempDir.resolve("repos");
		Files.createDirectories(sourceRepoDir);
		Files.createDirectories(reposDir);

		sourceGit = Git.init().setDirectory(sourceRepoDir.toFile()).setInitialBranch("main").call();

		// Commit 1: Initial commit with files (some valid, some excluded)
		writeFile(sourceRepoDir, "README.md", "# Test Project\nWelcome to test project.");
		writeFile(sourceRepoDir, "src/main/java/App.java", "package com.example;\npublic class App {\n    int version = 1;\n}");
		writeFile(sourceRepoDir, "package-lock.json", "{\"name\": \"test\"}");
		writeFile(sourceRepoDir, "app.min.js", "var a=1;");
		writeFile(sourceRepoDir, "node_modules/subpkg/index.js", "console.log(1);");
		writeBinaryFile(sourceRepoDir, "logo.png", new byte[]{0x00, 0x01, 0x02});

		sourceGit.add().addFilepattern(".").call();
		commit1 = sourceGit.commit().setMessage("feat: initial commit with app and readme").setAuthor("Alice", "alice@example.com").call();

		// Commit 2: Add docs and a helper
		writeFile(sourceRepoDir, "docs/architecture.adoc", "= Architecture Guide\nOverview of the system.");
		writeFile(sourceRepoDir, "src/main/java/Helper.java", "package com.example;\npublic class Helper {}");

		sourceGit.add().addFilepattern(".").call();
		commit2 = sourceGit.commit().setMessage("docs: add architecture documentation and helper").setAuthor("Bob", "bob@example.com").call();

		// Commit 3: Modify App.java and add another class
		writeFile(sourceRepoDir, "src/main/java/App.java", "package com.example;\npublic class App {\n    int version = 2;\n    String message = \"hello\";\n}");
		writeFile(sourceRepoDir, "src/main/java/Utils.java", "package com.example;\npublic class Utils {}");

		sourceGit.add().addFilepattern(".").call();
		commit3 = sourceGit.commit().setMessage("feat: update app version and add utils").setAuthor("Alice", "alice@example.com").call();

		properties = new GitContextProperties(
				null,
				reposDir,
				"http://localhost:6333",
				null,
				null,
				null
		);
		repositoryCloner = new RepositoryCloner(properties);
		fileFilter = new FileFilter(properties);
		fileIngestor = new FileIngestor(properties, fileFilter);
		commitIngestor = new CommitIngestor(properties, fileFilter);
	}

	@AfterEach
	void tearDown() {
		if (sourceGit != null) {
			sourceGit.close();
			sourceGit.getRepository().close();
		}
		// Clear JGit mmap file cache to ensure Windows can cleanly delete temporary files
		WindowCache.reconfigure(new WindowCacheConfig());
	}

	@Test
	@DisplayName("Clones repository from local file:// URL and updates on new commits")
	void testRepositoryCloner() throws Exception {
		RepoRef repoRef = new RepoRef("testowner", "testrepo");
		String remoteUrl = sourceRepoDir.toUri().toString();

		ClonedRepo cloned = repositoryCloner.cloneOrUpdate(repoRef, remoteUrl);
		assertThat(cloned.repo()).isEqualTo(repoRef);
		assertThat(cloned.headCommitSha()).isEqualTo(commit3.name());
		assertThat(cloned.defaultBranch()).isEqualTo("main");
		assertThat(Files.exists(cloned.localPath().resolve("README.md"))).isTrue();

		// Add commit 4 to source repo
		writeFile(sourceRepoDir, "src/main/java/NewClass.java", "package com.example;\npublic class NewClass {}");
		sourceGit.add().addFilepattern(".").call();
		RevCommit commit4 = sourceGit.commit().setMessage("feat: add new class").setAuthor("Charlie", "charlie@example.com").call();

		// Update clone
		ClonedRepo updated = repositoryCloner.cloneOrUpdate(repoRef, remoteUrl);
		assertThat(updated.headCommitSha()).isEqualTo(commit4.name());
		assertThat(Files.exists(updated.localPath().resolve("src/main/java/NewClass.java"))).isTrue();
	}

	@Test
	@DisplayName("FileIngestor ingests valid files, assigns metadata, and omits excluded files")
	void testFileIngestor() {
		RepoRef repoRef = new RepoRef("testowner", "testrepo");
		ClonedRepo cloned = repositoryCloner.cloneOrUpdate(repoRef, sourceRepoDir.toUri().toString());

		List<Document> documents = fileIngestor.ingest(cloned);

		// Expected files: README.md, docs/architecture.adoc, src/main/java/App.java, src/main/java/Helper.java, src/main/java/Utils.java
		assertThat(documents).hasSize(5);

		Map<String, Document> docByPath = documents.stream()
				.collect(Collectors.toMap(doc -> doc.metadata().path(), doc -> doc));

		// Verify README.md
		Document readmeDoc = docByPath.get("README.md");
		assertThat(readmeDoc).isNotNull();
		assertThat(readmeDoc.metadata().sourceType()).isEqualTo(SourceType.DOC);
		assertThat(readmeDoc.metadata().language()).isEqualTo("markdown");
		assertThat(readmeDoc.metadata().author()).isEqualTo("Alice");
		assertThat(readmeDoc.metadata().commitSha()).isEqualTo(commit1.name());

		// Verify docs/architecture.adoc
		Document docDoc = docByPath.get("docs/architecture.adoc");
		assertThat(docDoc).isNotNull();
		assertThat(docDoc.metadata().sourceType()).isEqualTo(SourceType.DOC);
		assertThat(docDoc.metadata().language()).isEqualTo("asciidoc");
		assertThat(docDoc.metadata().author()).isEqualTo("Bob");
		assertThat(docDoc.metadata().commitSha()).isEqualTo(commit2.name());

		// Verify App.java (modified in commit 3)
		Document appDoc = docByPath.get("src/main/java/App.java");
		assertThat(appDoc).isNotNull();
		assertThat(appDoc.metadata().sourceType()).isEqualTo(SourceType.CODE);
		assertThat(appDoc.metadata().language()).isEqualTo("java");
		assertThat(appDoc.metadata().author()).isEqualTo("Alice");
		assertThat(appDoc.metadata().commitSha()).isEqualTo(commit3.name());
		assertThat(appDoc.content()).contains("int version = 2;");

		// Verify Helper.java (added in commit 2)
		Document helperDoc = docByPath.get("src/main/java/Helper.java");
		assertThat(helperDoc).isNotNull();
		assertThat(helperDoc.metadata().commitSha()).isEqualTo(commit2.name());
		assertThat(helperDoc.metadata().author()).isEqualTo("Bob");

		// Verify excluded files are absent
		assertThat(docByPath).doesNotContainKey("package-lock.json");
		assertThat(docByPath).doesNotContainKey("app.min.js");
		assertThat(docByPath).doesNotContainKey("logo.png");
		assertThat(docByPath).doesNotContainKey("node_modules/subpkg/index.js");
	}

	@Test
	@DisplayName("CommitIngestor ingests commit history with diffs, files changed, and truncation")
	void testCommitIngestor() {
		RepoRef repoRef = new RepoRef("testowner", "testrepo");
		ClonedRepo cloned = repositoryCloner.cloneOrUpdate(repoRef, sourceRepoDir.toUri().toString());

		List<Document> commits = commitIngestor.ingest(cloned);
		assertThat(commits).hasSize(3);

		// Most recent commit is first (commit 3)
		Document latest = commits.get(0);
		assertThat(latest.metadata().sourceType()).isEqualTo(SourceType.COMMIT);
		assertThat(latest.metadata().title()).isEqualTo("feat: update app version and add utils");
		assertThat(latest.metadata().commitSha()).isEqualTo(commit3.name());
		assertThat(latest.metadata().author()).isEqualTo("Alice");
		assertThat(latest.content()).contains("feat: update app version and add utils");
		assertThat(latest.content()).contains("Files changed:");
		assertThat(latest.content()).contains("src/main/java/App.java");
		assertThat(latest.content()).contains("src/main/java/Utils.java");

		// Initial commit (commit 1) with no parent is handled
		Document initial = commits.get(2);
		assertThat(initial.metadata().title()).isEqualTo("feat: initial commit with app and readme");
		assertThat(initial.metadata().commitSha()).isEqualTo(commit1.name());
		assertThat(initial.content()).contains("Files changed:");
	}

	@Test
	@DisplayName("Commit diff is truncated when exceeding maxDiffChars")
	void testCommitDiffTruncation() {
		// Create a property instance with very small maxDiffChars = 50
		GitContextProperties smallDiffProps = new GitContextProperties(
				null,
				reposDir,
				"http://localhost:6333",
				null,
				new GitContextProperties.IngestionProperties(512, 500, 50, null, null, null, null, null, null),
				null
		);
		CommitIngestor smallDiffIngestor = new CommitIngestor(smallDiffProps, new FileFilter(smallDiffProps));

		RepoRef repoRef = new RepoRef("testowner", "testrepo");
		ClonedRepo cloned = repositoryCloner.cloneOrUpdate(repoRef, sourceRepoDir.toUri().toString());

		List<Document> commits = smallDiffIngestor.ingest(cloned);
		assertThat(commits).isNotEmpty();

		Document latest = commits.get(0);
		assertThat(latest.content()).contains("[diff truncated]");
	}

	private static void writeFile(Path base, String relativePath, String content) throws IOException {
		Path path = base.resolve(relativePath);
		Files.createDirectories(path.getParent());
		Files.writeString(path, content);
	}

	private static void writeBinaryFile(Path base, String relativePath, byte[] data) throws IOException {
		Path path = base.resolve(relativePath);
		Files.createDirectories(path.getParent());
		Files.write(path, data);
	}
}
