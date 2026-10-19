package io.github.tarunipriya.gitcontext.retrieval.keyword;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CodeAnalyzerTest {

	private CodeAnalyzer analyzer;

	@BeforeEach
	void setUp() {
		analyzer = new CodeAnalyzer();
	}

	@AfterEach
	void tearDown() {
		if (analyzer != null) {
			analyzer.close();
		}
	}

	private List<String> tokenize(String text) {
		List<String> tokens = new ArrayList<>();
		try (TokenStream stream = analyzer.tokenStream("testField", text)) {
			CharTermAttribute termAtt = stream.addAttribute(CharTermAttribute.class);
			stream.reset();
			while (stream.incrementToken()) {
				tokens.add(termAtt.toString());
			}
			stream.end();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		return tokens;
	}

	@Test
	@DisplayName("camelCase identifier splits into parts while preserving original token")
	void tokenize_camelCase_splitsAndPreservesOriginal() {
		List<String> tokens = tokenize("checkRateLimit");
		// "checkRateLimit" -> checkratelimit, check, rate, limit
		assertThat(tokens).containsExactly("checkratelimit", "check", "rate", "limit");
	}

	@Test
	@DisplayName("kebab-case identifier with PascalCase splits and preserves parts")
	void tokenize_kebabCaseWithPascal_splitsAndPreserves() {
		List<String> tokens = tokenize("X-RateLimit-Remaining");
		// "X-RateLimit-Remaining" -> x, ratelimit, rate, limit, remaining
		assertThat(tokens).containsExactly("x", "ratelimit", "rate", "limit", "remaining");
	}

	@Test
	@DisplayName("snake_case identifier splits into parts and preserves original token")
	void tokenize_snakeCase_splitsAndPreserves() {
		List<String> tokens = tokenize("max_file_size_kb");
		// "max_file_size_kb" -> max, file, size, kb, max_file_size_kb (or in order)
		assertThat(tokens).contains("max", "file", "size", "kb", "max_file_size_kb");
	}

	@Test
	@DisplayName("English minimal stemming reduces plurals e.g. limits -> limit")
	void tokenize_stemming_reducesPluralLimits() {
		List<String> tokens = tokenize("limits");
		assertThat(tokens).containsExactly("limit");
	}

	@Test
	@DisplayName("English stop words are removed while content words remain")
	void tokenize_stopWords_removed() {
		List<String> tokens = tokenize("the quick check of the rate limits in a repository");
		assertThat(tokens).doesNotContain("the", "of", "in", "a");
		assertThat(tokens).contains("quick", "check", "rate", "limit", "repository");
	}
}
