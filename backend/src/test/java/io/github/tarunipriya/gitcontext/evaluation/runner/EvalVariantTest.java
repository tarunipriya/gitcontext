package io.github.tarunipriya.gitcontext.evaluation.runner;

import io.github.tarunipriya.gitcontext.retrieval.SearchMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EvalVariantTest {

	@ParameterizedTest
	@CsvSource({
			"vector, VECTOR, VECTOR, false",
			"VECTOR, VECTOR, VECTOR, false",
			"keyword, KEYWORD, KEYWORD, false",
			"KEYWORD, KEYWORD, KEYWORD, false",
			"hybrid, HYBRID, HYBRID, false",
			"HYBRID, HYBRID, HYBRID, false",
			"hybrid_rerank, HYBRID_RERANK, HYBRID, true",
			"HYBRID_RERANK, HYBRID_RERANK, HYBRID, true"
	})
	@DisplayName("Parses variant names case-insensitively with expected search mode and rerank flag")
	void testParseCaseInsensitive(String input, EvalVariant expectedVariant, SearchMode expectedMode, boolean expectedRerank) {
		EvalVariant parsed = EvalVariant.parse(input);
		assertThat(parsed).isEqualTo(expectedVariant);
		assertThat(parsed.mode()).isEqualTo(expectedMode);
		assertThat(parsed.rerank()).isEqualTo(expectedRerank);
	}

	@Test
	@DisplayName("Unknown variant name throws IllegalArgumentException listing valid values")
	void testUnknownVariantThrows() {
		assertThatThrownBy(() -> EvalVariant.parse("UNKNOWN_VARIANT"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Unknown evaluation variant")
				.hasMessageContaining("VECTOR")
				.hasMessageContaining("KEYWORD")
				.hasMessageContaining("HYBRID")
				.hasMessageContaining("HYBRID_RERANK");
	}

	@Test
	@DisplayName("Null or blank variant name throws IllegalArgumentException")
	void testNullOrBlankThrows() {
		assertThatThrownBy(() -> EvalVariant.parse(null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Evaluation variant must not be null or blank");

		assertThatThrownBy(() -> EvalVariant.parse("   "))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Evaluation variant must not be null or blank");
	}
}
