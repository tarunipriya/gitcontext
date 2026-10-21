package io.github.tarunipriya.gitcontext.evaluation.runner;

import io.github.tarunipriya.gitcontext.evaluation.LatencyStats;
import io.github.tarunipriya.gitcontext.evaluation.MetricSummary;
import io.github.tarunipriya.gitcontext.evaluation.QueryMetrics;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class EvalReportWriterTest {

	private final EvalReportWriter writer = new EvalReportWriter();

	@Test
	@DisplayName("Writes JSON and Markdown reports: validates tables, headers, and JSON round-trip")
	void testWriteReport(@TempDir Path tempDir) throws IOException {
		Instant now = Instant.parse("2026-10-21T12:30:00Z");

		QueryMetrics qmVector1 = new QueryMetrics(1.0, 1.0, 1.0, 1.0, 1.0);
		QueryMetrics qmVector5 = new QueryMetrics(1.0, 1.0, 0.2, 1.0, 1.0);
		MetricSummary summaryVector1 = MetricSummary.of(1, List.of(qmVector1));
		MetricSummary summaryVector5 = MetricSummary.of(5, List.of(qmVector5));

		CaseResult case1 = new CaseResult(
				"case-001",
				"How to index repo?",
				EvalVariant.VECTOR,
				List.of("src/Index.java", "src/Util.java"),
				Set.of("src/Index.java"),
				Map.of(1, qmVector1, 5, qmVector5),
				45L,
				List.of()
		);

		QueryMetrics qmHybrid1 = new QueryMetrics(1.0, 1.0, 1.0, 1.0, 1.0);
		QueryMetrics qmHybrid5 = new QueryMetrics(1.0, 1.0, 0.2, 1.0, 1.0);
		MetricSummary summaryHybrid1 = MetricSummary.of(1, List.of(qmHybrid1));
		MetricSummary summaryHybrid5 = MetricSummary.of(5, List.of(qmHybrid5));

		CaseResult case2 = new CaseResult(
				"case-001",
				"How to index repo?",
				EvalVariant.HYBRID,
				List.of("src/Index.java"),
				Set.of("src/Index.java"),
				Map.of(1, qmHybrid1, 5, qmHybrid5),
				70L,
				List.of()
		);

		VariantReport vrVector = new VariantReport(
				EvalVariant.VECTOR,
				Map.of(1, summaryVector1, 5, summaryVector5),
				LatencyStats.of(List.of(45L)),
				List.of(case1)
		);

		VariantReport vrHybrid = new VariantReport(
				EvalVariant.HYBRID,
				Map.of(1, summaryHybrid1, 5, summaryHybrid5),
				LatencyStats.of(List.of(70L)),
				List.of(case2)
		);

		EvalReport report = new EvalReport(
				"sample-eval",
				"tarunipriya/gitcontext",
				"c0ffee1234567890abcdef1234567890abcdef12",
				now,
				1,
				2,
				List.of("docs/Missing.md"),
				List.of(vrVector, vrHybrid)
		);

		Path outputSubDir = tempDir.resolve("results");
		EvalReportWriter.ReportPaths paths = writer.write(report, outputSubDir);

		// 1. Verify both files created
		assertThat(paths.jsonPath()).isRegularFile();
		assertThat(paths.markdownPath()).isRegularFile();
		assertThat(paths.jsonPath().getFileName().toString()).startsWith("sample-eval-").endsWith(".json");
		assertThat(paths.markdownPath().getFileName().toString()).startsWith("sample-eval-").endsWith(".md");

		// 2. Verify Markdown content
		String mdContent = Files.readString(paths.markdownPath());
		assertThat(mdContent)
				.contains("# Retrieval Evaluation: sample-eval")
				.contains("- **Repository:** tarunipriya/gitcontext")
				.contains("- **Commit SHA:** c0ffee1234567890abcdef1234567890abcdef12")
				.contains("- **Answerable Cases:** 1")
				.contains("- **Skipped Unanswerable Cases:** 2")
				.contains("## Metrics @ k=1")
				.contains("## Metrics @ k=5")
				.contains("| Variant | Hit@1 | Recall@1 | Precision@1 | MRR | nDCG@1 |")
				.contains("| VECTOR | 1.000 | 1.000 | 1.000 | 1.000 | 1.000 |")
				.contains("| HYBRID | 1.000 | 1.000 | 1.000 | 1.000 | 1.000 |")
				.contains("## Latency")
				.contains("| Variant | p50 | p95 | p99 | mean (ms) | failed |")
				.contains("| VECTOR | 45 | 45 | 45 | 45.0 | 0 |")
				.contains("| HYBRID | 70 | 70 | 70 | 70.0 | 0 |")
				.contains("## Missing Expected Paths")
				.contains("docs/Missing.md")
				.contains("## Misses at k=5");

		// 3. Verify JSON round-trips dataset name and commit SHA
		JsonMapper mapper = JsonMapper.builder()
				.disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
				.build();
		EvalReport roundTripped = mapper.readValue(paths.jsonPath().toFile(), EvalReport.class);
		assertThat(roundTripped.datasetName()).isEqualTo("sample-eval");
		assertThat(roundTripped.indexedCommitSha()).isEqualTo("c0ffee1234567890abcdef1234567890abcdef12");
		assertThat(roundTripped.repo()).isEqualTo("tarunipriya/gitcontext");
		assertThat(roundTripped.answerableCases()).isEqualTo(1);
		assertThat(roundTripped.skippedUnanswerable()).isEqualTo(2);
		assertThat(roundTripped.missingExpectedPaths()).containsExactly("docs/Missing.md");
		assertThat(roundTripped.variants()).hasSize(2);
	}

	@Test
	@DisplayName("Renders n/a in latency table when all cases in a variant failed")
	void testRenderMarkdownWithAllFailedCases(@TempDir Path tempDir) throws IOException {
		CaseResult failedCase = new CaseResult(
				"case-fail",
				"Question?",
				EvalVariant.VECTOR,
				List.of(),
				Set.of("src/Target.java"),
				Map.of(1, new QueryMetrics(0.0, 0.0, 0.0, 0.0, 0.0)),
				0L,
				List.of("Search timeout")
		);

		VariantReport failedVariant = new VariantReport(
				EvalVariant.VECTOR,
				Map.of(1, new MetricSummary(1, 1, 0.0, 0.0, 0.0, 0.0, 0.0)),
				new LatencyStats(0, 0L, 0.0, 0L, 0L, 0L, 0L),
				List.of(failedCase),
				1
		);

		EvalReport report = new EvalReport(
				"fail-eval",
				"tarunipriya/gitcontext",
				"sha999",
				Instant.now(),
				1,
				0,
				List.of(),
				List.of(failedVariant)
		);

		EvalReportWriter.ReportPaths paths = writer.write(report, tempDir);
		String md = Files.readString(paths.markdownPath());

		assertThat(md)
				.contains("| Variant | p50 | p95 | p99 | mean (ms) | failed |")
				.contains("| VECTOR | n/a | n/a | n/a | n/a | 1 |");
	}
}
