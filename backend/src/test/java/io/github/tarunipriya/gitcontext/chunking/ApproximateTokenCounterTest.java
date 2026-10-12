package io.github.tarunipriya.gitcontext.chunking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApproximateTokenCounterTest {

	private final ApproximateTokenCounter counter = new ApproximateTokenCounter();

	@Test
	@DisplayName("Empty string returns zero tokens")
	void testEmptyString() {
		assertThat(counter.countTokens("")).isZero();
		assertThat(counter.countTokens(null)).isZero();
	}

	@Test
	@DisplayName("Short string of up to 4 characters counts as 1 token")
	void testShortString() {
		assertThat(counter.countTokens("a")).isEqualTo(1);
		assertThat(counter.countTokens("ab")).isEqualTo(1);
		assertThat(counter.countTokens("abc")).isEqualTo(1);
		assertThat(counter.countTokens("abcd")).isEqualTo(1);
	}

	@Test
	@DisplayName("Character counts round up to the next integer token")
	void testRoundingUp() {
		// 5 chars -> ceil(5 / 4.0) = 2
		assertThat(counter.countTokens("abcde")).isEqualTo(2);
		// 8 chars -> ceil(8 / 4.0) = 2
		assertThat(counter.countTokens("12345678")).isEqualTo(2);
		// 9 chars -> ceil(9 / 4.0) = 3
		assertThat(counter.countTokens("123456789")).isEqualTo(3);
	}
}
