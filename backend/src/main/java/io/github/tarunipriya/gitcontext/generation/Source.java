package io.github.tarunipriya.gitcontext.generation;

import io.github.tarunipriya.gitcontext.indexing.SearchHit;

/**
 * Numbered source document formatted for context injection into the prompt.
 *
 * @param number 1-based source number
 * @param hit search hit containing the chunk metadata and content
 * @param cited whether this source was cited in the generated answer
 */
public record Source(
		int number,
		SearchHit hit,
		boolean cited
) {
	public Source withCited(boolean cited) {
		return new Source(number, hit, cited);
	}
}
