package io.github.tarunipriya.gitcontext.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class HashingTest {

	@Test
	@DisplayName("SHA-256 standard test vectors compute correctly")
	void testSha256StandardVectors() {
		// Empty string SHA-256
		assertThat(Hashing.sha256("")).isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");

		// Known test vector "hello world"
		assertThat(Hashing.sha256("hello world")).isEqualTo("b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9");
	}

	@Test
	@DisplayName("Null input to sha256 throws NullPointerException")
	void testNullInputThrows() {
		assertThatNullPointerException()
				.isThrownBy(() -> Hashing.sha256(null));
	}
}
