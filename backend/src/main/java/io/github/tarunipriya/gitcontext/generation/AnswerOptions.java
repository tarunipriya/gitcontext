package io.github.tarunipriya.gitcontext.generation;

import io.github.tarunipriya.gitcontext.retrieval.SearchMode;
import java.util.List;

/**
 * Execution parameters for answer generation.
 *
 * @param mode search mode (default: HYBRID)
 * @param excludeTests whether test files should be excluded (default: true)
 * @param rerank whether cross-encoder reranking should be used (default: false)
 * @param maxSources maximum sources to include in context (default: 8, valid range 1-12)
 * @param candidates number of candidate fused results to retrieve (default: 20)
 * @param excludePathPatterns path substrings to filter out from retrieved hits (e.g. "/demo/")
 */
public record AnswerOptions(
		SearchMode mode,
		boolean excludeTests,
		boolean rerank,
		int maxSources,
		int candidates,
		List<String> excludePathPatterns
) {
	public static final int DEFAULT_MAX_SOURCES = 8;
	public static final int DEFAULT_CANDIDATES = 20;

	public AnswerOptions {
		mode = mode != null ? mode : SearchMode.HYBRID;
		if (maxSources <= 0) {
			maxSources = DEFAULT_MAX_SOURCES;
		}
		if (candidates <= 0) {
			candidates = DEFAULT_CANDIDATES;
		}
		excludePathPatterns = (excludePathPatterns != null) ? List.copyOf(excludePathPatterns) : List.of();
	}

	public AnswerOptions(SearchMode mode, boolean excludeTests, boolean rerank, int maxSources, int candidates) {
		this(mode, excludeTests, rerank, maxSources, candidates, List.of());
	}

	public static AnswerOptions defaults() {
		return new AnswerOptions(SearchMode.HYBRID, true, false, DEFAULT_MAX_SOURCES, DEFAULT_CANDIDATES, List.of());
	}
}
