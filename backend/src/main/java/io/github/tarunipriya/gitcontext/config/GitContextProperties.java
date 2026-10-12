package io.github.tarunipriya.gitcontext.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Parameter;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
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
 * @param github GitHub REST API configuration for fetching pull requests and issues
 * @param chunking chunking configuration for AST and fallback window chunkers
 */
@Validated
@ConfigurationProperties(prefix = "gitcontext")
public record GitContextProperties(
		String githubToken,
		@DefaultValue("./data/repos") Path reposDir,
		@NotBlank @DefaultValue("http://localhost:6333") String qdrantUrl,
		String llmApiKey,
		@Valid @DefaultValue IngestionProperties ingestion,
		@Valid @DefaultValue GitHubProperties github,
		@Valid @DefaultValue ChunkingProperties chunking
) {

	/**
	 * Compact constructor to normalise paths and apply defaults.
	 */
	@ConstructorBinding
	public GitContextProperties {
		reposDir = resolveToRepoRoot(reposDir != null ? reposDir : Path.of("./data/repos"));
		if (qdrantUrl == null) {
			qdrantUrl = "http://localhost:6333";
		}
		if (ingestion == null) {
			ingestion = new IngestionProperties(0, 0, 0, null, null, null, null, null);
		}
		if (github == null) {
			github = new GitHubProperties(null, 0, 0, 0, null, -1);
		}
		if (chunking == null) {
			chunking = new ChunkingProperties(512, 40, 5, 60);
		}
	}

	/**
	 * Overloaded constructor preserving compatibility for callers without chunking properties.
	 */
	public GitContextProperties(
			String githubToken,
			Path reposDir,
			String qdrantUrl,
			String llmApiKey,
			IngestionProperties ingestion,
			GitHubProperties github
	) {
		this(githubToken, reposDir, qdrantUrl, llmApiKey, ingestion, github, null);
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
		private static final Map<String, List<String>> DEFAULT_LISTS = loadDefaultLists();
		private static final int DEFAULT_MAX_FILE_SIZE_KB = loadDefaultInt("maxFileSizeKb", 512);
		private static final int DEFAULT_MAX_COMMITS = loadDefaultInt("maxCommits", 500);
		private static final int DEFAULT_MAX_DIFF_CHARS = loadDefaultInt("maxDiffChars", 4000);

		public IngestionProperties {
			if (maxFileSizeKb <= 0) {
				maxFileSizeKb = DEFAULT_MAX_FILE_SIZE_KB;
			}
			if (maxCommits <= 0) {
				maxCommits = DEFAULT_MAX_COMMITS;
			}
			if (maxDiffChars <= 0) {
				maxDiffChars = DEFAULT_MAX_DIFF_CHARS;
			}
			excludeDirs = excludeDirs != null ? List.copyOf(excludeDirs) : DEFAULT_LISTS.getOrDefault("excludeDirs", List.of());
			docExtensions = docExtensions != null ? List.copyOf(docExtensions) : DEFAULT_LISTS.getOrDefault("docExtensions", List.of());
			codeExtensions = codeExtensions != null ? List.copyOf(codeExtensions) : DEFAULT_LISTS.getOrDefault("codeExtensions", List.of());
			excludeLockFiles = excludeLockFiles != null ? List.copyOf(excludeLockFiles) : DEFAULT_LISTS.getOrDefault("excludeLockFiles", List.of());
			excludeMinifiedPatterns = excludeMinifiedPatterns != null ? List.copyOf(excludeMinifiedPatterns) : DEFAULT_LISTS.getOrDefault("excludeMinifiedPatterns", List.of());
		}

		private static Map<String, List<String>> loadDefaultLists() {
			Map<String, List<String>> map = new HashMap<>();
			for (RecordComponent rc : IngestionProperties.class.getRecordComponents()) {
				if (List.class.isAssignableFrom(rc.getType())) {
					DefaultValue dv = findDefaultValue(rc.getName());
					if (dv != null) {
						map.put(rc.getName(), List.of(dv.value()));
					}
				}
			}
			return Map.copyOf(map);
		}

		private static int loadDefaultInt(String propertyName, int fallback) {
			DefaultValue dv = findDefaultValue(propertyName);
			if (dv != null && dv.value().length > 0) {
				try {
					return Integer.parseInt(dv.value()[0]);
				} catch (NumberFormatException ignored) {
				}
			}
			return fallback;
		}

		private static DefaultValue findDefaultValue(String propertyName) {
			try {
				Field field = IngestionProperties.class.getDeclaredField(propertyName);
				DefaultValue dv = field.getAnnotation(DefaultValue.class);
				if (dv != null) {
					return dv;
				}
			} catch (NoSuchFieldException ignored) {
			}
			for (Constructor<?> ctor : IngestionProperties.class.getDeclaredConstructors()) {
				for (Parameter param : ctor.getParameters()) {
					if (propertyName.equals(param.getName())) {
						DefaultValue dv = param.getAnnotation(DefaultValue.class);
						if (dv != null) {
							return dv;
						}
					}
				}
			}
			return null;
		}
	}

	/**
	 * Configuration properties for GitHub REST API client and ingestion under {@code gitcontext.github}.
	 *
	 * @param apiBaseUrl base URL for the GitHub REST API (default: https://api.github.com)
	 * @param maxPullRequests maximum number of pull requests to ingest (default: 200)
	 * @param maxIssues maximum number of issues to ingest (default: 200)
	 * @param maxCommentsPerItem maximum number of comments to fetch per issue or pull request (default: 50)
	 * @param incidentLabels label names that identify an incident or outage issue (default: incident, bug, postmortem, outage)
	 * @param rateLimitWarnThreshold remaining rate limit threshold below which warning logs are emitted (default: 50)
	 */
	public record GitHubProperties(
			@NotBlank @DefaultValue("https://api.github.com") String apiBaseUrl,
			@Min(1) @DefaultValue("200") int maxPullRequests,
			@Min(1) @DefaultValue("200") int maxIssues,
			@Min(1) @DefaultValue("50") int maxCommentsPerItem,
			@NotNull @DefaultValue({"incident", "bug", "postmortem", "outage"}) List<String> incidentLabels,
			@Min(0) @DefaultValue("50") int rateLimitWarnThreshold
	) {
		private static final Map<String, List<String>> DEFAULT_LISTS = loadDefaultLists();
		private static final String DEFAULT_API_BASE_URL = loadDefaultString("apiBaseUrl", "https://api.github.com");
		private static final int DEFAULT_MAX_PULL_REQUESTS = loadDefaultInt("maxPullRequests", 200);
		private static final int DEFAULT_MAX_ISSUES = loadDefaultInt("maxIssues", 200);
		private static final int DEFAULT_MAX_COMMENTS_PER_ITEM = loadDefaultInt("maxCommentsPerItem", 50);
		private static final int DEFAULT_RATE_LIMIT_WARN_THRESHOLD = loadDefaultInt("rateLimitWarnThreshold", 50);

		public GitHubProperties {
			if (apiBaseUrl == null || apiBaseUrl.isBlank()) {
				apiBaseUrl = DEFAULT_API_BASE_URL;
			}
			if (maxPullRequests <= 0) {
				maxPullRequests = DEFAULT_MAX_PULL_REQUESTS;
			}
			if (maxIssues <= 0) {
				maxIssues = DEFAULT_MAX_ISSUES;
			}
			if (maxCommentsPerItem <= 0) {
				maxCommentsPerItem = DEFAULT_MAX_COMMENTS_PER_ITEM;
			}
			if (rateLimitWarnThreshold < 0) {
				rateLimitWarnThreshold = DEFAULT_RATE_LIMIT_WARN_THRESHOLD;
			}
			incidentLabels = incidentLabels != null ? List.copyOf(incidentLabels) : DEFAULT_LISTS.getOrDefault("incidentLabels", List.of());
		}

		private static Map<String, List<String>> loadDefaultLists() {
			Map<String, List<String>> map = new HashMap<>();
			for (RecordComponent rc : GitHubProperties.class.getRecordComponents()) {
				if (List.class.isAssignableFrom(rc.getType())) {
					DefaultValue dv = findDefaultValue(rc.getName());
					if (dv != null) {
						map.put(rc.getName(), List.of(dv.value()));
					}
				}
			}
			return Map.copyOf(map);
		}

		private static int loadDefaultInt(String propertyName, int fallback) {
			DefaultValue dv = findDefaultValue(propertyName);
			if (dv != null && dv.value().length > 0) {
				try {
					return Integer.parseInt(dv.value()[0]);
				} catch (NumberFormatException ignored) {
				}
			}
			return fallback;
		}

		private static String loadDefaultString(String propertyName, String fallback) {
			DefaultValue dv = findDefaultValue(propertyName);
			if (dv != null && dv.value().length > 0) {
				return dv.value()[0];
			}
			return fallback;
		}

		private static DefaultValue findDefaultValue(String propertyName) {
			try {
				Field field = GitHubProperties.class.getDeclaredField(propertyName);
				DefaultValue dv = field.getAnnotation(DefaultValue.class);
				if (dv != null) {
					return dv;
				}
			} catch (NoSuchFieldException ignored) {
			}
			for (Constructor<?> ctor : GitHubProperties.class.getDeclaredConstructors()) {
				for (Parameter param : ctor.getParameters()) {
					if (propertyName.equals(param.getName())) {
						DefaultValue dv = param.getAnnotation(DefaultValue.class);
						if (dv != null) {
							return dv;
						}
					}
				}
			}
			return null;
		}
	}

	/**
	 * Configuration properties for chunking strategies under {@code gitcontext.chunking}.
	 *
	 * @param maxChunkTokens maximum token count per chunk before splitting (default: 512)
	 * @param minChunkTokens minimum token threshold below which sections are merged (default: 40)
	 * @param overlapLines number of overlapping lines between consecutive chunk windows (default: 5)
	 * @param fallbackWindowLines line window size for the fallback line chunker (default: 60)
	 */
	public record ChunkingProperties(
			@Min(1) @DefaultValue("512") int maxChunkTokens,
			@Min(1) @DefaultValue("40") int minChunkTokens,
			@Min(0) @DefaultValue("5") int overlapLines,
			@Min(1) @DefaultValue("60") int fallbackWindowLines
	) {
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
				"ingestion=" + ingestion + ", " +
				"github=" + github + ", " +
				"chunking=" + chunking +
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
