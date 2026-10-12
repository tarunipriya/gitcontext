package io.github.tarunipriya.gitcontext.chunking;

/**
 * Strategy interface for estimating or calculating token counts for text slices.
 */
public interface TokenCounter {

	/**
	 * Counts or estimates the number of tokens in the given text.
	 *
	 * @param text the input string to evaluate (null or empty returns 0)
	 * @return non-negative token count
	 */
	int countTokens(String text);
}
