package io.github.tarunipriya.gitcontext.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.github.tarunipriya.gitcontext.generation.AnswerResult;
import java.util.List;

/**
 * API response payload containing the generated answer, verified citations, and timings.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AskResponse(
		String question,
		String answer,
		List<CitationResponse> citations,
		List<SourceResponse> sources,
		String model,
		int promptTokens,
		int outputTokens,
		Integer thoughtsTokens,
		long retrievalMillis,
		long generationMillis,
		long totalMillis,
		List<String> warnings
) {
	public AskResponse(
			String question,
			String answer,
			List<CitationResponse> citations,
			List<SourceResponse> sources,
			String model,
			int promptTokens,
			int outputTokens,
			long retrievalMillis,
			long generationMillis,
			long totalMillis,
			List<String> warnings
	) {
		this(question, answer, citations, sources, model, promptTokens, outputTokens, null, retrievalMillis, generationMillis, totalMillis, warnings);
	}

	public static AskResponse from(String question, AnswerResult result) {
		List<CitationResponse> citations = result.citations().stream()
				.map(CitationResponse::from)
				.toList();
		List<SourceResponse> sources = result.sources().stream()
				.map(SourceResponse::from)
				.toList();
		return new AskResponse(
				question,
				result.answer(),
				citations,
				sources,
				result.model(),
				result.promptTokens(),
				result.outputTokens(),
				result.thoughtsTokens(),
				result.retrievalMillis(),
				result.generationMillis(),
				result.totalMillis(),
				result.warnings()
		);
	}
}
