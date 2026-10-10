package io.github.tarunipriya.gitcontext.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Strongly typed configuration properties for the GitContext application under the {@code gitcontext} namespace.
 *
 * <p>Repository root resolution for {@code reposDir}:
 * When {@code reposDir} is specified as a relative path (e.g. the default {@code ./data/repos}), resolving it against
 * the JVM's current working directory directly would produce different locations depending on whether the process is launched
 * from the repository root ({@code ./data/repos}) or from the {@code backend/} subdirectory ({@code backend/data/repos}).
 * To ensure consistent, cross-platform behavior, the compact constructor dynamically traverses parent directories starting
 * from the working directory until it locates the repository root marker (e.g. {@code .git} directory or {@code AGENTS.md}
 * alongside {@code backend/}), and normalises the path relative to that root. If {@code reposDir} is already absolute, it
 * is normalised as-is.
 *
 * @param githubToken personal access token for GitHub REST API authentication (optional)
 * @param reposDir local directory path where ingested repositories are cloned (defaults to {@code ./data/repos} relative to repo root)
 * @param qdrantUrl base URL for the Qdrant vector database instance (required, defaults to {@code http://localhost:6333})
 * @param llmApiKey API key for LLM provider access (optional)
 * @param ingestion ingestion configuration for cloning, filtering, and parsing repository content
 */
@Validated
@ConfigurationProperties(prefix = "gitcontext")
public record GitContextProperties(
		String githubToken,
		@DefaultValue("./data/repos") Path reposDir,
		@NotBlank @DefaultValue("http://localhost:6333") String qdrantUrl,
		String llmApiKey,
		@Valid @DefaultValue IngestionProperties ingestion
) {

	/**
	 * Compact constructor to normalise paths and apply defaults.
	 */
	public GitContextProperties {
		reposDir = resolveToRepoRoot(reposDir != null ? reposDir : Path.of("./data/repos"));
		if (qdrantUrl == null) {
			qdrantUrl = "http://localhost:6333";
		}
		if (ingestion == null) {
			ingestion = new IngestionProperties(512, 500, 4000, null, null, null, null, null);
		}
	}

	/**
	 * Configuration properties for repository ingestion.
	 *
	 * @param maxFileSizeKb maximum file size in kilobytes eligible for ingestion (default: 512)
	 * @param maxCommits maximum number of commits to walk and ingest (default: 500)
	 * @param maxDiffChars maximum character length for git diff output before truncation (default: 4000)
	 * @param excludeDirs directory names to skip during working tree traversal
	 * @param docExtensions file extensions classified as documentation documents
	 * @param codeExtensions file extensions classified as source code documents
	 * @param excludeLockFiles filenames of lock files to skip during ingestion
	 * @param excludeMinifiedPatterns filename glob/suffix patterns for minified assets to skip
	 */
	public record IngestionProperties(
			@Min(1) @DefaultValue("512") int maxFileSizeKb,
			@Min(1) @DefaultValue("500") int maxCommits,
			@Min(1) @DefaultValue("4000") int maxDiffChars,
			@NotNull @DefaultValue({".git", "node_modules", "target", "build", "dist", "out", ".idea", ".vscode", ".venv", "venv", "__pycache__", ".gradle", ".mvn", "vendor", "coverage"}) List<String> excludeDirs,
			@NotNull @DefaultValue({"md", "rst", "txt", "adoc"}) List<String> docExtensions,
			@NotNull @DefaultValue({"java", "kt", "py", "js", "jsx", "ts", "tsx", "go", "rs", "c", "h", "cpp", "hpp", "cs", "rb", "php", "dart", "swift", "scala", "sql", "sh", "yml", "yaml", "toml", "json", "xml", "gradle"}) List<String> codeExtensions,
			@NotNull @DefaultValue({"package-lock.json", "yarn.lock", "pnpm-lock.yaml", "poetry.lock", "Cargo.lock"}) List<String> excludeLockFiles,
			@NotNull @DefaultValue({"*.min.js", "*.min.css"}) List<String> excludeMinifiedPatterns
	) {
		public IngestionProperties {
			if (maxFileSizeKb <= 0) {
				maxFileSizeKb = 512;
			}
			if (maxCommits <= 0) {
				maxCommits = 500;
			}
			if (maxDiffChars <= 0) {
				maxDiffChars = 4000;
			}
			excludeDirs = excludeDirs != null ? List.copyOf(excludeDirs) : List.of(
					".git", "node_modules", "target", "build", "dist", "out", ".idea", ".vscode", ".venv", "venv", "__pycache__", ".gradle", ".mvn", "vendor", "coverage"
			);
			docExtensions = docExtensions != null ? List.copyOf(docExtensions) : List.of(
					"md", "rst", "txt", "adoc"
			);
			codeExtensions = codeExtensions != null ? List.copyOf(codeExtensions) : List.of(
					"java", "kt", "py", "js", "jsx", "ts", "tsx", "go", "rs", "c", "h", "cpp", "hpp", "cs", "rb", "php", "dart", "swift", "scala", "sql", "sh", "yml", "yaml", "toml", "json", "xml", "gradle"
			);
			excludeLockFiles = excludeLockFiles != null ? List.copyOf(excludeLockFiles) : List.of(
					"package-lock.json", "yarn.lock", "pnpm-lock.yaml", "poetry.lock", "Cargo.lock"
			);
			excludeMinifiedPatterns = excludeMinifiedPatterns != null ? List.copyOf(excludeMinifiedPatterns) : List.of(
					"*.min.js", "*.min.css"
			);
		}
	}

	/**
	 * Returns {@code true} if a non-blank GitHub personal access token is configured.
	 *
	 * @return true if GitHub token is present and non-blank
	 */
	public boolean hasGithubToken() {
		return githubToken != null && !githubToken.isBlank();
	}

	/**
	 * Returns {@code true} if a non-blank LLM API key is configured.
	 *
	 * @return true if LLM API key is present and non-blank
	 */
	public boolean hasLlmApiKey() {
		return llmApiKey != null && !llmApiKey.isBlank();
	}

	@Override
	public String toString() {
		return "GitContextProperties[" +
				"githubToken=" + maskSecret(githubToken) + ", " +
				"reposDir=" + reposDir + ", " +
				"qdrantUrl=" + qdrantUrl + ", " +
				"llmApiKey=" + maskSecret(llmApiKey) + ", " +
				"ingestion=" + ingestion +
				"]";
	}

	private static String maskSecret(String secret) {
		if (secret == null || secret.isBlank()) {
			return "<not set>";
		}
		return "****";
	}

	private static Path resolveToRepoRoot(Path path) {
		if (path.isAbsolute()) {
			return path.normalize();
		}
		Path repoRoot = findRepoRoot();
		return repoRoot.resolve(path).normalize().toAbsolutePath();
	}

	private static Path findRepoRoot() {
		Path current = Path.of("").toAbsolutePath().normalize();
		Path candidate = current;
		while (candidate != null) {
			if (Files.isDirectory(candidate.resolve(".git")) ||
					(Files.exists(candidate.resolve("backend")) && Files.exists(candidate.resolve("AGENTS.md")))) {
				return candidate;
			}
			candidate = candidate.getParent();
		}
		return current;
	}
}
