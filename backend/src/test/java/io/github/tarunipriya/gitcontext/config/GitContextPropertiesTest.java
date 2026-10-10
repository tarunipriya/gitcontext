package io.github.tarunipriya.gitcontext.config;

import java.nio.file.Path;
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
		});
	}

	@Test
	@DisplayName("Custom property overrides bind successfully")
	void testCustomProperties() {
		runner.withPropertyValues(
				"gitcontext.github-token=ghp_testToken12345",
				"gitcontext.repos-dir=./custom/repos",
				"gitcontext.qdrant-url=http://qdrant.internal:6333",
				"gitcontext.llm-api-key=sk_testKey67890"
		).run(context -> {
			assertThat(context).hasNotFailed();
			GitContextProperties props = context.getBean(GitContextProperties.class);

			assertThat(props.githubToken()).isEqualTo("ghp_testToken12345");
			assertThat(props.qdrantUrl()).isEqualTo("http://qdrant.internal:6333");
			assertThat(props.llmApiKey()).isEqualTo("sk_testKey67890");
			assertThat(props.reposDir().toString().replace('\\', '/')).endsWith("custom/repos");
			assertThat(props.hasGithubToken()).isTrue();
			assertThat(props.hasLlmApiKey()).isTrue();
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
				"sk_superSecretLlmKey"
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
				""
		);

		String unconfiguredOutput = withoutSecrets.toString();
		assertThat(unconfiguredOutput).contains("githubToken=<not set>");
		assertThat(unconfiguredOutput).contains("llmApiKey=<not set>");
	}
}
