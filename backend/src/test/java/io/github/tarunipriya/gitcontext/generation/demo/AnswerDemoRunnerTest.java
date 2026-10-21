package io.github.tarunipriya.gitcontext.generation.demo;

import io.github.tarunipriya.gitcontext.generation.AnswerOptions;
import io.github.tarunipriya.gitcontext.generation.AnswerResult;
import io.github.tarunipriya.gitcontext.generation.AnswerService;
import io.github.tarunipriya.gitcontext.generation.Citation;
import io.github.tarunipriya.gitcontext.generation.LlmUnavailableException;
import io.github.tarunipriya.gitcontext.indexing.VectorStore;
import io.github.tarunipriya.gitcontext.ingestion.IngestionService;
import io.github.tarunipriya.gitcontext.model.RepoRef;
import io.github.tarunipriya.gitcontext.retrieval.keyword.KeywordIndex;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnswerDemoRunnerTest {

	@Mock
	private AnswerService answerService;

	@Mock
	private IngestionService ingestionService;

	@Mock
	private VectorStore vectorStore;

	@Mock
	private KeywordIndex keywordIndex;

	private AnswerDemoRunner runner;
	private final RepoRef repoRef = RepoRef.parse("tarunipriya/gitcontext");

	@BeforeEach
	void setUp() {
		runner = new AnswerDemoRunner(
				answerService,
				ingestionService,
				vectorStore,
				keywordIndex,
				"tarunipriya/gitcontext",
				false
		);
	}

	@Test
	@DisplayName("executeQuestions classifies answered, partial, refused, and failed with /demo/ exclusion")
	void executeQuestions_classifiesAllFourCategoriesAndExcludesDemoPaths() {
		List<String> questions = List.of(
				"How does rate limiting work?",
				"What are the caching policies?",
				"Which database stores passwords?",
				"Failing question causing error"
		);

		AnswerResult successResult = new AnswerResult(
				"GitContext uses exponential backoff [1].",
				List.of(new Citation(1, "src/GitHubClient.java", 10, 20, "checkRateLimit", "https://github.com/...", "chunk-1")),
				List.of(),
				"gemini-3.5-flash",
				150,
				45,
				12,
				100L,
				1500L,
				1600L,
				List.of()
		);

		AnswerResult partialResult = new AnswerResult(
				"GitContext caches tokens in memory [1]. However, database caching policies were not found in the indexed repository.",
				List.of(new Citation(1, "src/TokenCache.java", 5, 15, "getToken", "https://github.com/...", "chunk-2")),
				List.of(),
				"gemini-3.5-flash",
				180,
				60,
				10,
				90L,
				1200L,
				1290L,
				List.of()
		);

		AnswerResult refusedResult = new AnswerResult(
				"I couldn't find this in the indexed repository. GitContext does not store user passwords.",
				List.of(),
				List.of(),
				"gemini-3.5-flash",
				120,
				20,
				5,
				80L,
				900L,
				980L,
				List.of()
		);

		when(answerService.ask(eq(repoRef), eq(questions.get(0)), any(AnswerOptions.class)))
				.thenReturn(successResult);
		when(answerService.ask(eq(repoRef), eq(questions.get(1)), any(AnswerOptions.class)))
				.thenReturn(partialResult);
		when(answerService.ask(eq(repoRef), eq(questions.get(2)), any(AnswerOptions.class)))
				.thenReturn(refusedResult);
		when(answerService.ask(eq(repoRef), eq(questions.get(3)), any(AnswerOptions.class)))
				.thenThrow(new LlmUnavailableException("Gemini API server error (503)"));

		AnswerDemoRunner.DemoSummary summary = runner.executeQuestions(repoRef, questions);

		assertThat(summary).isNotNull();
		assertThat(summary.answered()).isEqualTo(1);
		assertThat(summary.partial()).isEqualTo(1);
		assertThat(summary.refused()).isEqualTo(1);
		assertThat(summary.failed()).isEqualTo(1);
		assertThat(summary.total()).isEqualTo(4);

		ArgumentCaptor<AnswerOptions> optionsCaptor = ArgumentCaptor.forClass(AnswerOptions.class);
		verify(answerService).ask(eq(repoRef), eq(questions.get(0)), optionsCaptor.capture());
		assertThat(optionsCaptor.getValue().excludePathPatterns()).contains("/demo/");
		assertThat(optionsCaptor.getValue().excludeTests()).isTrue();
	}

	@Test
	@DisplayName("executeQuestions handles slow generation warning gracefully")
	void executeQuestions_slowGeneration_succeedsAndRecordsAnswered() {
		List<String> questions = List.of("Slow query");

		AnswerResult slowResult = new AnswerResult(
				"Completed answer [1].",
				List.of(new Citation(1, "path/File.java", 1, 10, "method", "https://github.com/...", "chunk-1")),
				List.of(),
				"gemini-3.5-flash",
				200,
				100,
				25,
				200L,
				25000L, // > 20s
				25200L,
				List.of()
		);

		when(answerService.ask(eq(repoRef), eq(questions.getFirst()), any(AnswerOptions.class)))
				.thenReturn(slowResult);

		AnswerDemoRunner.DemoSummary summary = runner.executeQuestions(repoRef, questions);

		assertThat(summary.answered()).isEqualTo(1);
		assertThat(summary.partial()).isEqualTo(0);
		assertThat(summary.refused()).isEqualTo(0);
		assertThat(summary.failed()).isEqualTo(0);
		assertThat(summary.total()).isEqualTo(1);
	}
}
