package io.github.tarunipriya.gitcontext.config;

import jakarta.validation.constraints.NotBlank;
import java.nio.file.Files;
import java.nio.file.Path;
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
 */
@Validated
@ConfigurationProperties(prefix = "gitcontext")
public record GitContextProperties(
		String githubToken,
		@DefaultValue("./data/repos") Path reposDir,
		@NotBlank @DefaultValue("http://localhost:6333") String qdrantUrl,
		String llmApiKey
) {

	/**
	 * Compact constructor to normalise paths and apply defaults.
	 */
	public GitContextProperties {
		reposDir = resolveToRepoRoot(reposDir != null ? reposDir : Path.of("./data/repos"));
		if (qdrantUrl == null) {
			qdrantUrl = "http://localhost:6333";
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
				"llmApiKey=" + maskSecret(llmApiKey) +
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
