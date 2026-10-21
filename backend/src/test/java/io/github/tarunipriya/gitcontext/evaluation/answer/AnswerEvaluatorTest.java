package io.github.tarunipriya.gitcontext.evaluation.answer;

import io.github.tarunipriya.gitcontext.config.GitContextProperties;
import io.github.tarunipriya.gitcontext.evaluation.dataset.EvalCase;
import io.github.tarunipriya.gitcontext.evaluation.dataset.EvalDataset;
import io.github.tarunipriya.gitcontext.generation.AnswerOptions;
import io.github.tarunipriya.gitcontext.generation.AnswerResult;
import io.github.tarunipriya.gitcontext.generation.AnswerService;
import io.github.tarunipriya.gitcontext.generation.Citation;
import io.github.tarunipriya.gitcontext.generation.Source;
import io.github.tarunipriya.gitcontext.indexing.SearchHit;
import io.github.tarunipriya.gitcontext.model.ChunkType;
import io.github.tarunipriya.gitcontext.model.RepoRef;
import io.github.tarunipriya.gitcontext.model.SourceType;
import io.github.tarunipriya.gitcontext.retrieval.HybridSearchService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AnswerEvaluatorTest {

	private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

	private SearchHit createHit(String path, SourceType sourceType, String url, String commitSha) {
		return new SearchHit(
				"point-1",
				0.95,
				"tarunipriya/gitcontext",
				"run-1",
				"chunk-1",
				"doc-1",
				0,
				sourceType,
				ChunkType.METHOD,
				path,
				"java",
				null,
				null,
				null,
				1,
				20,
				100,
				url,
				commitSha,
				"code snippet",
				"code snippet"
		);
	}

	@Test
	@DisplayName("Calculates metrics, refusal detection, cited recall with pr/commit, skips judge when refused/unanswerable, and records judge failure")
	void testEvaluationMetricsAndWorkflow(@TempDir Path tempDir) throws Exception {
		AnswerService answerService = mock(AnswerService.class);
		AnswerJudge answerJudge = mock(AnswerJudge.class);
		HybridSearchService hybridSearchService = mock(HybridSearchService.class);

		GitContextProperties.AnswerEvalProperties evalConfig = new GitContextProperties.AnswerEvalProperties(
				"eval/datasets/test.jsonl",
				"tarunipriya/gitcontext",
				false,
				tempDir.toString(),
				200L,
				true,
				6000,
				0,
				3,
				true,
				"gemini-3.8-flash",
				true
		);

		GitContextProperties properties = new GitContextProperties(
				null,
				Path.of("./data/repos"),
				"http://localhost:6333",
				null,
				null,
				null,
				null,
				null,
				null,
				null,
				null,
				null,
				null,
				null,
				null,
				evalConfig
		);

		AtomicInteger sleepCalls = new AtomicInteger();
		List<Long> sleepDurations = new ArrayList<>();
		AnswerEvaluator.Sleeper sleeper = millis -> {
			sleepCalls.incrementAndGet();
			sleepDurations.add(millis);
		};

		AnswerEvaluator evaluator = new AnswerEvaluator(answerService, answerJudge, hybridSearchService, properties, sleeper);

		// Case 1: Answerable case citing code, PR, and commit
		EvalCase case1 = new EvalCase(
				"case-1",
				"How are PRs and commits ingested?",
				List.of("backend/src/main/App.java", "pr:16", "commit:a1b2c3d"),
				List.of("PRs use GitHub API", "Commits use JGit"),
				"architecture",
				true
		);

		SearchHit hitFile = createHit("backend/src/main/App.java", SourceType.CODE, null, null);
		SearchHit hitPr = createHit(null, SourceType.PULL_REQUEST, "https://github.com/tarunipriya/gitcontext/pull/16", null);
		SearchHit hitUncited = createHit("backend/src/main/Other.java", SourceType.CODE, null, null);

		AnswerResult result1 = new AnswerResult(
				"PRs use GitHub API [2] and Commits use JGit in App.java [1].",
				List.of(new Citation(1, "backend/src/main/App.java", 1, 20, null, null, "chunk-1")),
				List.of(
						new Source(1, hitFile, true),
						new Source(2, hitPr, true),
						new Source(3, hitUncited, false)
				),
				"gemini-3.5-flash",
				100,
				50,
				null,
				150L,
				2000L,
				2150L,
				List.of()
		);

		when(answerService.ask(any(), eq(case1.question()), any(AnswerOptions.class))).thenReturn(result1);
		when(answerJudge.judge(eq(case1.question()), eq(case1.expectedFacts()), eq(result1.answer()), anyString(), anyString()))
				.thenReturn(Optional.of(new JudgeVerdict(List.of(true, true), true, List.of())));

		// Case 2: Answerable case that gets falsely refused
		EvalCase case2 = new EvalCase(
				"case-2",
				"What is the Qdrant port?",
				List.of("backend/src/main/Config.java"),
				List.of("Port is 6333"),
				"config",
				true
		);

		AnswerResult result2 = new AnswerResult(
				"I couldn't find this in the indexed repository.",
				List.of(),
				List.of(),
				"gemini-3.5-flash",
				80,
				10,
				null,
				100L,
				1200L,
				1300L,
				List.of()
		);
		when(answerService.ask(any(), eq(case2.question()), any(AnswerOptions.class))).thenReturn(result2);

		// Case 3: Unanswerable case correctly refused
		EvalCase case3 = new EvalCase(
				"case-3",
				"How to deploy on Kubernetes with Helm chart?",
				List.of(),
				List.of(),
				"out-of-scope",
				false
		);

		AnswerResult result3 = new AnswerResult(
				AnswerService.NOT_FOUND_ANSWER,
				List.of(),
				List.of(),
				"gemini-3.5-flash",
				60,
				10,
				null,
				80L,
				900L,
				980L,
				List.of()
		);
		when(answerService.ask(any(), eq(case3.question()), any(AnswerOptions.class))).thenReturn(result3);

		// Case 4: Answerable case where judge fails (invalid JSON or count mismatch)
		EvalCase case4 = new EvalCase(
				"case-4",
				"Where are Lucene indexes located?",
				List.of("backend/src/main/Lucene.java"),
				List.of("Index directory is data/lucene"),
				"indexing",
				true
		);
		AnswerResult result4 = new AnswerResult(
				"Lucene index is at data/lucene [1].",
				List.of(),
				List.of(new Source(1, hitFile, true)),
				"gemini-3.5-flash",
				70,
				30,
				null,
				90L,
				1500L,
				1590L,
				List.of()
		);
		when(answerService.ask(any(), eq(case4.question()), any(AnswerOptions.class))).thenReturn(result4);
		when(answerJudge.judge(eq(case4.question()), eq(case4.expectedFacts()), eq(result4.answer()), anyString(), anyString()))
				.thenReturn(Optional.of(new JudgeVerdict(List.of(true), true, List.of())));

		EvalDataset dataset = new EvalDataset("test-benchmark", List.of(case1, case2, case3, case4));
		RepoRef repoRef = RepoRef.parse("tarunipriya/gitcontext");

		Optional<AnswerEvalReport> reportOpt = evaluator.evaluate(dataset, repoRef, "commit-sha-123", evalConfig);
		assertThat(reportOpt).isPresent();
		AnswerEvalReport report = reportOpt.get();

		// Verification of workflow
		assertThat(report.datasetName()).isEqualTo("test-benchmark");
		assertThat(report.repo()).isEqualTo("tarunipriya/gitcontext");
		assertThat(report.indexedCommitSha()).isEqualTo("commit-sha-123");
		assertThat(report.judgeModel()).isEqualTo("gemini-3.8-flash");
		assertThat(report.answerableCases()).isEqualTo(3);
		assertThat(report.unanswerableCases()).isEqualTo(1);
		assertThat(report.judgedCases()).isEqualTo(2);
		assertThat(report.judgeFailures()).isEqualTo(0);
		assertThat(report.generationFailures()).isEqualTo(0);

		// Case 1 checks
		AnswerCaseResult r1 = report.cases().get(0);
		assertThat(r1.caseId()).isEqualTo("case-1");
		assertThat(r1.refused()).isFalse();
		assertThat(r1.citedKeys()).containsExactly("backend/src/main/App.java", "pr:16");
		assertThat(r1.expectedKeys()).containsExactly("backend/src/main/App.java", "pr:16", "commit:a1b2c3d");
		assertThat(r1.citedExpectedRecall()).isCloseTo(2.0 / 3.0, within(0.001));
		assertThat(r1.citedAnyExpected()).isTrue();
		assertThat(r1.factCoverage()).isEqualTo(1.0);
		assertThat(r1.grounded()).isTrue();

		// Case 2 checks (False refusal: judge was skipped)
		AnswerCaseResult r2 = report.cases().get(1);
		assertThat(r2.caseId()).isEqualTo("case-2");
		assertThat(r2.refused()).isTrue();
		assertThat(r2.factCoverage()).isNull();
		assertThat(r2.grounded()).isNull();
		verify(answerJudge, never()).judge(eq(case2.question()), any(), any(), any(), any());

		// Case 3 checks (Unanswerable refusal: judge was skipped)
		AnswerCaseResult r3 = report.cases().get(2);
		assertThat(r3.caseId()).isEqualTo("case-3");
		assertThat(r3.refused()).isTrue();
		assertThat(r3.citedExpectedRecall()).isEqualTo(0.0);
		assertThat(r3.citedAnyExpected()).isFalse();
		verify(answerJudge, never()).judge(eq(case3.question()), any(), any(), any(), any());

		// Case 4 checks (Answerable and successfully judged)
		AnswerCaseResult r4 = report.cases().get(3);
		assertThat(r4.caseId()).isEqualTo("case-4");
		assertThat(r4.answer()).isEqualTo("Lucene index is at data/lucene [1].");
		assertThat(r4.factCoverage()).isEqualTo(1.0);
		assertThat(r4.grounded()).isTrue();

		// Summary metrics checks
		assertThat(report.meanFactCoverage()).isEqualTo(1.0);
		assertThat(report.fullyCorrectRate()).isEqualTo(1.0);
		assertThat(report.groundedRate()).isEqualTo(1.0);

		// Latency stats should include all 4 successful cases
		assertThat(report.retrievalLatency().count()).isEqualTo(4);
		assertThat(report.generationLatency().count()).isEqualTo(4);
		assertThat(report.totalLatency().count()).isEqualTo(4);

		// Verify untimed retrieval warm-up was called once with the first question
		verify(hybridSearchService, times(1)).search(repoRef, case1.question(), 8, null, null, null, false, false);

		// Models and ModelUsage checks
		assertThat(report.modelUsage()).containsExactly(Map.entry("gemini-3.5-flash", 4));

		// Verify delay was called between LLM calls
		assertThat(sleepCalls.get()).isEqualTo(5);
		assertThat(sleepDurations).allMatch(d -> d == 200L);
	}

	@Test
	@DisplayName("Mixed models are tracked in insertion order and warm-up failures are handled gracefully")
	void testMixedModelTrackingAndWarmupFailureResilience(@TempDir Path tempDir) {
		AnswerService answerService = mock(AnswerService.class);
		AnswerJudge answerJudge = mock(AnswerJudge.class);
		HybridSearchService hybridSearchService = mock(HybridSearchService.class);

		GitContextProperties.AnswerEvalProperties evalConfig = new GitContextProperties.AnswerEvalProperties(
				"eval/datasets/test.jsonl",
				"tarunipriya/gitcontext",
				false,
				tempDir.toString(),
				0L,
				false,
				6000,
				0,
				3,
				true,
				"",
				false
		);

		GitContextProperties properties = new GitContextProperties(
				null, Path.of("./data/repos"), "http://localhost:6333", null, null, null, null, null, null, null, null, null, null, null, null, evalConfig
		);

		when(hybridSearchService.search(any(), any(), eq(8), any(), any(), any(), eq(false), eq(false)))
				.thenThrow(new RuntimeException("Qdrant connection refused during warm-up"));

		AnswerEvaluator evaluator = new AnswerEvaluator(answerService, answerJudge, hybridSearchService, properties);

		EvalCase case1 = new EvalCase("c-1", "Question 1", List.of("backend/A.java"), List.of(), "cat", true);
		EvalCase case2 = new EvalCase("c-2", "Question 2", List.of("backend/B.java"), List.of(), "cat", true);

		AnswerResult result1 = new AnswerResult("Answer 1", List.of(), List.of(), "gemini-3.5-flash", 10, 10, null, 100L, 500L, 600L, List.of());
		AnswerResult result2 = new AnswerResult("Answer 2", List.of(), List.of(), "gemini-3.8-flash", 10, 10, null, 120L, 600L, 720L, List.of());

		when(answerService.ask(any(), eq(case1.question()), any())).thenReturn(result1);
		when(answerService.ask(any(), eq(case2.question()), any())).thenReturn(result2);

		EvalDataset dataset = new EvalDataset("mixed-benchmark", List.of(case1, case2));
		RepoRef repoRef = RepoRef.parse("tarunipriya/gitcontext");

		AnswerEvalReport report = evaluator.evaluate(dataset, repoRef, "sha-abc", evalConfig).orElseThrow();

		verify(hybridSearchService, times(1)).search(repoRef, case1.question(), 8, null, null, null, false, false);

		assertThat(report.modelUsage()).containsExactly(
				Map.entry("gemini-3.5-flash", 1),
				Map.entry("gemini-3.8-flash", 1)
		);
		assertThat(report.cases().get(0).model()).isEqualTo("gemini-3.5-flash");
		assertThat(report.cases().get(1).model()).isEqualTo("gemini-3.8-flash");
	}

	@Test
	@DisplayName("3 consecutive failures abort the evaluation loop without generating a final report")
	void testConsecutiveFailuresAbortsWithoutFinalReport(@TempDir Path tempDir) {
		AnswerService answerService = mock(AnswerService.class);
		AnswerJudge answerJudge = mock(AnswerJudge.class);
		HybridSearchService hybridSearchService = mock(HybridSearchService.class);

		GitContextProperties.AnswerEvalProperties evalConfig = new GitContextProperties.AnswerEvalProperties(
				"eval/datasets/test.jsonl",
				"tarunipriya/gitcontext",
				false,
				tempDir.toString(),
				0L,
				false,
				6000,
				0,
				3, // maxConsecutiveFailures = 3
				true,
				"gemini-3.8-flash",
				true
		);

		GitContextProperties properties = new GitContextProperties(
				null, Path.of("./data/repos"), "http://localhost:6333", null, null, null, null, null, null, null, null, null, null, null, null, evalConfig
		);

		AnswerEvaluator evaluator = new AnswerEvaluator(answerService, answerJudge, hybridSearchService, properties);

		// 5 cases, but failures will happen on all
		List<EvalCase> cases = List.of(
				new EvalCase("c-1", "Q1", List.of("A.java"), List.of(), "cat", true),
				new EvalCase("c-2", "Q2", List.of("B.java"), List.of(), "cat", true),
				new EvalCase("c-3", "Q3", List.of("C.java"), List.of(), "cat", true),
				new EvalCase("c-4", "Q4", List.of("D.java"), List.of(), "cat", true),
				new EvalCase("c-5", "Q5", List.of("E.java"), List.of(), "cat", true)
		);
		EvalDataset dataset = new EvalDataset("abort-benchmark", cases);
		RepoRef repoRef = RepoRef.parse("tarunipriya/gitcontext");

		when(answerService.ask(any(), anyString(), any()))
				.thenThrow(new RuntimeException("Gemini API rate limit exceeded (HTTP 429)"));

		Optional<AnswerEvalReport> reportOpt = evaluator.evaluate(dataset, repoRef, "sha-failfast", evalConfig);

		// Must abort without a final report
		assertThat(reportOpt).isEmpty();

		// Only 3 attempts must be made before aborting (c-4 and c-5 never called)
		verify(answerService, times(3)).ask(any(), anyString(), any());

		// Checkpoint file must exist and contain exactly 3 lines with failed entries
		Path checkpointFile = tempDir.resolve("answers-abort-benchmark.checkpoint.jsonl");
		assertThat(checkpointFile).exists();
		List<AnswerCheckpointEntry> entries = AnswerEvaluator.readCheckpointEntries(checkpointFile);
		assertThat(entries).hasSize(3);
		assertThat(entries).allMatch(e -> !e.isComplete());
	}

	@Test
	@DisplayName("Resume skips successfully completed cases and retries failed cases")
	void testResumeSkipsCompletedAndRetriesFailed(@TempDir Path tempDir) throws IOException {
		AnswerService answerService = mock(AnswerService.class);
		AnswerJudge answerJudge = mock(AnswerJudge.class);
		HybridSearchService hybridSearchService = mock(HybridSearchService.class);

		GitContextProperties.AnswerEvalProperties evalConfig = new GitContextProperties.AnswerEvalProperties(
				"eval/datasets/test.jsonl",
				"tarunipriya/gitcontext",
				false,
				tempDir.toString(),
				0L,
				false,
				6000,
				0,
				3,
				true,
				"gemini-3.8-flash",
				true
		);

		GitContextProperties properties = new GitContextProperties(
				null, Path.of("./data/repos"), "http://localhost:6333", null, null, null, null, null, null, null, null, null, null, null, null, evalConfig
		);

		Path checkpointFile = tempDir.resolve("answers-resume-test.checkpoint.jsonl");

		// Pre-populate checkpoint: c-1 was successful, c-2 failed
		AnswerCaseResult case1Result = new AnswerCaseResult(
				"c-1", "Q1", true, "Answer 1", false, "gemini-3.5-flash", List.of(), List.of("A.java"), 0.0, false, null, null, List.of(), 10L, 20L, 30L, List.of()
		);
		AnswerCaseResult case2Failed = new AnswerCaseResult(
				"c-2", "Q2", true, "", false, null, List.of(), List.of("B.java"), 0.0, false, null, null, List.of(), 0L, 0L, 0L, List.of("Generation failed: 429")
		);

		AnswerCheckpointEntry entry1 = AnswerCheckpointEntry.from(case1Result, "sha-resume", "gemini-3.5-flash", "gemini-3.8-flash");
		AnswerCheckpointEntry entry2 = AnswerCheckpointEntry.from(case2Failed, "sha-resume", "gemini-3.5-flash", "gemini-3.8-flash");

		Files.writeString(checkpointFile, JSON_MAPPER.writeValueAsString(entry1) + "\n" + JSON_MAPPER.writeValueAsString(entry2) + "\n", StandardCharsets.UTF_8);

		AnswerEvaluator evaluator = new AnswerEvaluator(answerService, answerJudge, hybridSearchService, properties);

		EvalCase case1 = new EvalCase("c-1", "Q1", List.of("A.java"), List.of(), "cat", true);
		EvalCase case2 = new EvalCase("c-2", "Q2", List.of("B.java"), List.of(), "cat", true);
		EvalDataset dataset = new EvalDataset("resume-test", List.of(case1, case2));
		RepoRef repoRef = RepoRef.parse("tarunipriya/gitcontext");

		// On retry, c-2 succeeds
		AnswerResult retryResult = new AnswerResult("Answer 2", List.of(), List.of(), "gemini-3.5-flash", 10, 10, null, 15L, 25L, 40L, List.of());
		when(answerService.ask(any(), eq(case2.question()), any())).thenReturn(retryResult);

		Optional<AnswerEvalReport> reportOpt = evaluator.evaluate(dataset, repoRef, "sha-resume", evalConfig);

		// All cases now succeed, so report is returned
		assertThat(reportOpt).isPresent();
		AnswerEvalReport report = reportOpt.get();
		assertThat(report.cases()).hasSize(2);
		assertThat(report.cases().get(0).caseId()).isEqualTo("c-1");
		assertThat(report.cases().get(0).answer()).isEqualTo("Answer 1");
		assertThat(report.cases().get(1).caseId()).isEqualTo("c-2");
		assertThat(report.cases().get(1).answer()).isEqualTo("Answer 2");

		// Crucial verification: ask was NEVER called for c-1 (it was skipped!)
		verify(answerService, never()).ask(any(), eq(case1.question()), any());
		// ask WAS called for c-2
		verify(answerService, times(1)).ask(any(), eq(case2.question()), any());
	}

	@Test
	@DisplayName("SHA mismatch renames checkpoint to *.stale and starts fresh")
	void testShaMismatchStartsFreshAndRenamesToStale(@TempDir Path tempDir) throws IOException {
		AnswerService answerService = mock(AnswerService.class);
		AnswerJudge answerJudge = mock(AnswerJudge.class);
		HybridSearchService hybridSearchService = mock(HybridSearchService.class);

		GitContextProperties.AnswerEvalProperties evalConfig = new GitContextProperties.AnswerEvalProperties(
				"eval/datasets/test.jsonl",
				"tarunipriya/gitcontext",
				true, // reingest=true
				tempDir.toString(),
				0L,
				false,
				6000,
				0,
				3,
				true,
				"gemini-3.8-flash",
				true
		);

		GitContextProperties properties = new GitContextProperties(
				null, Path.of("./data/repos"), "http://localhost:6333", null, null, null, null, null, null, null, null, null, null, null, null, evalConfig
		);

		Path checkpointFile = tempDir.resolve("answers-sha-test.checkpoint.jsonl");

		// Checkpoint has old SHA "sha-old"
		AnswerCaseResult case1Result = new AnswerCaseResult(
				"c-1", "Q1", true, "Old Answer 1", false, "gemini-3.5-flash", List.of(), List.of("A.java"), 0.0, false, null, null, List.of(), 10L, 20L, 30L, List.of()
		);
		AnswerCheckpointEntry entry1 = AnswerCheckpointEntry.from(case1Result, "sha-old", "gemini-3.5-flash", "gemini-3.8-flash");
		Files.writeString(checkpointFile, JSON_MAPPER.writeValueAsString(entry1) + "\n", StandardCharsets.UTF_8);

		AnswerEvaluator evaluator = new AnswerEvaluator(answerService, answerJudge, hybridSearchService, properties);

		EvalCase case1 = new EvalCase("c-1", "Q1", List.of("A.java"), List.of(), "cat", true);
		EvalDataset dataset = new EvalDataset("sha-test", List.of(case1));
		RepoRef repoRef = RepoRef.parse("tarunipriya/gitcontext");

		AnswerResult newResult = new AnswerResult("New Answer 1", List.of(), List.of(), "gemini-3.5-flash", 10, 10, null, 15L, 25L, 40L, List.of());
		when(answerService.ask(any(), eq(case1.question()), any())).thenReturn(newResult);

		// Run with new SHA "sha-new"
		Optional<AnswerEvalReport> reportOpt = evaluator.evaluate(dataset, repoRef, "sha-new", evalConfig);

		assertThat(reportOpt).isPresent();
		assertThat(reportOpt.get().indexedCommitSha()).isEqualTo("sha-new");

		// Old checkpoint must have been renamed to .stale
		Path staleFile = tempDir.resolve("answers-sha-test.stale");
		assertThat(staleFile).exists();
		String staleContent = Files.readString(staleFile);
		assertThat(staleContent).contains("Old Answer 1");

		// ask WAS called because it started fresh
		verify(answerService, times(1)).ask(any(), eq(case1.question()), any());
	}

	@Test
	@DisplayName("Model mismatch renames checkpoint to *.stale and starts fresh")
	void testModelMismatchStartsFreshAndRenamesToStale(@TempDir Path tempDir) throws IOException {
		AnswerService answerService = mock(AnswerService.class);
		AnswerJudge answerJudge = mock(AnswerJudge.class);
		HybridSearchService hybridSearchService = mock(HybridSearchService.class);

		GitContextProperties.AnswerEvalProperties evalConfig = new GitContextProperties.AnswerEvalProperties(
				"eval/datasets/test.jsonl",
				"tarunipriya/gitcontext",
				false,
				tempDir.toString(),
				0L,
				false,
				6000,
				0,
				3,
				true,
				"gemini-3.8-flash",
				true
		);

		GitContextProperties properties = new GitContextProperties(
				null, Path.of("./data/repos"), "http://localhost:6333", null, null, null, null, null, null, null, null, null, null, null, null, evalConfig
		);

		Path checkpointFile = tempDir.resolve("answers-model-test.checkpoint.jsonl");

		// Checkpoint has different answer model "gemini-1.5-pro"
		AnswerCaseResult case1Result = new AnswerCaseResult(
				"c-1", "Q1", true, "Answer 1", false, "gemini-1.5-pro", List.of(), List.of("A.java"), 0.0, false, null, null, List.of(), 10L, 20L, 30L, List.of()
		);
		AnswerCheckpointEntry entry1 = AnswerCheckpointEntry.from(case1Result, "sha-same", "gemini-1.5-pro", "gemini-3.8-flash");
		Files.writeString(checkpointFile, JSON_MAPPER.writeValueAsString(entry1) + "\n", StandardCharsets.UTF_8);

		AnswerEvaluator evaluator = new AnswerEvaluator(answerService, answerJudge, hybridSearchService, properties);

		EvalCase case1 = new EvalCase("c-1", "Q1", List.of("A.java"), List.of(), "cat", true);
		EvalDataset dataset = new EvalDataset("model-test", List.of(case1));
		RepoRef repoRef = RepoRef.parse("tarunipriya/gitcontext");

		AnswerResult newResult = new AnswerResult("Fresh Answer 1", List.of(), List.of(), "gemini-3.5-flash", 10, 10, null, 15L, 25L, 40L, List.of());
		when(answerService.ask(any(), eq(case1.question()), any())).thenReturn(newResult);

		Optional<AnswerEvalReport> reportOpt = evaluator.evaluate(dataset, repoRef, "sha-same", evalConfig);

		assertThat(reportOpt).isPresent();

		// Old checkpoint must have been renamed to .stale
		Path staleFile = tempDir.resolve("answers-model-test.stale");
		assertThat(staleFile).exists();

		// ask WAS called because it started fresh
		verify(answerService, times(1)).ask(any(), eq(case1.question()), any());
	}

	@Test
	@DisplayName("Final report is returned only when all cases complete successfully")
	void testFinalReportOnlyWhenComplete(@TempDir Path tempDir) {
		AnswerService answerService = mock(AnswerService.class);
		AnswerJudge answerJudge = mock(AnswerJudge.class);
		HybridSearchService hybridSearchService = mock(HybridSearchService.class);

		GitContextProperties.AnswerEvalProperties evalConfig = new GitContextProperties.AnswerEvalProperties(
				"eval/datasets/test.jsonl",
				"tarunipriya/gitcontext",
				false,
				tempDir.toString(),
				0L,
				false,
				6000,
				0,
				3, // maxFailures = 3
				true,
				"gemini-3.8-flash",
				true
		);

		GitContextProperties properties = new GitContextProperties(
				null, Path.of("./data/repos"), "http://localhost:6333", null, null, null, null, null, null, null, null, null, null, null, null, evalConfig
		);

		AnswerEvaluator evaluator = new AnswerEvaluator(answerService, answerJudge, hybridSearchService, properties);

		EvalCase case1 = new EvalCase("c-1", "Q1", List.of("A.java"), List.of(), "cat", true);
		EvalCase case2 = new EvalCase("c-2", "Q2", List.of("B.java"), List.of(), "cat", true);
		EvalDataset dataset = new EvalDataset("complete-test", List.of(case1, case2));
		RepoRef repoRef = RepoRef.parse("tarunipriya/gitcontext");

		// Run 1: case 1 succeeds, case 2 fails with exception (1 failure < 3, so loop finishes without early abort, but incomplete)
		AnswerResult res1 = new AnswerResult("Ans 1", List.of(), List.of(), "gemini-3.5-flash", 10, 10, null, 10L, 20L, 30L, List.of());
		when(answerService.ask(any(), eq(case1.question()), any())).thenReturn(res1);
		when(answerService.ask(any(), eq(case2.question()), any())).thenThrow(new RuntimeException("Transient network timeout"));

		Optional<AnswerEvalReport> report1 = evaluator.evaluate(dataset, repoRef, "sha-complete", evalConfig);
		// Not all cases succeeded -> report must be empty
		assertThat(report1).isEmpty();

		// Checkpoint file exists with 1 success, 1 failure
		Path checkpointFile = tempDir.resolve("answers-complete-test.checkpoint.jsonl");
		assertThat(checkpointFile).exists();

		// Run 2: resume; case 2 now succeeds
		AnswerResult res2 = new AnswerResult("Ans 2", List.of(), List.of(), "gemini-3.5-flash", 10, 10, null, 12L, 22L, 34L, List.of());
		when(answerService.ask(any(), eq(case2.question()), any())).thenReturn(res2);

		Optional<AnswerEvalReport> report2 = evaluator.evaluate(dataset, repoRef, "sha-complete", evalConfig);
		// Now all cases succeeded -> final report is returned!
		assertThat(report2).isPresent();
		assertThat(report2.get().cases()).hasSize(2);
	}

	@Test
	@DisplayName("disableFallback=true passes gitcontext.llm.model as modelOverride to AnswerOptions")
	void testDisableFallbackPassesModelOverride(@TempDir Path tempDir) {
		AnswerService answerService = mock(AnswerService.class);
		AnswerJudge answerJudge = mock(AnswerJudge.class);
		HybridSearchService hybridSearchService = mock(HybridSearchService.class);

		GitContextProperties.AnswerEvalProperties evalConfig = new GitContextProperties.AnswerEvalProperties(
				"eval/datasets/test.jsonl",
				"tarunipriya/gitcontext",
				false,
				tempDir.toString(),
				0L,
				false,
				6000,
				0,
				3,
				true,
				"gemini-3.8-flash",
				true // disableFallback = true
		);

		GitContextProperties properties = new GitContextProperties(
				null, Path.of("./data/repos"), "http://localhost:6333", null, null, null, null, null, null, null, null, null, null, null, null, evalConfig
		);

		AnswerEvaluator evaluator = new AnswerEvaluator(answerService, answerJudge, hybridSearchService, properties);

		EvalCase case1 = new EvalCase("c-1", "Q1", List.of("A.java"), List.of(), "cat", true);
		EvalDataset dataset = new EvalDataset("override-test", List.of(case1));
		RepoRef repoRef = RepoRef.parse("tarunipriya/gitcontext");

		AnswerResult res1 = new AnswerResult("Ans 1", List.of(), List.of(), "gemini-3.5-flash", 10, 10, null, 10L, 20L, 30L, List.of());
		when(answerService.ask(any(), eq(case1.question()), any())).thenReturn(res1);

		evaluator.evaluate(dataset, repoRef, "sha-1", evalConfig);

		ArgumentCaptor<AnswerOptions> captor = ArgumentCaptor.forClass(AnswerOptions.class);
		verify(answerService).ask(any(), eq(case1.question()), captor.capture());
		assertThat(captor.getValue().modelOverride()).isEqualTo("gemini-3.5-flash");
	}

	@Test
	@DisplayName("formatCitedSourcesText divides max chars equally among cited sources with all headers appearing and each truncated")
	void testFormatCitedSourcesText_equalShareTruncation() {
		String longContent1 = "Alpha ".repeat(500);
		String longContent2 = "Beta ".repeat(500);
		String longContent3 = "Gamma ".repeat(500);

		SearchHit hit1 = new SearchHit("p1", 0.9, "repo", "run", "c1", "d1", 0, SourceType.CODE, ChunkType.METHOD,
				"backend/src/main/Alpha.java", "java", null, null, null, 1, 100, 500, null, null, longContent1, longContent1);
		SearchHit hit2 = new SearchHit("p2", 0.8, "repo", "run", "c2", "d2", 0, SourceType.CODE, ChunkType.METHOD,
				"backend/src/main/Beta.java", "java", null, null, null, 1, 100, 500, null, null, longContent2, longContent2);
		SearchHit hit3 = new SearchHit("p3", 0.7, "repo", "run", "c3", "d3", 0, SourceType.CODE, ChunkType.METHOD,
				"backend/src/main/Gamma.java", "java", null, null, null, 1, 100, 500, null, null, longContent3, longContent3);

		List<Source> sources = List.of(
				new Source(1, hit1, true),
				new Source(2, hit2, true),
				new Source(3, hit3, true)
		);

		// judgeMaxSourceChars = 3000 -> 3 cited sources -> perSource = 3000 / 3 = 1000
		String formatted = AnswerEvaluator.formatCitedSourcesText(sources, 3000);

		// All 3 headers appear
		assertThat(formatted).contains("[1] backend/src/main/Alpha.java:");
		assertThat(formatted).contains("[2] backend/src/main/Beta.java:");
		assertThat(formatted).contains("[3] backend/src/main/Gamma.java:");

		// Each is truncated
		int occurrences = 0;
		int idx = 0;
		while ((idx = formatted.indexOf("…[truncated]", idx)) != -1) {
			occurrences++;
			idx += "…[truncated]".length();
		}
		assertThat(occurrences).isEqualTo(3);
	}

	@Test
	@DisplayName("judge fails -> entry not complete, resume calls only the judge without calling AnswerService again")
	void testJudgeFails_entryNotComplete_resumeCallsOnlyJudge(@TempDir Path tempDir) {
		AnswerService answerService = mock(AnswerService.class);
		AnswerJudge answerJudge = mock(AnswerJudge.class);
		HybridSearchService hybridSearchService = mock(HybridSearchService.class);

		GitContextProperties.AnswerEvalProperties evalConfig = new GitContextProperties.AnswerEvalProperties(
				"eval/datasets/test.jsonl",
				"tarunipriya/gitcontext",
				false,
				tempDir.toString(),
				0L,
				true,
				6000,
				0,
				3,
				true,
				"gemini-3.8-flash",
				true
		);

		GitContextProperties properties = new GitContextProperties(
				null, Path.of("./data/repos"), "http://localhost:6333", null, null, null, null, null, null, null, null, null, null, null, null, evalConfig
		);

		AnswerEvaluator evaluator = new AnswerEvaluator(answerService, answerJudge, hybridSearchService, properties);

		EvalCase evalCase = new EvalCase("c-judge-retry", "What is the entry point?", List.of("src/Main.java"), List.of("Main.java has main()"), "core", true);
		EvalDataset dataset = new EvalDataset("judge-retry-benchmark", List.of(evalCase));
		RepoRef repoRef = RepoRef.parse("tarunipriya/gitcontext");

		SearchHit hit = createHit("src/Main.java", SourceType.CODE, null, null);
		AnswerResult answerResult = new AnswerResult(
				"The main method is in src/Main.java [1].",
				List.of(),
				List.of(new Source(1, hit, true)),
				"gemini-3.5-flash",
				50,
				20,
				null,
				10L,
				20L,
				30L,
				List.of()
		);

		when(answerService.ask(any(), eq(evalCase.question()), any())).thenReturn(answerResult);
		// Run 1: judge fails (returns empty verdict)
		when(answerJudge.judge(eq(evalCase.question()), eq(evalCase.expectedFacts()), eq(answerResult.answer()), anyString(), anyString()))
				.thenReturn(Optional.empty());

		Optional<AnswerEvalReport> run1Report = evaluator.evaluate(dataset, repoRef, "sha-judge", evalConfig);
		assertThat(run1Report).isEmpty();

		// Checkpoint has entry with answer, judgeRequired=true, but factCoverage=null -> not complete!
		Path checkpointFile = tempDir.resolve("answers-judge-retry-benchmark.checkpoint.jsonl");
		assertThat(checkpointFile).exists();
		List<AnswerCheckpointEntry> entriesRun1 = AnswerEvaluator.readCheckpointEntries(checkpointFile);
		assertThat(entriesRun1).hasSize(1);
		AnswerCheckpointEntry run1Entry = entriesRun1.get(0);
		assertThat(run1Entry.isComplete()).isFalse();
		assertThat(run1Entry.judgeRequired()).isTrue();
		assertThat(run1Entry.factCoverage()).isNull();
		assertThat(run1Entry.citedSourcesText()).contains("src/Main.java");

		verify(answerService, times(1)).ask(any(), eq(evalCase.question()), any());

		// Run 2: resume! Judge now succeeds
		JudgeVerdict verdict = new JudgeVerdict(List.of(true), true, List.of());
		when(answerJudge.judge(eq(evalCase.question()), eq(evalCase.expectedFacts()), eq(answerResult.answer()), eq(run1Entry.citedSourcesText()), anyString()))
				.thenReturn(Optional.of(verdict));

		Optional<AnswerEvalReport> run2Report = evaluator.evaluate(dataset, repoRef, "sha-judge", evalConfig);
		assertThat(run2Report).isPresent();

		// Crucial verification: AnswerService was NOT called again! (still 1 call from Run 1)
		verify(answerService, times(1)).ask(any(), eq(evalCase.question()), any());

		// AnswerJudge was called again on resume with the stored cited sources text
		verify(answerJudge, times(2)).judge(eq(evalCase.question()), eq(evalCase.expectedFacts()), eq(answerResult.answer()), anyString(), anyString());

		AnswerEvalReport report = run2Report.get();
		assertThat(report.cases()).hasSize(1);
		assertThat(report.cases().get(0).factCoverage()).isEqualTo(1.0);
		assertThat(report.cases().get(0).grounded()).isTrue();
		assertThat(report.judgeFailures()).isEqualTo(0);
	}

	@Test
	@DisplayName("3 consecutive judge failures abort the evaluation loop without generating a final report")
	void testThreeConsecutiveJudgeFailuresAbort(@TempDir Path tempDir) {
		AnswerService answerService = mock(AnswerService.class);
		AnswerJudge answerJudge = mock(AnswerJudge.class);
		HybridSearchService hybridSearchService = mock(HybridSearchService.class);

		GitContextProperties.AnswerEvalProperties evalConfig = new GitContextProperties.AnswerEvalProperties(
				"eval/datasets/test.jsonl",
				"tarunipriya/gitcontext",
				false,
				tempDir.toString(),
				0L,
				true,
				6000,
				0,
				3, // maxConsecutiveFailures = 3
				true,
				"gemini-3.8-flash",
				true
		);

		GitContextProperties properties = new GitContextProperties(
				null, Path.of("./data/repos"), "http://localhost:6333", null, null, null, null, null, null, null, null, null, null, null, null, evalConfig
		);

		AnswerEvaluator evaluator = new AnswerEvaluator(answerService, answerJudge, hybridSearchService, properties);

		List<EvalCase> cases = List.of(
				new EvalCase("c-1", "Q1", List.of("A.java"), List.of("Fact 1"), "cat", true),
				new EvalCase("c-2", "Q2", List.of("B.java"), List.of("Fact 2"), "cat", true),
				new EvalCase("c-3", "Q3", List.of("C.java"), List.of("Fact 3"), "cat", true),
				new EvalCase("c-4", "Q4", List.of("D.java"), List.of("Fact 4"), "cat", true)
		);
		EvalDataset dataset = new EvalDataset("judge-abort-benchmark", cases);
		RepoRef repoRef = RepoRef.parse("tarunipriya/gitcontext");

		when(answerService.ask(any(), anyString(), any())).thenReturn(
				new AnswerResult("Generated answer", List.of(), List.of(), "gemini-3.5-flash", 10, 10, null, 10L, 20L, 30L, List.of())
		);
		// Judge fails every time
		when(answerJudge.judge(anyString(), any(), anyString(), anyString(), anyString()))
				.thenReturn(Optional.empty());

		Optional<AnswerEvalReport> reportOpt = evaluator.evaluate(dataset, repoRef, "sha-failfast-judge", evalConfig);

		assertThat(reportOpt).isEmpty();

		// Only 3 attempts must be made before aborting (c-4 never evaluated)
		verify(answerJudge, times(3)).judge(anyString(), any(), anyString(), anyString(), anyString());
		verify(answerService, times(3)).ask(any(), anyString(), any());

		Path checkpointFile = tempDir.resolve("answers-judge-abort-benchmark.checkpoint.jsonl");
		assertThat(checkpointFile).exists();
		List<AnswerCheckpointEntry> entries = AnswerEvaluator.readCheckpointEntries(checkpointFile);
		assertThat(entries).hasSize(3);
		assertThat(entries).allMatch(e -> !e.isComplete());
	}

	@Test
	@DisplayName("last entry wins when reading checkpoint with duplicate caseIds")
	void testLastEntryWinsForDuplicateCaseIds(@TempDir Path tempDir) throws IOException {
		AnswerService answerService = mock(AnswerService.class);
		AnswerJudge answerJudge = mock(AnswerJudge.class);
		HybridSearchService hybridSearchService = mock(HybridSearchService.class);

		GitContextProperties.AnswerEvalProperties evalConfig = new GitContextProperties.AnswerEvalProperties(
				"eval/datasets/test.jsonl",
				"tarunipriya/gitcontext",
				false,
				tempDir.toString(),
				0L,
				true,
				6000,
				0,
				3,
				true,
				"gemini-3.8-flash",
				true
		);

		GitContextProperties properties = new GitContextProperties(
				null, Path.of("./data/repos"), "http://localhost:6333", null, null, null, null, null, null, null, null, null, null, null, null, evalConfig
		);

		Path checkpointFile = tempDir.resolve("answers-duplicate-benchmark.checkpoint.jsonl");

		// Line 1: incomplete entry (failed judge)
		AnswerCheckpointEntry entry1 = new AnswerCheckpointEntry(
				"c-dup", "Q1", true, "Answer 1", false, "gemini-3.5-flash",
				List.of("A.java"), List.of("A.java"), 1.0, true, null, null, List.of(),
				10L, 20L, 30L, List.of("Judge failed"), "sha-dup", "gemini-3.5-flash", "gemini-3.8-flash",
				true, "Source [1]"
		);

		// Line 2: complete entry for same caseId (retry succeeded)
		AnswerCheckpointEntry entry2 = new AnswerCheckpointEntry(
				"c-dup", "Q1", true, "Answer 1", false, "gemini-3.5-flash",
				List.of("A.java"), List.of("A.java"), 1.0, true, 1.0, true, List.of(),
				10L, 20L, 30L, List.of(), "sha-dup", "gemini-3.5-flash", "gemini-3.8-flash",
				true, "Source [1]"
		);

		Files.writeString(checkpointFile,
				JSON_MAPPER.writeValueAsString(entry1) + "\n" + JSON_MAPPER.writeValueAsString(entry2) + "\n",
				StandardCharsets.UTF_8
		);

		AnswerEvaluator evaluator = new AnswerEvaluator(answerService, answerJudge, hybridSearchService, properties);

		EvalCase evalCase = new EvalCase("c-dup", "Q1", List.of("A.java"), List.of("Fact 1"), "cat", true);
		EvalDataset dataset = new EvalDataset("duplicate-benchmark", List.of(evalCase));
		RepoRef repoRef = RepoRef.parse("tarunipriya/gitcontext");

		Optional<AnswerEvalReport> reportOpt = evaluator.evaluate(dataset, repoRef, "sha-dup", evalConfig);

		assertThat(reportOpt).isPresent();
		AnswerEvalReport report = reportOpt.get();
		assertThat(report.cases()).hasSize(1);
		assertThat(report.cases().get(0).factCoverage()).isEqualTo(1.0);
		assertThat(report.cases().get(0).grounded()).isTrue();

		// Neither answerService nor answerJudge should have been called because entry2 was complete!
		verify(answerService, never()).ask(any(), anyString(), any());
		verify(answerJudge, never()).judge(anyString(), any(), anyString(), anyString(), anyString());
	}

	@Test
	@DisplayName("judge-model change keeps answers and re-judges them with only the judge called (AnswerService not called for those cases)")
	void testJudgeModelChangeKeepsAnswersAndCallsOnlyJudge(@TempDir Path tempDir) throws IOException {
		AnswerService answerService = mock(AnswerService.class);
		AnswerJudge answerJudge = mock(AnswerJudge.class);
		HybridSearchService hybridSearchService = mock(HybridSearchService.class);

		GitContextProperties.AnswerEvalProperties evalConfig = new GitContextProperties.AnswerEvalProperties(
				"eval/datasets/test.jsonl",
				"tarunipriya/gitcontext",
				false,
				tempDir.toString(),
				0L,
				true,
				6000,
				0,
				3,
				true,
				"gemini-3.5-flash-lite",
				true
		);

		GitContextProperties properties = new GitContextProperties(
				null, Path.of("./data/repos"), "http://localhost:6333", null, null, null, null, null, null, null, null, null, null, null, null, evalConfig
		);

		Path checkpointFile = tempDir.resolve("answers-judge-change.checkpoint.jsonl");

		// Saved checkpoint has 2 answered cases, previously evaluated with judgeModel "gemini-3.8-flash"
		AnswerCheckpointEntry entry1 = new AnswerCheckpointEntry(
				"c-1", "Q1", true, "Saved answer 1", false, "gemini-3.5-flash",
				List.of("A.java"), List.of("A.java"), 1.0, true, 1.0, true, List.of(),
				10L, 20L, 30L, List.of(), "sha-match", "gemini-3.5-flash", "gemini-3.8-flash",
				true, "[1] A.java: content 1"
		);
		AnswerCheckpointEntry entry2 = new AnswerCheckpointEntry(
				"c-2", "Q2", true, "Saved answer 2", false, "gemini-3.5-flash",
				List.of("B.java"), List.of("B.java"), 1.0, true, 0.5, false, List.of("Old claim"),
				15L, 25L, 40L, List.of(), "sha-match", "gemini-3.5-flash", "gemini-3.8-flash",
				true, "[2] B.java: content 2"
		);

		Files.writeString(
				checkpointFile,
				JSON_MAPPER.writeValueAsString(entry1) + "\n" + JSON_MAPPER.writeValueAsString(entry2) + "\n",
				StandardCharsets.UTF_8
		);

		AnswerEvaluator evaluator = new AnswerEvaluator(answerService, answerJudge, hybridSearchService, properties);

		EvalCase case1 = new EvalCase("c-1", "Q1", List.of("A.java"), List.of("Fact 1"), "cat", true);
		EvalCase case2 = new EvalCase("c-2", "Q2", List.of("B.java"), List.of("Fact 2"), "cat", true);
		EvalDataset dataset = new EvalDataset("judge-change", List.of(case1, case2));
		RepoRef repoRef = RepoRef.parse("tarunipriya/gitcontext");

		when(answerJudge.judge(eq("Q1"), eq(case1.expectedFacts()), eq("Saved answer 1"), eq("[1] A.java: content 1"), eq("gemini-3.5-flash-lite")))
				.thenReturn(Optional.of(new JudgeVerdict(List.of(true), true, List.of())));
		when(answerJudge.judge(eq("Q2"), eq(case2.expectedFacts()), eq("Saved answer 2"), eq("[2] B.java: content 2"), eq("gemini-3.5-flash-lite")))
				.thenReturn(Optional.of(new JudgeVerdict(List.of(true), true, List.of())));

		Optional<AnswerEvalReport> reportOpt = evaluator.evaluate(dataset, repoRef, "sha-match", evalConfig);

		assertThat(reportOpt).isPresent();
		AnswerEvalReport report = reportOpt.get();
		assertThat(report.judgeModel()).isEqualTo("gemini-3.5-flash-lite");
		assertThat(report.cases()).hasSize(2);
		assertThat(report.cases().get(0).answer()).isEqualTo("Saved answer 1");
		assertThat(report.cases().get(1).answer()).isEqualTo("Saved answer 2");
		assertThat(report.cases().get(0).factCoverage()).isEqualTo(1.0);
		assertThat(report.cases().get(1).factCoverage()).isEqualTo(1.0);

		// AnswerService was NEVER called because answers were preserved!
		verify(answerService, never()).ask(any(), anyString(), any());

		// Judge was called for both cases with the new judge model!
		verify(answerJudge, times(1)).judge(eq("Q1"), eq(case1.expectedFacts()), eq("Saved answer 1"), anyString(), eq("gemini-3.5-flash-lite"));
		verify(answerJudge, times(1)).judge(eq("Q2"), eq(case2.expectedFacts()), eq("Saved answer 2"), anyString(), eq("gemini-3.5-flash-lite"));

		// Stale file was NOT created
		assertThat(tempDir.resolve("answers-judge-change.stale")).doesNotExist();

		// Checkpoint file now has 4 entries (2 old + 2 re-judged entries with new judge model)
		List<AnswerCheckpointEntry> updatedEntries = AnswerEvaluator.readCheckpointEntries(checkpointFile);
		assertThat(updatedEntries).hasSize(4);
		assertThat(updatedEntries.get(2).judgeModel()).isEqualTo("gemini-3.5-flash-lite");
		assertThat(updatedEntries.get(3).judgeModel()).isEqualTo("gemini-3.5-flash-lite");
	}

	@Test
	@DisplayName("an old verdict from a different judge model is not counted")
	void testOldVerdictFromDifferentJudgeModelNotCounted(@TempDir Path tempDir) throws IOException {
		AnswerService answerService = mock(AnswerService.class);
		AnswerJudge answerJudge = mock(AnswerJudge.class);
		HybridSearchService hybridSearchService = mock(HybridSearchService.class);

		GitContextProperties.AnswerEvalProperties evalConfig = new GitContextProperties.AnswerEvalProperties(
				"eval/datasets/test.jsonl",
				"tarunipriya/gitcontext",
				false,
				tempDir.toString(),
				0L,
				true,
				6000,
				0,
				3,
				true,
				"gemini-3.5-flash-lite",
				true
		);

		GitContextProperties properties = new GitContextProperties(
				null, Path.of("./data/repos"), "http://localhost:6333", null, null, null, null, null, null, null, null, null, null, null, null, evalConfig
		);

		Path checkpointFile = tempDir.resolve("answers-old-verdict.checkpoint.jsonl");

		// Old entry had factCoverage=1.0 and grounded=true with old judge gemini-3.8-flash
		AnswerCheckpointEntry oldEntry = new AnswerCheckpointEntry(
				"c-1", "Q1", true, "Answer 1", false, "gemini-3.5-flash",
				List.of("A.java"), List.of("A.java"), 1.0, true, 1.0, true, List.of(),
				10L, 20L, 30L, List.of(), "sha-match", "gemini-3.5-flash", "gemini-3.8-flash",
				true, "[1] A.java: content 1"
		);

		Files.writeString(checkpointFile, JSON_MAPPER.writeValueAsString(oldEntry) + "\n", StandardCharsets.UTF_8);

		AnswerEvaluator evaluator = new AnswerEvaluator(answerService, answerJudge, hybridSearchService, properties);

		EvalCase evalCase = new EvalCase("c-1", "Q1", List.of("A.java"), List.of("Fact 1", "Fact 2"), "cat", true);
		EvalDataset dataset = new EvalDataset("old-verdict", List.of(evalCase));
		RepoRef repoRef = RepoRef.parse("tarunipriya/gitcontext");

		// When new judge evaluates, it produces a different verdict (e.g. 0.5 coverage instead of old 1.0, and ungrounded)
		when(answerJudge.judge(eq("Q1"), eq(evalCase.expectedFacts()), eq("Answer 1"), eq("[1] A.java: content 1"), eq("gemini-3.5-flash-lite")))
				.thenReturn(Optional.of(new JudgeVerdict(List.of(true, false), false, List.of("Ungrounded claim"))));

		Optional<AnswerEvalReport> reportOpt = evaluator.evaluate(dataset, repoRef, "sha-match", evalConfig);

		assertThat(reportOpt).isPresent();
		AnswerEvalReport report = reportOpt.get();
		assertThat(report.judgeModel()).isEqualTo("gemini-3.5-flash-lite");
		// Old verdict 1.0 was NOT counted: new verdict 0.5 must be reported
		assertThat(report.meanFactCoverage()).isEqualTo(0.5);
		assertThat(report.groundedRate()).isEqualTo(0.0);
		assertThat(report.cases().get(0).factCoverage()).isEqualTo(0.5);
		assertThat(report.cases().get(0).grounded()).isFalse();
		assertThat(report.cases().get(0).unsupportedClaims()).containsExactly("Ungrounded claim");
	}

	@Test
	@DisplayName("an old verdict from a different judge model is not counted when new judge fails")
	void testOldVerdictFromDifferentJudgeModelNotCountedWhenJudgeFails(@TempDir Path tempDir) throws IOException {
		AnswerService answerService = mock(AnswerService.class);
		AnswerJudge answerJudge = mock(AnswerJudge.class);
		HybridSearchService hybridSearchService = mock(HybridSearchService.class);

		GitContextProperties.AnswerEvalProperties evalConfig = new GitContextProperties.AnswerEvalProperties(
				"eval/datasets/test.jsonl",
				"tarunipriya/gitcontext",
				false,
				tempDir.toString(),
				0L,
				true,
				6000,
				0,
				3,
				true,
				"gemini-3.5-flash-lite",
				true
		);

		GitContextProperties properties = new GitContextProperties(
				null, Path.of("./data/repos"), "http://localhost:6333", null, null, null, null, null, null, null, null, null, null, null, null, evalConfig
		);

		Path checkpointFile = tempDir.resolve("answers-judge-fails.checkpoint.jsonl");

		// Old entry had factCoverage=1.0 and grounded=true with old judge gemini-3.8-flash
		AnswerCheckpointEntry oldEntry = new AnswerCheckpointEntry(
				"c-1", "Q1", true, "Answer 1", false, "gemini-3.5-flash",
				List.of("A.java"), List.of("A.java"), 1.0, true, 1.0, true, List.of(),
				10L, 20L, 30L, List.of(), "sha-match", "gemini-3.5-flash", "gemini-3.8-flash",
				true, "[1] A.java: content 1"
		);

		Files.writeString(checkpointFile, JSON_MAPPER.writeValueAsString(oldEntry) + "\n", StandardCharsets.UTF_8);

		AnswerEvaluator evaluator = new AnswerEvaluator(answerService, answerJudge, hybridSearchService, properties);

		EvalCase evalCase = new EvalCase("c-1", "Q1", List.of("A.java"), List.of("Fact 1"), "cat", true);
		EvalDataset dataset = new EvalDataset("judge-fails", List.of(evalCase));
		RepoRef repoRef = RepoRef.parse("tarunipriya/gitcontext");

		// New judge fails (returns empty)
		when(answerJudge.judge(eq("Q1"), any(), eq("Answer 1"), any(), eq("gemini-3.5-flash-lite")))
				.thenReturn(Optional.empty());

		Optional<AnswerEvalReport> reportOpt = evaluator.evaluate(dataset, repoRef, "sha-match", evalConfig);

		// Old verdict was NOT counted to claim completion! Evaluation is incomplete.
		assertThat(reportOpt).isEmpty();

		List<AnswerCheckpointEntry> updated = AnswerEvaluator.readCheckpointEntries(checkpointFile);
		assertThat(updated).hasSize(2);
		assertThat(updated.get(1).judgeModel()).isEqualTo("gemini-3.5-flash-lite");
		assertThat(updated.get(1).factCoverage()).isNull();
		assertThat(updated.get(1).isComplete()).isFalse();
	}
}
