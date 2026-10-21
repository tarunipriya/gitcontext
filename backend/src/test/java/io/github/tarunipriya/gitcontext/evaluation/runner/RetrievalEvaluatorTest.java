package io.github.tarunipriya.gitcontext.evaluation.runner;

import io.github.tarunipriya.gitcontext.evaluation.MetricSummary;
import io.github.tarunipriya.gitcontext.evaluation.dataset.EvalCase;
import io.github.tarunipriya.gitcontext.evaluation.dataset.EvalDataset;
import io.github.tarunipriya.gitcontext.indexing.SearchHit;
import io.github.tarunipriya.gitcontext.model.ChunkType;
import io.github.tarunipriya.gitcontext.model.RepoRef;
import io.github.tarunipriya.gitcontext.model.SourceType;
import io.github.tarunipriya.gitcontext.retrieval.HybridSearchHit;
import io.github.tarunipriya.gitcontext.retrieval.HybridSearchResult;
import io.github.tarunipriya.gitcontext.retrieval.HybridSearchService;
import io.github.tarunipriya.gitcontext.retrieval.SearchMode;
import java.util.List;
import java.util.Set;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RetrievalEvaluatorTest {

	@Mock
	private HybridSearchService searchService;

	private final RepoRef repo = RepoRef.parse("tarunipriya/gitcontext");

	private HybridSearchResult createResult(List<String> paths, boolean reranked, long tookMillis) {
		List<HybridSearchHit> hits = paths.stream()
				.map(p -> new HybridSearchHit(
						new SearchHit(
								"hit-" + p,
								0.85,
								"tarunipriya/gitcontext",
								"run-1",
								"chunk-1",
								"doc-1",
								0,
								SourceType.CODE,
								ChunkType.CLASS,
								p,
								"java",
								"ClassName",
								null,
								null,
								1,
								20,
								50,
								null,
								"sha123",
								"content",
								"content"
						),
						0.8,
						0.8,
						1,
						0.8,
						1,
						reranked ? 0.95 : null,
						1
				))
				.toList();
		return new HybridSearchResult(
				hits,
				SearchMode.HYBRID,
				reranked,
				List.of(),
				10L,
				10L,
				5L,
				reranked ? 20L : 0L,
				tookMillis
		);
	}

	@Test
	@DisplayName("Computes metrics per variant from canned hits across multiple cutoff ks")
	void testMetricsPerVariantFromCannedHits() {
		EvalCase case1 = new EvalCase("case-001", "q1", List.of("src/A.java"), List.of("fact1"), "cat", true);
		EvalCase case2 = new EvalCase("case-002", "q2", List.of("src/B.java"), List.of("fact2"), "cat", true);
		EvalDataset dataset = new EvalDataset("test-ds", List.of(case1, case2));

		// VECTOR variant:
		// q1 returns ["src/A.java"] -> Hit@1 = 1.0
		// q2 returns ["src/Other.java", "src/B.java"] -> Hit@1 = 0.0, Hit@3 = 1.0
		when(searchService.search(eq(repo), eq("q1"), eq(3), any(), any(), eq(SearchMode.VECTOR), eq(false), eq(false)))
				.thenReturn(createResult(List.of("src/A.java"), false, 40L));
		when(searchService.search(eq(repo), eq("q2"), eq(3), any(), any(), eq(SearchMode.VECTOR), eq(false), eq(false)))
				.thenReturn(createResult(List.of("src/Other.java", "src/B.java"), false, 60L));

		RetrievalEvaluator evaluator = new RetrievalEvaluator(searchService);
		EvalReport report = evaluator.evaluate(
				dataset,
				repo,
				List.of("src/A.java", "src/B.java", "src/Other.java"),
				"commit1",
				List.of(EvalVariant.VECTOR),
				List.of(1, 3),
				false,
				0
		);

		assertThat(report.answerableCases()).isEqualTo(2);
		assertThat(report.variants()).hasSize(1);

		VariantReport vr = report.variants().get(0);
		assertThat(vr.variant()).isEqualTo(EvalVariant.VECTOR);
		assertThat(vr.cases()).hasSize(2);

		MetricSummary summaryK1 = vr.summaryByK().get(1);
		assertThat(summaryK1.queryCount()).isEqualTo(2);
		assertThat(summaryK1.meanHitRateAtK()).isEqualTo(0.5); // (1.0 + 0.0) / 2

		MetricSummary summaryK3 = vr.summaryByK().get(3);
		assertThat(summaryK3.queryCount()).isEqualTo(2);
		assertThat(summaryK3.meanHitRateAtK()).isEqualTo(1.0); // (1.0 + 1.0) / 2

		assertThat(vr.latency().count()).isEqualTo(2);
		assertThat(vr.latency().minMs()).isEqualTo(40L);
		assertThat(vr.latency().maxMs()).isEqualTo(60L);
		assertThat(vr.latency().meanMs()).isEqualTo(50.0);
	}

	@Test
	@DisplayName("Warmup searches are executed with first question and are NOT recorded in report")
	void testWarmupSearchesNotRecorded() {
		EvalCase case1 = new EvalCase("case-001", "q1", List.of("src/A.java"), List.of(), "cat", true);
		EvalCase case2 = new EvalCase("case-002", "q2", List.of("src/B.java"), List.of(), "cat", true);
		EvalDataset dataset = new EvalDataset("warmup-ds", List.of(case1, case2));

		when(searchService.search(any(), any(), anyInt(), any(), any(), any(), anyBoolean(), anyBoolean()))
				.thenReturn(createResult(List.of("src/A.java"), false, 30L));

		RetrievalEvaluator evaluator = new RetrievalEvaluator(searchService);
		EvalReport report = evaluator.evaluate(
				dataset,
				repo,
				List.of("src/A.java", "src/B.java"),
				"sha",
				List.of(EvalVariant.VECTOR),
				List.of(1),
				false,
				2 // 2 warmup searches requested
		);

		// Total searches: 2 warmup + 2 cases = 4 calls to searchService
		verify(searchService, times(4))
				.search(eq(repo), any(), eq(1), any(), any(), eq(SearchMode.VECTOR), eq(false), eq(false));

		VariantReport vr = report.variants().get(0);
		// Warmup searches must NOT be present in case results or latency stats
		assertThat(vr.cases()).hasSize(2);
		assertThat(vr.latency().count()).isEqualTo(2);
	}

	@Test
	@DisplayName("Unanswerable cases are counted as skipped and never queried")
	void testUnanswerableCasesSkipped() {
		EvalCase ans1 = new EvalCase("ans-1", "q-ans-1", List.of("src/Main.java"), List.of(), "cat", true);
		EvalCase ans2 = new EvalCase("ans-2", "q-ans-2", List.of("src/Util.java"), List.of(), "cat", true);
		EvalCase unans1 = new EvalCase("unans-1", "q-unans-1", List.of(), List.of(), "cat", false);
		EvalCase unans2 = new EvalCase("unans-2", "q-unans-2", List.of(), List.of(), "cat", false);
		EvalDataset dataset = new EvalDataset("mixed-ds", List.of(ans1, unans1, ans2, unans2));

		when(searchService.search(any(), any(), anyInt(), any(), any(), any(), anyBoolean(), anyBoolean()))
				.thenReturn(createResult(List.of("src/Main.java"), false, 25L));

		RetrievalEvaluator evaluator = new RetrievalEvaluator(searchService);
		EvalReport report = evaluator.evaluate(
				dataset,
				repo,
				List.of("src/Main.java", "src/Util.java"),
				"sha",
				List.of(EvalVariant.KEYWORD),
				List.of(5),
				false,
				0
		);

		assertThat(report.answerableCases()).isEqualTo(2);
		assertThat(report.skippedUnanswerable()).isEqualTo(2);

		// Verified that only answerable questions were searched
		verify(searchService, times(1)).search(eq(repo), eq("q-ans-1"), anyInt(), any(), any(), any(), anyBoolean(), anyBoolean());
		verify(searchService, times(1)).search(eq(repo), eq("q-ans-2"), anyInt(), any(), any(), any(), anyBoolean(), anyBoolean());
		verify(searchService, times(0)).search(eq(repo), eq("q-unans-1"), anyInt(), any(), any(), any(), anyBoolean(), anyBoolean());
		verify(searchService, times(0)).search(eq(repo), eq("q-unans-2"), anyInt(), any(), any(), any(), anyBoolean(), anyBoolean());
	}

	@Test
	@DisplayName("Missing expected paths are detected; cases with all missing paths get warning")
	void testMissingExpectedPathsDetected() {
		EvalCase case1 = new EvalCase("case-001", "q1", List.of("src/Existing.java", "src/Missing1.java"), List.of(), "cat", true);
		EvalCase case2 = new EvalCase("case-002", "q2", List.of("src/Missing2.java"), List.of(), "cat", true);
		EvalDataset dataset = new EvalDataset("missing-ds", List.of(case1, case2));

		when(searchService.search(any(), any(), anyInt(), any(), any(), any(), anyBoolean(), anyBoolean()))
				.thenReturn(createResult(List.of("src/Existing.java"), false, 30L));

		RetrievalEvaluator evaluator = new RetrievalEvaluator(searchService);
		EvalReport report = evaluator.evaluate(
				dataset,
				repo,
				List.of("src/Existing.java"),
				"sha",
				List.of(EvalVariant.HYBRID),
				List.of(1),
				false,
				0
		);

		// Missing paths detected
		assertThat(report.missingExpectedPaths()).containsExactlyInAnyOrder("src/Missing1.java", "src/Missing2.java");

		VariantReport vr = report.variants().get(0);
		CaseResult cr1 = vr.cases().get(0);
		CaseResult cr2 = vr.cases().get(1);

		// Case 1 has 1 existing path, so not ALL paths are missing
		assertThat(cr1.warnings()).noneMatch(w -> w.contains("All expected paths are missing"));

		// Case 2 has only missing paths, so gets a warning
		assertThat(cr2.warnings()).anyMatch(w -> w.contains("All expected paths are missing"));
	}

	@Test
	@DisplayName("A search exception for one case records empty results, zero metrics, warning, and run continues")
	void testThrowingSearchContinuesRun() {
		EvalCase case1 = new EvalCase("case-001", "q1", List.of("src/A.java"), List.of(), "cat", true);
		EvalCase case2 = new EvalCase("case-002", "q2", List.of("src/B.java"), List.of(), "cat", true);
		EvalDataset dataset = new EvalDataset("throw-ds", List.of(case1, case2));

		when(searchService.search(eq(repo), eq("q1"), anyInt(), any(), any(), any(), anyBoolean(), anyBoolean()))
				.thenReturn(createResult(List.of("src/A.java"), false, 50L));
		when(searchService.search(eq(repo), eq("q2"), anyInt(), any(), any(), any(), anyBoolean(), anyBoolean()))
				.thenThrow(new RuntimeException("Qdrant connection timeout"));

		RetrievalEvaluator evaluator = new RetrievalEvaluator(searchService);
		EvalReport report = evaluator.evaluate(
				dataset,
				repo,
				List.of("src/A.java", "src/B.java"),
				"sha",
				List.of(EvalVariant.VECTOR),
				List.of(1, 5),
				false,
				0
		);

		VariantReport vr = report.variants().get(0);
		assertThat(vr.cases()).hasSize(2);
		assertThat(vr.failedCases()).isEqualTo(1);
		assertThat(vr.latency().count()).isEqualTo(1);
		assertThat(vr.latency().meanMs()).isEqualTo(50.0);

		CaseResult failedCase = vr.cases().get(1);
		assertThat(failedCase.caseId()).isEqualTo("case-002");
		assertThat(failedCase.retrievedPaths()).isEmpty();
		assertThat(failedCase.tookMillis()).isEqualTo(0L);
		assertThat(failedCase.warnings()).anyMatch(w -> w.contains("Qdrant connection timeout"));
		assertThat(failedCase.metricsByK().get(1).hitRateAtK()).isEqualTo(0.0);
		assertThat(failedCase.metricsByK().get(5).hitRateAtK()).isEqualTo(0.0);
	}

	@Test
	@DisplayName("Validates cutoff ranks ks: non-empty, every k > 0, duplicates removed")
	void testValidateKs() {
		RetrievalEvaluator evaluator = new RetrievalEvaluator(searchService);
		EvalDataset dataset = new EvalDataset("ds", List.of(new EvalCase("c1", "q1", List.of("src/A.java"), List.of(), "cat", true)));

		// 1. Empty ks throws
		org.assertj.core.api.Assertions.assertThatThrownBy(() -> evaluator.evaluate(
				dataset, repo, List.of("src/A.java"), "sha", List.of(EvalVariant.VECTOR), List.of(), false, 0))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Cutoff ranks ks must not be empty");

		// 2. Non-positive k throws
		org.assertj.core.api.Assertions.assertThatThrownBy(() -> evaluator.evaluate(
				dataset, repo, List.of("src/A.java"), "sha", List.of(EvalVariant.VECTOR), List.of(1, 0, 5), false, 0))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Cutoff rank k must be greater than 0");

		org.assertj.core.api.Assertions.assertThatThrownBy(() -> evaluator.evaluate(
				dataset, repo, List.of("src/A.java"), "sha", List.of(EvalVariant.VECTOR), List.of(-3), false, 0))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Cutoff rank k must be greater than 0");

		// 3. Duplicates removed
		when(searchService.search(any(), any(), anyInt(), any(), any(), any(), anyBoolean(), anyBoolean()))
				.thenReturn(createResult(List.of("src/A.java"), false, 20L));

		EvalReport report = evaluator.evaluate(
				dataset, repo, List.of("src/A.java"), "sha", List.of(EvalVariant.VECTOR), List.of(3, 1, 3, 1), false, 0);

		VariantReport vr = report.variants().get(0);
		assertThat(vr.summaryByK().keySet()).containsExactly(1, 3);
	}

	@Test
	@DisplayName("HYBRID_RERANK with reranked=false adds warning to case results without failing")
	void testHybridRerankWithoutRerankedEmitsWarning() {
		EvalCase case1 = new EvalCase("case-001", "q1", List.of("src/A.java"), List.of(), "cat", true);
		EvalDataset dataset = new EvalDataset("rerank-ds", List.of(case1));

		// Returns reranked = false
		when(searchService.search(any(), any(), anyInt(), any(), any(), eq(SearchMode.HYBRID), eq(true), anyBoolean()))
				.thenReturn(createResult(List.of("src/A.java"), false, 80L));

		RetrievalEvaluator evaluator = new RetrievalEvaluator(searchService);
		EvalReport report = evaluator.evaluate(
				dataset,
				repo,
				List.of("src/A.java"),
				"sha",
				List.of(EvalVariant.HYBRID_RERANK),
				List.of(1),
				false,
				0
		);

		VariantReport vr = report.variants().get(0);
		CaseResult cr = vr.cases().get(0);
		assertThat(cr.warnings()).anyMatch(w -> w.contains("Reranker was requested"));
	}
}
