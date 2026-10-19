package io.github.tarunipriya.gitcontext.generation;

import io.github.tarunipriya.gitcontext.config.GitContextProperties;
import io.github.tarunipriya.gitcontext.model.RepoRef;
import io.github.tarunipriya.gitcontext.retrieval.HybridSearchHit;
import io.github.tarunipriya.gitcontext.retrieval.HybridSearchResult;
import io.github.tarunipriya.gitcontext.retrieval.HybridSearchService;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Coordinates grounded answer generation by executing hybrid retrieval, composing
 * bounded context with overlap suppression, generating answer via LLM, and verifying citations.
 */
@Service
public class AnswerService {

	private static final Logger log = LoggerFactory.getLogger(AnswerService.class);
	public static final String NOT_FOUND_ANSWER = "I couldn't find this in the indexed repository.";

	private final HybridSearchService hybridSearchService;
	private final ContextBuilder contextBuilder;
	private final LlmClient llmClient;
	private final CitationParser citationParser;
	private final PromptTemplates promptTemplates;
	private final GitContextProperties properties;

	public AnswerService(
			HybridSearchService hybridSearchService,
			ContextBuilder contextBuilder,
			LlmClient llmClient,
			CitationParser citationParser,
			PromptTemplates promptTemplates,
			GitContextProperties properties
	) {
		this.hybridSearchService = Objects.requireNonNull(hybridSearchService, "hybridSearchService must not be null");
		this.contextBuilder = Objects.requireNonNull(contextBuilder, "contextBuilder must not be null");
		this.llmClient = Objects.requireNonNull(llmClient, "llmClient must not be null");
		this.citationParser = Objects.requireNonNull(citationParser, "citationParser must not be null");
		this.promptTemplates = Objects.requireNonNull(promptTemplates, "promptTemplates must not be null");
		this.properties = Objects.requireNonNull(properties, "properties must not be null");
	}

	/**
	 * Generates a grounded answer for the question using default answer options.
	 *
	 * @param repo repository reference
	 * @param question natural language inquiry
	 * @return grounded answer result
	 */
	public AnswerResult ask(RepoRef repo, String question) {
		return ask(repo, question, AnswerOptions.defaults());
	}

	/**
	 * Generates a grounded answer for the question using specified answer options.
	 *
	 * @param repo repository reference
	 * @param question natural language inquiry
	 * @param options retrieval, source limit, and reranking options
	 * @return grounded answer result with citations and timings
	 */
	public AnswerResult ask(RepoRef repo, String question, AnswerOptions options) {
		Objects.requireNonNull(repo, "repo must not be null");
		Objects.requireNonNull(question, "question must not be null");

		AnswerOptions effectiveOptions = (options != null) ? options : AnswerOptions.defaults();
		long totalStart = System.currentTimeMillis();
		List<String> warnings = new ArrayList<>();

		// 1. Retrieval via HybridSearchService
		int candidates = effectiveOptions.candidates();
		HybridSearchResult searchResult = hybridSearchService.search(
				repo,
				question,
				candidates,
				null,
				null,
				effectiveOptions.mode(),
				effectiveOptions.rerank(),
				effectiveOptions.excludeTests()
		);
		warnings.addAll(searchResult.warnings());
		long retrievalMillis = searchResult.tookMillis();

		List<HybridSearchHit> hits = searchResult.hits();
		if (!effectiveOptions.excludePathPatterns().isEmpty()) {
			hits = hits.stream()
					.filter(h -> {
						String path = (h.hit() != null) ? h.hit().path() : null;
						if (path == null) {
							return true;
						}
						String normalized = path.replace('\\', '/');
						for (String pattern : effectiveOptions.excludePathPatterns()) {
							if (normalized.contains(pattern)) {
								return false;
							}
						}
						return true;
					})
					.toList();
		}

		// Short-circuit without calling LLM if search found zero hits
		if (hits.isEmpty()) {
			log.info("No matching search hits found for repo='{}' question='{}'; short-circuiting LLM call",
					repo.fullName(), question);
			long totalMillis = System.currentTimeMillis() - totalStart;
			return new AnswerResult(
					NOT_FOUND_ANSWER,
					List.of(),
					List.of(),
					null,
					0,
					0,
					retrievalMillis,
					0L,
					totalMillis,
					warnings
			);
		}

		// 2. Build Bounded Context
		ContextBuilder.ContextBuildResult contextResult = contextBuilder.buildContext(
				hits,
				effectiveOptions.maxSources()
		);

		if (contextResult.sources().isEmpty()) {
			log.info("Zero sources met context criteria for repo='{}' question='{}'; short-circuiting LLM call",
					repo.fullName(), question);
			long totalMillis = System.currentTimeMillis() - totalStart;
			return new AnswerResult(
					NOT_FOUND_ANSWER,
					List.of(),
					List.of(),
					null,
					0,
					0,
					retrievalMillis,
					0L,
					totalMillis,
					warnings
			);
		}

		// 3. Prompt Construction & LLM Generation
		String systemInstruction = promptTemplates.systemInstruction();
		String userPrompt = promptTemplates.buildUserPrompt(question, contextResult.formattedContext());

		long genStart = System.currentTimeMillis();
		LlmResponse llmResponse = llmClient.generate(new LlmRequest(systemInstruction, userPrompt));
		long generationMillis = System.currentTimeMillis() - genStart;
		if (generationMillis > 20_000L) {
			log.warn("Answer generation took {} ms (> 20 s) for question: '{}'", generationMillis, question);
		}

		String primaryModel = properties.llm().model();
		if (llmResponse.model() != null && !llmResponse.model().equalsIgnoreCase(primaryModel)) {
			warnings.add("Primary model '" + primaryModel + "' failed; used fallback model '" + llmResponse.model() + "'");
		}

		if (llmResponse.finishReason() != null) {
			String reason = llmResponse.finishReason().trim().toUpperCase();
			if ("MAX_TOKENS".equals(reason)) {
				warnings.add("Answer was truncated because the output token limit was reached.");
			} else if ("SAFETY".equals(reason)) {
				warnings.add("Answer was flagged by safety policies.");
			} else if (!"STOP".equals(reason)) {
				warnings.add("Answer generation stopped unexpectedly with reason: " + llmResponse.finishReason());
			}
		}

		// 4. Citation Parsing & Verification
		CitationParser.CitationParseResult citationResult = citationParser.parse(
				llmResponse.text(),
				contextResult.sources()
		);

		if (citationResult.invalidCitationsCount() > 0) {
			warnings.add("Removed " + citationResult.invalidCitationsCount() + " invalid citation marker(s)");
		}

		long totalMillis = System.currentTimeMillis() - totalStart;

		return new AnswerResult(
				citationResult.cleanedAnswer(),
				citationResult.citations(),
				citationResult.updatedSources(),
				llmResponse.model(),
				llmResponse.promptTokens(),
				llmResponse.outputTokens(),
				llmResponse.thoughtsTokens(),
				retrievalMillis,
				generationMillis,
				totalMillis,
				warnings
		);
	}
}
