package io.github.tarunipriya.gitcontext.evaluation.answer;

import java.util.List;

/**
 * Impartial LLM judge verdict assessing expected fact coverage and groundedness against cited sources.
 *
 * @param factsSupported list of booleans indicating whether each expected fact is stated in the answer
 * @param grounded whether all claims made in the answer are supported by cited sources
 * @param unsupportedClaims list of statements in the answer not supported by cited sources
 */
public record JudgeVerdict(
		List<Boolean> factsSupported,
		boolean grounded,
		List<String> unsupportedClaims
) {
	public JudgeVerdict {
		factsSupported = factsSupported != null ? List.copyOf(factsSupported) : List.of();
		unsupportedClaims = unsupportedClaims != null ? List.copyOf(unsupportedClaims) : List.of();
	}
}
