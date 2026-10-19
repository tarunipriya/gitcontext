package io.github.tarunipriya.gitcontext.config;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class GitContextPropertiesTest {

	@Configuration(proxyBeanMethods = false)
	@EnableConfigurationProperties(GitContextProperties.class)
	static class TestConfiguration {
	}

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
			.withUserConfiguration(TestConfiguration.class);

	@Test
	@DisplayName("Default properties bind with expected defaults and normalised absolute path")
	void testDefaultProperties() {
		runner.run(context -> {
			assertThat(context).hasNotFailed();
			assertThat(context).hasSingleBean(GitContextProperties.class);

			GitContextProperties props = context.getBean(GitContextProperties.class);
			assertThat(props.qdrantUrl()).isEqualTo("http://localhost:6333");
			assertThat(props.reposDir()).isAbsolute();
			assertThat(props.reposDir().toString().replace('\\', '/')).endsWith("data/repos");
			assertThat(props.githubToken()).isNull();
			assertThat(props.llmApiKey()).isNull();
			assertThat(props.hasGithubToken()).isFalse();
			assertThat(props.hasLlmApiKey()).isFalse();

			// Ingestion defaults
			GitContextProperties.IngestionProperties ingestion = props.ingestion();
			assertThat(ingestion).isNotNull();
			assertThat(ingestion.maxFileSizeKb()).isEqualTo(512);
			assertThat(ingestion.maxCommits()).isEqualTo(500);
			assertThat(ingestion.maxDiffChars()).isEqualTo(4000);
			assertThat(ingestion.excludeDirs()).contains(".git", "node_modules", "target", "build", "dist", ".idea");
			assertThat(ingestion.docExtensions()).contains("md", "rst", "txt", "adoc");
			assertThat(ingestion.codeExtensions()).contains("java", "py", "ts", "go", "rs", "cpp");
			assertThat(ingestion.excludeLockFiles()).contains("package-lock.json", "yarn.lock", "poetry.lock");
			assertThat(ingestion.excludeMinifiedPatterns()).contains("*.min.js", "*.min.css");

			// GitHub defaults
			GitContextProperties.GitHubProperties github = props.github();
			assertThat(github).isNotNull();
			assertThat(github.apiBaseUrl()).isEqualTo("https://api.github.com");
			assertThat(github.maxPullRequests()).isEqualTo(200);
			assertThat(github.maxIssues()).isEqualTo(200);
			assertThat(github.maxCommentsPerItem()).isEqualTo(50);
			assertThat(github.incidentLabels()).containsExactly("incident", "bug", "postmortem", "outage");
			assertThat(github.rateLimitWarnThreshold()).isEqualTo(50);

			// Chunking defaults
			GitContextProperties.ChunkingProperties chunking = props.chunking();
			assertThat(chunking).isNotNull();
			assertThat(chunking.maxChunkTokens()).isEqualTo(512);
			assertThat(chunking.minChunkTokens()).isEqualTo(40);
			assertThat(chunking.overlapLines()).isEqualTo(5);
			assertThat(chunking.fallbackWindowLines()).isEqualTo(60);

			// Embedding defaults
			GitContextProperties.EmbeddingProperties embedding = props.embedding();
			assertThat(embedding).isNotNull();
			assertThat(embedding.batchSize()).isEqualTo(32);
			assertThat(embedding.queryPrefix()).isEqualTo("Represent this sentence for searching relevant passages: ");

			// Qdrant defaults
			GitContextProperties.QdrantProperties qdrant = props.qdrant();
			assertThat(qdrant).isNotNull();
			assertThat(qdrant.url()).isEqualTo("http://localhost:6333");
			assertThat(qdrant.collection()).isEqualTo("gitcontext_chunks");
			assertThat(qdrant.upsertBatchSize()).isEqualTo(128);
			assertThat(qdrant.timeoutSeconds()).isEqualTo(30);
		});
	}

	@Test
	@DisplayName("Custom property overrides bind successfully")
	void testCustomProperties() {
		runner.withPropertyValues(
				"gitcontext.github-token=ghp_testToken12345",
				"gitcontext.repos-dir=./custom/repos",
				"gitcontext.qdrant-url=http://qdrant.internal:6333",
				"gitcontext.llm-api-key=sk_testKey67890",
				"gitcontext.ingestion.max-file-size-kb=1024",
				"gitcontext.ingestion.max-commits=100",
				"gitcontext.ingestion.max-diff-chars=2000",
				"gitcontext.github.api-base-url=https://github-enterprise.internal/api/v3",
				"gitcontext.github.max-pull-requests=50",
				"gitcontext.github.max-issues=75",
				"gitcontext.github.max-comments-per-item=20",
				"gitcontext.github.incident-labels=sev-1,incident,critical",
				"gitcontext.github.rate-limit-warn-threshold=100",
				"gitcontext.chunking.max-chunk-tokens=256",
				"gitcontext.chunking.min-chunk-tokens=20",
				"gitcontext.chunking.overlap-lines=3",
				"gitcontext.chunking.fallback-window-lines=40",
				"gitcontext.embedding.batch-size=16",
				"gitcontext.embedding.query-prefix=Represent query:",
				"gitcontext.qdrant.collection=custom_chunks",
				"gitcontext.qdrant.upsert-batch-size=64",
				"gitcontext.qdrant.timeout-seconds=45"
		).run(context -> {
			assertThat(context).hasNotFailed();
			GitContextProperties props = context.getBean(GitContextProperties.class);

			assertThat(props.githubToken()).isEqualTo("ghp_testToken12345");
			assertThat(props.qdrantUrl()).isEqualTo("http://qdrant.internal:6333");
			assertThat(props.llmApiKey()).isEqualTo("sk_testKey67890");
			assertThat(props.reposDir().toString().replace('\\', '/')).endsWith("custom/repos");
			assertThat(props.hasGithubToken()).isTrue();
			assertThat(props.hasLlmApiKey()).isTrue();

			assertThat(props.ingestion().maxFileSizeKb()).isEqualTo(1024);
			assertThat(props.ingestion().maxCommits()).isEqualTo(100);
			assertThat(props.ingestion().maxDiffChars()).isEqualTo(2000);

			assertThat(props.github().apiBaseUrl()).isEqualTo("https://github-enterprise.internal/api/v3");
			assertThat(props.github().maxPullRequests()).isEqualTo(50);
			assertThat(props.github().maxIssues()).isEqualTo(75);
			assertThat(props.github().maxCommentsPerItem()).isEqualTo(20);
			assertThat(props.github().incidentLabels()).containsExactly("sev-1", "incident", "critical");
			assertThat(props.github().rateLimitWarnThreshold()).isEqualTo(100);

			assertThat(props.chunking().maxChunkTokens()).isEqualTo(256);
			assertThat(props.chunking().minChunkTokens()).isEqualTo(20);
			assertThat(props.chunking().overlapLines()).isEqualTo(3);
			assertThat(props.chunking().fallbackWindowLines()).isEqualTo(40);

			assertThat(props.embedding().batchSize()).isEqualTo(16);
			assertThat(props.embedding().queryPrefix()).isEqualTo("Represent query:");

			assertThat(props.qdrant().url()).isEqualTo("http://qdrant.internal:6333");
			assertThat(props.qdrant().collection()).isEqualTo("custom_chunks");
			assertThat(props.qdrant().upsertBatchSize()).isEqualTo(64);
			assertThat(props.qdrant().timeoutSeconds()).isEqualTo(45);
		});
	}

	@Test
	@DisplayName("Blank qdrantUrl fails validation during binding")
	void testBlankQdrantUrlFailsValidation() {
		runner.withPropertyValues("gitcontext.qdrant-url=")
				.run(context -> assertThat(context).hasFailed());

		runner.withPropertyValues("gitcontext.qdrant-url=   ")
				.run(context -> assertThat(context).hasFailed());
	}

	@Test
	@DisplayName("toString() masks secret tokens and handles unconfigured tokens")
	void testToStringMasksSecrets() {
		GitContextProperties withSecrets = new GitContextProperties(
				"ghp_superSecretToken",
				Path.of("./data/repos"),
				"http://localhost:6333",
				"sk_superSecretLlmKey",
				null,
				null,
				null
		);

		String stringOutput = withSecrets.toString();
		assertThat(stringOutput).doesNotContain("ghp_superSecretToken");
		assertThat(stringOutput).doesNotContain("sk_superSecretLlmKey");
		assertThat(stringOutput).contains("githubToken=****");
		assertThat(stringOutput).contains("llmApiKey=****");

		GitContextProperties withoutSecrets = new GitContextProperties(
				null,
				Path.of("./data/repos"),
				"http://localhost:6333",
				"",
				null,
				null,
				null
		);

		String unconfiguredOutput = withoutSecrets.toString();
		assertThat(unconfiguredOutput).contains("githubToken=<not set>");
		assertThat(unconfiguredOutput).contains("llmApiKey=<not set>");
	}
}
