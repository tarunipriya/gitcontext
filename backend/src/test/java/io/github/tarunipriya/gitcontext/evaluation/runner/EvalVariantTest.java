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
	@DisplayName("Parses base variant names case-insensitively with expected search mode and rerank flag")
	void testParseCaseInsensitive(String input, String expectedLabel, SearchMode expectedMode, boolean expectedRerank) {
		EvalVariant parsed = EvalVariant.parse(input);
		assertThat(parsed.label()).isEqualTo(expectedLabel);
		assertThat(parsed.mode().mode()).isEqualTo(expectedMode);
		assertThat(parsed.mode().rerank()).isEqualTo(expectedRerank);
		assertThat(parsed.keywordWeight()).isNull();
		assertThat(parsed.toString()).isEqualTo(expectedLabel);
	}

	@ParameterizedTest
	@CsvSource({
			"HYBRID:kw=0.5, HYBRID:kw=0.5, HYBRID, false, 0.5",
			"hybrid:kw=0.75, HYBRID:kw=0.75, HYBRID, false, 0.75",
			"HYBRID:kw=0, HYBRID:kw=0, HYBRID, false, 0.0",
			"HYBRID:kw=0.0, HYBRID:kw=0.0, HYBRID, false, 0.0",
			"HYBRID_RERANK:kw=0.5, HYBRID_RERANK:kw=0.5, HYBRID_RERANK, true, 0.5",
			"hybrid_rerank:kw=0.25, HYBRID_RERANK:kw=0.25, HYBRID_RERANK, true, 0.25"
	})
	@DisplayName("Parses :kw= suffix for HYBRID and HYBRID_RERANK with expected normalized label and keywordWeight")
	void testParseWithKwSuffix(
			String input,
			String expectedLabel,
			EvalMode expectedMode,
			boolean expectedRerank,
			double expectedWeight
	) {
		EvalVariant parsed = EvalVariant.parse(input);
		assertThat(parsed.label()).isEqualTo(expectedLabel);
		assertThat(parsed.mode()).isEqualTo(expectedMode);
		assertThat(parsed.mode().mode()).isEqualTo(expectedMode.mode());
		assertThat(parsed.mode().rerank()).isEqualTo(expectedRerank);
		assertThat(parsed.keywordWeight()).isEqualTo(expectedWeight);
		assertThat(parsed.toString()).isEqualTo(expectedLabel);
	}

	@Test
	@DisplayName("Suffix on VECTOR or KEYWORD throws IllegalArgumentException")
	void testInvalidSuffixOnNonHybridThrows() {
		assertThatThrownBy(() -> EvalVariant.parse("VECTOR:kw=0.5"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Suffix ':kw=' is only supported on HYBRID and HYBRID_RERANK");

		assertThatThrownBy(() -> EvalVariant.parse("KEYWORD:kw=0.5"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Suffix ':kw=' is only supported on HYBRID and HYBRID_RERANK");

		assertThatThrownBy(() -> EvalVariant.parse("vector:kw=0.5"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Suffix ':kw=' is only supported on HYBRID and HYBRID_RERANK");
	}

	@Test
	@DisplayName("Bad numbers in :kw= suffix throw IllegalArgumentException")
	void testBadNumbersThrow() {
		assertThatThrownBy(() -> EvalVariant.parse("HYBRID:kw=abc"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Invalid keyword weight number");

		assertThatThrownBy(() -> EvalVariant.parse("HYBRID:kw="))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Keyword weight number is missing");

		assertThatThrownBy(() -> EvalVariant.parse("HYBRID:kw=NaN"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Keyword weight must be finite and >= 0");

		assertThatThrownBy(() -> EvalVariant.parse("HYBRID:kw=Infinity"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Keyword weight must be finite and >= 0");

		assertThatThrownBy(() -> EvalVariant.parse("HYBRID:other=0.5"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Invalid variant suffix");
	}

	@Test
	@DisplayName("Negative weight in :kw= suffix throws IllegalArgumentException")
	void testNegativeWeightThrows() {
		assertThatThrownBy(() -> EvalVariant.parse("HYBRID:kw=-0.5"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Keyword weight must be finite and >= 0");

		assertThatThrownBy(() -> EvalVariant.parse("HYBRID_RERANK:kw=-1.0"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Keyword weight must be finite and >= 0");
	}

	@Test
	@DisplayName("Unknown variant name throws IllegalArgumentException listing valid formats")
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
