package io.github.tarunipriya.gitcontext.generation;

import java.util.List;

/**
 * Grounded answer with citations, sources, and execution timings.
 *
 * @param answer cleaned textual answer from the LLM
 * @param citations list of verified citations in order of first use
 * @param sources numbered sources provided in context with cited flag
 * @param model LLM model name that generated the response
 * @param promptTokens prompt tokens consumed
 * @param outputTokens completion tokens produced
 * @param thoughtsTokens tokens consumed by internal thinking/reasoning (optional)
 * @param retrievalMillis time spent in hybrid search and retrieval
 * @param generationMillis time spent calling the LLM
 * @param totalMillis total execution time in milliseconds
 * @param warnings warnings accumulated during retrieval, rerank, or citation parsing
 */
public record AnswerResult(
		String answer,
		List<Citation> citations,
		List<Source> sources,
		String model,
		int promptTokens,
		int outputTokens,
		Integer thoughtsTokens,
		long retrievalMillis,
		long generationMillis,
		long totalMillis,
		List<String> warnings
) {
	public AnswerResult {
		citations = citations != null ? List.copyOf(citations) : List.of();
		sources = sources != null ? List.copyOf(sources) : List.of();
		warnings = warnings != null ? List.copyOf(warnings) : List.of();
	}

	public AnswerResult(
			String answer,
			List<Citation> citations,
			List<Source> sources,
			String model,
			int promptTokens,
			int outputTokens,
			long retrievalMillis,
			long generationMillis,
			long totalMillis,
			List<String> warnings
	) {
		this(answer, citations, sources, model, promptTokens, outputTokens, null, retrievalMillis, generationMillis, totalMillis, warnings);
	}
}
