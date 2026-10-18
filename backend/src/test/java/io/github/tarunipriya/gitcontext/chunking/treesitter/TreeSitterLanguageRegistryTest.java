package io.github.tarunipriya.gitcontext.chunking.treesitter;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.treesitter.TSParser;

import static org.assertj.core.api.Assertions.assertThat;

class TreeSitterLanguageRegistryTest {

	private final TreeSitterLanguageRegistry registry = new TreeSitterLanguageRegistry();

	@Test
	@DisplayName("Supports Java, Python, JavaScript, TypeScript, and TSX aliases")
	void testSupportedLanguages() {
		assertThat(registry.supports("java")).isTrue();
		assertThat(registry.supports("JAVA")).isTrue();
		assertThat(registry.supports("python")).isTrue();
		assertThat(registry.supports("py")).isTrue();
		assertThat(registry.supports("javascript")).isTrue();
		assertThat(registry.supports("js")).isTrue();
		assertThat(registry.supports("typescript")).isTrue();
		assertThat(registry.supports("ts")).isTrue();
		assertThat(registry.supports("tsx")).isTrue();

		assertThat(registry.supports("dart")).isFalse();
		assertThat(registry.supports("rust")).isFalse();
		assertThat(registry.supports(null)).isFalse();
		assertThat(registry.supports("  ")).isFalse();
	}

	@Test
	@DisplayName("Creates valid TSParser instances configured with language grammar")
	void testCreateParser() {
		for (String lang : List.of("java", "python", "javascript", "typescript", "tsx")) {
			try (TSParser parser = registry.createParser(lang)) {
				assertThat(parser).isNotNull();
				assertThat(parser.getLanguage()).isNotNull();
			}
		}
		assertThat(registry.createParser("unsupported")).isNull();
		assertThat(registry.createParser(null)).isNull();
	}
}
