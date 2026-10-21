package io.github.tarunipriya.gitcontext.generation;

import io.github.tarunipriya.gitcontext.config.GitContextProperties;
import io.github.tarunipriya.gitcontext.indexing.SearchHit;
import io.github.tarunipriya.gitcontext.model.ChunkType;
import io.github.tarunipriya.gitcontext.model.RepoRef;
import io.github.tarunipriya.gitcontext.model.SourceType;
import io.github.tarunipriya.gitcontext.retrieval.HybridSearchHit;
import io.github.tarunipriya.gitcontext.retrieval.HybridSearchResult;
import io.github.tarunipriya.gitcontext.retrieval.HybridSearchService;
import io.github.tarunipriya.gitcontext.retrieval.SearchMode;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnswerServiceTest {

	@Mock
	private HybridSearchService hybridSearchService;

	@Mock
	private ContextBuilder contextBuilder;

	@Mock
	private LlmClient llmClient;

	@Mock
	private CitationParser citationParser;

	@Mock
	private PromptTemplates promptTemplates;

	@Mock
	private GitContextProperties properties;

	private AnswerService answerService;
	private final RepoRef testRepo = new RepoRef("testowner", "testrepo");

	@BeforeEach
	void setUp() {
		GitContextProperties.LlmProperties llmProps = new GitContextProperties.LlmProperties(
				"gemini",
				"gemini-3.8-flash",
				"gemini-2.5-flash",
				"https://generativelanguage.googleapis.com/v1beta",
				0.2,
				2048,
				60,
				2,
				"low"
		);
		lenient().when(properties.llm()).thenReturn(llmProps);

		answerService = new AnswerService(
				hybridSearchService,
				contextBuilder,
				llmClient,
				citationParser,
				promptTemplates,
				properties
		);
	}

	@Test
	@DisplayName("ask happy path retrieves hits, builds context, generates answer, and verifies citations")
	void ask_happyPath_generatesAnswerAndParsesCitations() {
		SearchHit hit = createSearchHit("chunk-1", "src/Chunker.java", 10, 30);
		HybridSearchHit hybridHit = new HybridSearchHit(hit, 0.03, 0.9, 1, 10.0, 1, null, null);
		HybridSearchResult searchResult = new HybridSearchResult(
				List.of(hybridHit),
				SearchMode.HYBRID,
				false,
				List.of(),
				15L,
				10L,
				5L,
				0L,
				30L
		);

		Source source = new Source(
				1,
				hit,
				false
		);
		ContextBuilder.ContextBuildResult contextResult = new ContextBuilder.ContextBuildResult(
				List.of(source),
				"[1] src/Chunker.java (lines 10-30)\n```java\nclass Chunker {}\n```",
				150
		);

		when(hybridSearchService.search(
				eq(testRepo),
				eq("how does chunking work?"),
				eq(20),
				eq(null),
				eq(null),
				eq(SearchMode.HYBRID),
				eq(false),
				eq(true)
		)).thenReturn(searchResult);

		when(contextBuilder.buildContext(eq(List.of(hybridHit)), eq(8)))
				.thenReturn(contextResult);

		when(promptTemplates.systemInstruction()).thenReturn("System instruction");
		when(promptTemplates.buildUserPrompt("how does chunking work?", contextResult.formattedContext()))
				.thenReturn("Formatted prompt");

		LlmResponse llmResponse = new LlmResponse(
				"Chunking splits Java code by AST nodes [1].",
				"gemini-3.8-flash",
				120,
				25,
				"STOP",
				450L
		);
		when(llmClient.generate(any(LlmRequest.class))).thenReturn(llmResponse);

		Source citedSource = source.withCited(true);
		Citation citation = new Citation(
				1,
				"src/Chunker.java",
				10,
				30,
				"Chunker",
				"https://github.com/testowner/testrepo/blob/main/src/Chunker.java#L10-L30",
				"chunk-1"
		);
		when(citationParser.parse("Chunking splits Java code by AST nodes [1].", List.of(source)))
				.thenReturn(new CitationParser.CitationParseResult(
						"Chunking splits Java code by AST nodes [1].",
						List.of(citation),
						List.of(citedSource),
						0
				));

		AnswerResult result = answerService.ask(testRepo, "how does chunking work?");

		assertThat(result.answer()).isEqualTo("Chunking splits Java code by AST nodes [1].");
		assertThat(result.citations()).hasSize(1);
		assertThat(result.citations().getFirst().number()).isEqualTo(1);
		assertThat(result.sources()).hasSize(1);
		assertThat(result.sources().getFirst().cited()).isTrue();
		assertThat(result.model()).isEqualTo("gemini-3.8-flash");
		assertThat(result.promptTokens()).isEqualTo(120);
		assertThat(result.outputTokens()).isEqualTo(25);
		assertThat(result.retrievalMillis()).isEqualTo(30L);
		assertThat(result.generationMillis()).isGreaterThanOrEqualTo(0L);
		assertThat(result.warnings()).isEmpty();
	}

	@Test
	@DisplayName("ask with zero search hits returns couldn't find answer without calling LLM")
	void ask_zeroSearchHits_returnsNotFoundWithoutCallingLlm() {
		HybridSearchResult emptySearchResult = new HybridSearchResult(
				List.of(),
				SearchMode.HYBRID,
				false,
				List.of(),
				10L,
				5L,
				2L,
				0L,
				17L
		);

		when(hybridSearchService.search(
				eq(testRepo),
				eq("unknown topic"),
				eq(20),
				eq(null),
				eq(null),
				eq(SearchMode.HYBRID),
				eq(false),
				eq(true)
		)).thenReturn(emptySearchResult);

		AnswerResult result = answerService.ask(testRepo, "unknown topic");

		assertThat(result.answer()).isEqualTo(AnswerService.NOT_FOUND_ANSWER);
		assertThat(result.citations()).isEmpty();
		assertThat(result.sources()).isEmpty();
		assertThat(result.model()).isNull();
		assertThat(result.promptTokens()).isZero();
		assertThat(result.outputTokens()).isZero();
		assertThat(result.generationMillis()).isZero();
		assertThat(result.retrievalMillis()).isEqualTo(17L);

		verify(contextBuilder, never()).buildContext(any(), anyInt());
		verify(llmClient, never()).generate(any());
	}

	@Test
	@DisplayName("ask with zero context sources returns couldn't find answer without calling LLM")
	void ask_zeroContextSources_returnsNotFoundWithoutCallingLlm() {
		SearchHit hit = createSearchHit("chunk-1", "src/Chunker.java", 10, 30);
		HybridSearchHit hybridHit = new HybridSearchHit(hit, 0.03, 0.9, 1, 10.0, 1, null, null);
		HybridSearchResult searchResult = new HybridSearchResult(
				List.of(hybridHit),
				SearchMode.HYBRID,
				false,
				List.of(),
				15L,
				10L,
				5L,
				0L,
				30L
		);

		when(hybridSearchService.search(
				eq(testRepo),
				eq("how does it work?"),
				anyInt(),
				any(),
				any(),
				any(),
				anyBoolean(),
				anyBoolean()
		)).thenReturn(searchResult);

		when(contextBuilder.buildContext(any(), anyInt()))
				.thenReturn(new ContextBuilder.ContextBuildResult(List.of(), "", 0));

		AnswerResult result = answerService.ask(testRepo, "how does it work?");

		assertThat(result.answer()).isEqualTo(AnswerService.NOT_FOUND_ANSWER);
		assertThat(result.citations()).isEmpty();
		assertThat(result.sources()).isEmpty();
		assertThat(result.model()).isNull();

		verify(llmClient, never()).generate(any());
	}

	@Test
	@DisplayName("ask with invalid citations produces warning and strips invalid markers")
	void ask_invalidCitations_producesWarning() {
		SearchHit hit = createSearchHit("chunk-1", "src/Chunker.java", 10, 30);
		HybridSearchHit hybridHit = new HybridSearchHit(hit, 0.03, 0.9, 1, 10.0, 1, null, null);
		HybridSearchResult searchResult = new HybridSearchResult(
				List.of(hybridHit),
				SearchMode.HYBRID,
				false,
				List.of(),
				15L,
				10L,
				5L,
				0L,
				30L
		);

		Source source = new Source(1, hit, false);
		ContextBuilder.ContextBuildResult contextResult = new ContextBuilder.ContextBuildResult(
				List.of(source),
				"formatted context",
				100
		);

		when(hybridSearchService.search(any(), any(), anyInt(), any(), any(), any(), anyBoolean(), anyBoolean()))
				.thenReturn(searchResult);
		when(contextBuilder.buildContext(any(), anyInt())).thenReturn(contextResult);
		when(promptTemplates.systemInstruction()).thenReturn("system");
		when(promptTemplates.buildUserPrompt(any(), any())).thenReturn("prompt");

		when(llmClient.generate(any())).thenReturn(new LlmResponse(
				"Answer citing [1] and [99].",
				"gemini-3.8-flash",
				100,
				30,
				"STOP",
				500L
		));

		when(citationParser.parse(eq("Answer citing [1] and [99]."), any()))
				.thenReturn(new CitationParser.CitationParseResult(
						"Answer citing [1].",
						List.of(new Citation(1, "src/Chunker.java", 10, 30, "Chunker", "url", "chunk-1")),
						List.of(source.withCited(true)),
						1
				));

		AnswerResult result = answerService.ask(testRepo, "question");

		assertThat(result.answer()).isEqualTo("Answer citing [1].");
		assertThat(result.warnings()).contains("Removed 1 invalid citation marker(s)");
	}

	@Test
	@DisplayName("ask when fallback model was used produces warning")
	void ask_fallbackModelUsed_producesWarning() {
		SearchHit hit = createSearchHit("chunk-1", "src/Chunker.java", 10, 30);
		HybridSearchHit hybridHit = new HybridSearchHit(hit, 0.03, 0.9, 1, 10.0, 1, null, null);
		HybridSearchResult searchResult = new HybridSearchResult(
				List.of(hybridHit),
				SearchMode.HYBRID,
				false,
				List.of(),
				15L,
				10L,
				5L,
				0L,
				30L
		);

		Source source = new Source(1, hit, false);
		ContextBuilder.ContextBuildResult contextResult = new ContextBuilder.ContextBuildResult(
				List.of(source),
				"formatted context",
				100
		);

		when(hybridSearchService.search(any(), any(), anyInt(), any(), any(), any(), anyBoolean(), anyBoolean()))
				.thenReturn(searchResult);
		when(contextBuilder.buildContext(any(), anyInt())).thenReturn(contextResult);
		when(promptTemplates.systemInstruction()).thenReturn("system");
		when(promptTemplates.buildUserPrompt(any(), any())).thenReturn("prompt");

		when(llmClient.generate(any())).thenReturn(new LlmResponse(
				"Answer from fallback.",
				"gemini-2.5-flash",
				100,
				20,
				"STOP",
				500L
		));

		when(citationParser.parse(any(), any()))
				.thenReturn(new CitationParser.CitationParseResult(
						"Answer from fallback.",
						List.of(),
						List.of(source),
						0
				));

		AnswerResult result = answerService.ask(testRepo, "question");

		assertThat(result.warnings()).contains("Primary model 'gemini-3.8-flash' failed; used fallback model 'gemini-2.5-flash'");
	}

	@Test
	@DisplayName("ask propagates retrieval warnings to AnswerResult")
	void ask_retrievalWarnings_propagatedToResult() {
		SearchHit hit = createSearchHit("chunk-1", "src/Chunker.java", 10, 30);
		HybridSearchHit hybridHit = new HybridSearchHit(hit, 0.03, 0.9, 1, 10.0, 1, null, null);
		HybridSearchResult searchResult = new HybridSearchResult(
				List.of(hybridHit),
				SearchMode.HYBRID,
				false,
				List.of("Qdrant service unavailable, degraded to keyword search"),
				15L,
				10L,
				5L,
				0L,
				30L
		);

		Source source = new Source(1, hit, false);
		ContextBuilder.ContextBuildResult contextResult = new ContextBuilder.ContextBuildResult(
				List.of(source),
				"formatted context",
				100
		);

		when(hybridSearchService.search(any(), any(), anyInt(), any(), any(), any(), anyBoolean(), anyBoolean()))
				.thenReturn(searchResult);
		when(contextBuilder.buildContext(any(), anyInt())).thenReturn(contextResult);
		when(promptTemplates.systemInstruction()).thenReturn("system");
		when(promptTemplates.buildUserPrompt(any(), any())).thenReturn("prompt");

		when(llmClient.generate(any())).thenReturn(new LlmResponse(
				"Answer.",
				"gemini-3.8-flash",
				100,
				20,
				"STOP",
				500L
		));

		when(citationParser.parse(any(), any()))
				.thenReturn(new CitationParser.CitationParseResult(
						"Answer.",
						List.of(),
						List.of(source),
						0
				));

		AnswerResult result = answerService.ask(testRepo, "question");

		assertThat(result.warnings()).contains("Qdrant service unavailable, degraded to keyword search");
	}

	@Test
	@DisplayName("ask when finishReason is MAX_TOKENS keeps partial answer, preserves thoughtsTokens, and adds truncation warning")
	void ask_finishReasonMaxTokens_keepsPartialAnswerAndAddsWarning() {
		SearchHit hit = createSearchHit("chunk-1", "src/Chunker.java", 10, 30);
		HybridSearchHit hybridHit = new HybridSearchHit(hit, 0.03, 0.9, 1, 10.0, 1, null, null);
		HybridSearchResult searchResult = new HybridSearchResult(
				List.of(hybridHit),
				SearchMode.HYBRID,
				false,
				List.of(),
				15L,
				10L,
				5L,
				0L,
				30L
		);

		Source source = new Source(1, hit, false);
		ContextBuilder.ContextBuildResult contextResult = new ContextBuilder.ContextBuildResult(
				List.of(source),
				"formatted context",
				100
		);

		when(hybridSearchService.search(any(), any(), anyInt(), any(), any(), any(), anyBoolean(), anyBoolean()))
				.thenReturn(searchResult);
		when(contextBuilder.buildContext(any(), anyInt())).thenReturn(contextResult);
		when(promptTemplates.systemInstruction()).thenReturn("system");
		when(promptTemplates.buildUserPrompt(any(), any())).thenReturn("prompt");

		when(llmClient.generate(any())).thenReturn(new LlmResponse(
				"Partial answer that was cut off [1]",
				"gemini-3.8-flash",
				150,
				41,
				20,
				"MAX_TOKENS",
				400L
		));

		when(citationParser.parse(eq("Partial answer that was cut off [1]"), any()))
				.thenReturn(new CitationParser.CitationParseResult(
						"Partial answer that was cut off [1]",
						List.of(new Citation(1, "src/Chunker.java", 10, 30, "Chunker", "url", "chunk-1")),
						List.of(source.withCited(true)),
						0
				));

		AnswerResult result = answerService.ask(testRepo, "question");

		assertThat(result.answer()).isEqualTo("Partial answer that was cut off [1]");
		assertThat(result.outputTokens()).isEqualTo(41);
		assertThat(result.thoughtsTokens()).isEqualTo(20);
		assertThat(result.citations()).hasSize(1);
		assertThat(result.warnings()).contains("Answer was truncated because the output token limit was reached.");
	}

	@Test
	@DisplayName("ask when finishReason is SAFETY keeps answer and adds safety warning")
	void ask_finishReasonSafety_keepsAnswerAndAddsWarning() {
		SearchHit hit = createSearchHit("chunk-1", "src/Chunker.java", 10, 30);
		HybridSearchHit hybridHit = new HybridSearchHit(hit, 0.03, 0.9, 1, 10.0, 1, null, null);
		HybridSearchResult searchResult = new HybridSearchResult(
				List.of(hybridHit),
				SearchMode.HYBRID,
				false,
				List.of(),
				15L,
				10L,
				5L,
				0L,
				30L
		);

		Source source = new Source(1, hit, false);
		ContextBuilder.ContextBuildResult contextResult = new ContextBuilder.ContextBuildResult(
				List.of(source),
				"formatted context",
				100
		);

		when(hybridSearchService.search(any(), any(), anyInt(), any(), any(), any(), anyBoolean(), anyBoolean()))
				.thenReturn(searchResult);
		when(contextBuilder.buildContext(any(), anyInt())).thenReturn(contextResult);
		when(promptTemplates.systemInstruction()).thenReturn("system");
		when(promptTemplates.buildUserPrompt(any(), any())).thenReturn("prompt");

		when(llmClient.generate(any())).thenReturn(new LlmResponse(
				"Partial content flagged by safety",
				"gemini-3.8-flash",
				100,
				15,
				null,
				"SAFETY",
				350L
		));

		when(citationParser.parse(any(), any()))
				.thenReturn(new CitationParser.CitationParseResult(
						"Partial content flagged by safety",
						List.of(),
						List.of(source),
						0
				));

		AnswerResult result = answerService.ask(testRepo, "question");

		assertThat(result.answer()).isEqualTo("Partial content flagged by safety");
		assertThat(result.warnings()).contains("Answer was flagged by safety policies.");
	}

	@Test
	@DisplayName("ask when finishReason is other non-STOP reasons adds warning")
	void ask_finishReasonOtherNonStop_addsWarning() {
		SearchHit hit = createSearchHit("chunk-1", "src/Chunker.java", 10, 30);
		HybridSearchHit hybridHit = new HybridSearchHit(hit, 0.03, 0.9, 1, 10.0, 1, null, null);
		HybridSearchResult searchResult = new HybridSearchResult(
				List.of(hybridHit),
				SearchMode.HYBRID,
				false,
				List.of(),
				15L,
				10L,
				5L,
				0L,
				30L
		);

		Source source = new Source(1, hit, false);
		ContextBuilder.ContextBuildResult contextResult = new ContextBuilder.ContextBuildResult(
				List.of(source),
				"formatted context",
				100
		);

		when(hybridSearchService.search(any(), any(), anyInt(), any(), any(), any(), anyBoolean(), anyBoolean()))
				.thenReturn(searchResult);
		when(contextBuilder.buildContext(any(), anyInt())).thenReturn(contextResult);
		when(promptTemplates.systemInstruction()).thenReturn("system");
		when(promptTemplates.buildUserPrompt(any(), any())).thenReturn("prompt");

		when(llmClient.generate(any())).thenReturn(new LlmResponse(
				"Recitation blocked",
				"gemini-3.8-flash",
				100,
				15,
				null,
				"RECITATION",
				350L
		));

		when(citationParser.parse(any(), any()))
				.thenReturn(new CitationParser.CitationParseResult(
						"Recitation blocked",
						List.of(),
						List.of(source),
						0
				));

		AnswerResult result = answerService.ask(testRepo, "question");

		assertThat(result.warnings()).contains("Answer generation stopped unexpectedly with reason: RECITATION");
	}

	@Test
	@DisplayName("ask with excludePathPatterns filters out matching paths before context building")
	void ask_excludePathPatterns_filtersOutMatchingPaths() {
		SearchHit hit1Data = createSearchHit("c1", "src/main/demo/DemoRunner.java", 1, 20);
		SearchHit hit2Data = createSearchHit("c2", "src/main/App.java", 1, 20);
		HybridSearchHit hit1 = new HybridSearchHit(hit1Data, 0.03, 0.9, 1, 10.0, 1, null, null);
		HybridSearchHit hit2 = new HybridSearchHit(hit2Data, 0.02, 0.8, 2, 8.0, 2, null, null);
		HybridSearchResult searchResult = new HybridSearchResult(
				List.of(hit1, hit2),
				SearchMode.HYBRID,
				false,
				List.of(),
				10L, 10L, 5L, 0L, 25L
		);

		when(hybridSearchService.search(any(), any(), anyInt(), any(), any(), any(), anyBoolean(), anyBoolean()))
				.thenReturn(searchResult);

		Source source = new Source(1, hit2Data, false);
		when(contextBuilder.buildContext(any(), anyInt()))
				.thenReturn(new ContextBuilder.ContextBuildResult(List.of(source), "Context text", 100));
		when(promptTemplates.systemInstruction()).thenReturn("sys");
		when(promptTemplates.buildUserPrompt(any(), any())).thenReturn("prompt");
		when(llmClient.generate(any()))
				.thenReturn(new LlmResponse("Answer [1]", "gemini-3.5-flash", 100, 20, null, "STOP", 100L));
		when(citationParser.parse(any(), any()))
				.thenReturn(new CitationParser.CitationParseResult(
						"Answer [1]",
						List.of(new Citation(1, "src/main/App.java", 1, 20, null, "url", "c2")),
						List.of(source.withCited(true)),
						1
				));

		AnswerOptions options = new AnswerOptions(
				SearchMode.HYBRID,
				true,
				false,
				8,
				20,
				List.of("/demo/")
		);

		AnswerResult result = answerService.ask(testRepo, "question", options);

		assertThat(result.answer()).isEqualTo("Answer [1]");

		@SuppressWarnings("unchecked")
		org.mockito.ArgumentCaptor<List<HybridSearchHit>> hitsCaptor = org.mockito.ArgumentCaptor.forClass(List.class);
		verify(contextBuilder).buildContext(hitsCaptor.capture(), eq(8));
		List<HybridSearchHit> passedHits = hitsCaptor.getValue();
		assertThat(passedHits).hasSize(1);
		assertThat(passedHits.getFirst().hit().path()).isEqualTo("src/main/App.java");
	}

	private SearchHit createSearchHit(String chunkId, String path, int startLine, int endLine) {
		return new SearchHit(
				"uuid-1",
				0.9,
				testRepo.normalizedKey(),
				"run-1",
				chunkId,
				"doc-1",
				0,
				SourceType.CODE,
				ChunkType.CLASS,
				path,
				"java",
				"Chunker",
				null,
				null,
				startLine,
				endLine,
				50,
				"https://github.com/testowner/testrepo/blob/main/" + path + "#L" + startLine + "-L" + endLine,
				"commit1",
				"// " + path + "\nclass Chunker {}",
				"class Chunker {}"
		);
	}
}
