package io.github.tarunipriya.gitcontext.evaluation.dataset;

import java.util.List;

/**
 * Immutable collection of evaluation test cases representing a named benchmark dataset.
 *
 * @param name dataset name (derived from file name without extension)
 * @param cases list of evaluation cases
 */
public record EvalDataset(
		String name,
		List<EvalCase> cases
) {

	public EvalDataset {
		name = name != null ? name : "";
		cases = cases != null ? List.copyOf(cases) : List.of();
	}

	/**
	 * Returns all evaluation cases that are marked as answerable from the repository.
	 *
	 * @return list of answerable evaluation cases
	 */
	public List<EvalCase> answerableCases() {
		return cases.stream().filter(EvalCase::answerable).toList();
	}

	/**
	 * Returns all negative/unanswerable evaluation cases testing the system's ability to refuse
	 * or indicate missing context without hallucinating.
	 *
	 * @return list of unanswerable evaluation cases
	 */
	public List<EvalCase> unanswerableCases() {
		return cases.stream().filter(c -> !c.answerable()).toList();
	}
}
