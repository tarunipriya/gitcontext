package io.github.tarunipriya.gitcontext.chunking;

import org.springframework.stereotype.Component;

/**
 * Fast character-based token estimator using the standard rule of thumb: {@code ceil(characters / 4.0)}.
 *
 * <p>This implementation provides an efficient, dependency-free token estimation suitable for chunking calculations.
 * Explain this is an approximation: character-to-token ratios typically average around 3.5 to 4.5 characters per token
 * for typical code and natural language text in subword tokenizers. A real tokenizer (e.g. tiktoken, Hugging Face BPE,
 * or JTokkit) can be swapped in later by implementing {@link TokenCounter}.
 */
@Component
public class ApproximateTokenCounter implements TokenCounter {

	@Override
	public int countTokens(String text) {
		if (text == null || text.isEmpty()) {
			return 0;
		}
		return (int) Math.ceil(text.length() / 4.0);
	}
}
