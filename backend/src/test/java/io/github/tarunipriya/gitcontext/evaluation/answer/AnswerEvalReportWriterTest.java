package io.github.tarunipriya.gitcontext.evaluation.answer;

import io.github.tarunipriya.gitcontext.evaluation.LatencyStats;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class AnswerEvalReportWriterTest {

	@Test
	@DisplayName("Writes JSON and Markdown reports with all tables, caveat, problems, and excludes full answers from Markdown")
	void testReportWritingAndMarkdownRendering(@TempDir Path tempDir) throws IOException {
		String secretAnswer1 = "THIS_IS_A_LONG_RAW_ANSWER_BODY_ABOUT_QDRANT_ARCHITECTURE_THAT_MUST_NOT_BE_IN_MARKDOWN";
		String secretAnswer2 = "ANOTHER_LONG_RAW_ANSWER_BODY_ABOUT_LUCENE_BM25_THAT_MUST_NOT_BE_IN_MARKDOWN";

		AnswerCaseResult case1 = new AnswerCaseResult(
				"case-001",
				"How does vector search work?",
				true,
				secretAnswer1,
				false,
				"gemini-3.5-flash",
				List.of("backend/src/main/Qdrant.java"),
				List.of("backend/src/main/Qdrant.java"),
				1.0,
				true,
				1.0,
				true,
				List.of(),
				100L,
				2000L,
				2100L,
				List.of()
		);

		AnswerCaseResult case2 = new AnswerCaseResult(
				"case-002",
				"How is Lucene BM25 configured?",
				true,
				secretAnswer2,
				false,
				"gemini-3.8-flash",
				List.of("backend/src/main/Lucene.java"),
				List.of("backend/src/main/Lucene.java", "backend/src/main/Keyword.java"),
				0.5,
				true,
				0.5,
				false,
				List.of("Claims BM25 uses Elasticsearch under the hood"),
				120L,
				2500L,
				2620L,
				List.of()
		);

		AnswerCaseResult case3 = new AnswerCaseResult(
				"case-003",
				"What is the deployment procedure for Kubernetes?",
				false,
				"I couldn't find this in the indexed repository.",
				true,
				"gemini-3.5-flash",
				List.of(),
				List.of(),
				0.0,
				false,
				null,
				null,
				List.of(),
				80L,
				800L,
				880L,
				List.of()
		);

		AnswerCaseResult case4 = new AnswerCaseResult(
				"case-004",
				"How are comments fetched?",
				true,
				"",
				false,
				null,
				List.of(),
				List.of("backend/src/main/GitHub.java"),
				0.0,
				false,
				null,
				null,
				List.of(),
				0L,
				0L,
				0L,
				List.of("Connection timed out to GitHub API")
		);

		LatencyStats retrievalStats = LatencyStats.of(List.of(100L, 120L, 80L));
		LatencyStats genStats = LatencyStats.of(List.of(2000L, 2500L, 800L));
		LatencyStats totalStats = LatencyStats.of(List.of(2100L, 2620L, 880L));

		Map<String, Integer> modelUsage = new LinkedHashMap<>();
		modelUsage.put("gemini-3.5-flash", 2);
		modelUsage.put("gemini-3.8-flash", 1);

		AnswerEvalReport report = new AnswerEvalReport(
				"gitcontext-smoke",
				"tarunipriya/gitcontext",
				"a1b2c3d4e5f6",
				modelUsage,
				"gemini-3.8-flash",
				Instant.parse("2026-10-21T18:00:00Z"),
				3,
				1,
				2,
				0,
				1,
				0.750,
				0.500,
				0.500,
				1.000,
				0.750,
				1.000,
				0.000,
				retrievalStats,
				genStats,
				totalStats,
				List.of(case1, case2, case3, case4)
		);

		AnswerEvalReportWriter writer = new AnswerEvalReportWriter();
		AnswerEvalReportWriter.ReportPaths paths = writer.write(report, tempDir);

		// Assert files exist
		assertThat(paths.jsonPath()).exists();
		assertThat(paths.markdownPath()).exists();

		// Check filename pattern: answers-<dataset>-<yyyyMMdd-HHmmss>
		String jsonName = paths.jsonPath().getFileName().toString();
		assertThat(jsonName).startsWith("answers-gitcontext-smoke-20261021-180000").endsWith(".json");

		// JSON file must contain full answers and all structured data
		String jsonContent = Files.readString(paths.jsonPath());
		assertThat(jsonContent).contains(secretAnswer1);
		assertThat(jsonContent).contains(secretAnswer2);
		assertThat(jsonContent).contains("\"datasetName\" : \"gitcontext-smoke\"");
		assertThat(jsonContent).contains("\"judgeModel\" : \"gemini-3.8-flash\"");
		assertThat(jsonContent).contains("\"gemini-3.5-flash\" : 2");
		assertThat(jsonContent).contains("\"gemini-3.8-flash\" : 1");

		// Markdown file must contain header, tables, caveat, problems
		String mdContent = Files.readString(paths.markdownPath());
		assertThat(mdContent).contains("# Answer Quality Evaluation: gitcontext-smoke");
		assertThat(mdContent).contains("- **Repository:** tarunipriya/gitcontext");
		assertThat(mdContent).contains("- **Commit SHA:** a1b2c3d4e5f6");
		assertThat(mdContent).contains("- **Answer model:** gemini-3.5-flash (2), gemini-3.8-flash (1)");
		assertThat(mdContent).contains("- **Judge model:** gemini-3.8-flash");
		assertThat(mdContent).contains("> **WARNING:** Mixed models in this run; results are not from a single model.");
		assertThat(mdContent).contains("- **Answerable Cases:** 3");
		assertThat(mdContent).contains("- **Unanswerable Cases:** 1");
		assertThat(mdContent).contains("- **Judged Cases:** 2");
		assertThat(mdContent).contains("- **Generation Failures:** 1");

		// Metrics table
		assertThat(mdContent).contains("## Metrics");
		assertThat(mdContent).contains("| Mean Fact Coverage | 0.750 |");
		assertThat(mdContent).contains("| Fully Correct Rate | 0.500 |");
		assertThat(mdContent).contains("| Grounded Rate | 0.500 |");
		assertThat(mdContent).contains("| Cited Any Expected Rate | 1.000 |");
		assertThat(mdContent).contains("| Mean Cited-Expected Recall | 0.750 |");
		assertThat(mdContent).contains("| Refusal Accuracy | 1.000 |");
		assertThat(mdContent).contains("| False Refusal Rate | 0.000 |");

		// Latency table
		assertThat(mdContent).contains("## Latency");
		assertThat(mdContent).contains("| Retrieval |");
		assertThat(mdContent).contains("| Generation |");
		assertThat(mdContent).contains("| Total |");

		// Caveat note
		assertThat(mdContent).contains("Judge caveat");
		assertThat(mdContent).contains("The LLM judge uses the same model family");

		// Per-case table
		assertThat(mdContent).contains("## Per-Case Results");
		assertThat(mdContent).contains("| id | model | refused | fact coverage | grounded | cited expected |");
		assertThat(mdContent).contains("| case-001 | gemini-3.5-flash | no | 1.000 | yes | 1.000 |");
		assertThat(mdContent).contains("| case-002 | gemini-3.8-flash | no | 0.500 | no | 0.500 |");
		assertThat(mdContent).contains("| case-003 | gemini-3.5-flash | yes | n/a | n/a | 0.000 |");
		assertThat(mdContent).contains("| case-004 | n/a | no | n/a | n/a | 0.000 |");

		// Problems section
		assertThat(mdContent).contains("## Problems");
		assertThat(mdContent).contains("case-002");
		assertThat(mdContent).contains("Claims BM25 uses Elasticsearch under the hood");
		assertThat(mdContent).contains("case-004");
		assertThat(mdContent).contains("Generation failed");

		// Crucial requirement: Do NOT put full answers in the Markdown
		assertThat(mdContent).doesNotContain(secretAnswer1);
		assertThat(mdContent).doesNotContain(secretAnswer2);
	}

	@Test
	@DisplayName("Single-model run omits mixed-models warning and renders single model in header")
	void testSingleModelRunOmitsWarning() {
		AnswerCaseResult singleCase = new AnswerCaseResult(
				"case-001",
				"How does vector search work?",
				true,
				"Vectors are searched in Qdrant.",
				false,
				"gemini-3.5-flash",
				List.of("backend/src/main/Qdrant.java"),
				List.of("backend/src/main/Qdrant.java"),
				1.0,
				true,
				1.0,
				true,
				List.of(),
				100L,
				2000L,
				2100L,
				List.of()
		);

		AnswerEvalReport report = new AnswerEvalReport(
				"gitcontext-single",
				"tarunipriya/gitcontext",
				"a1b2c3d4e5f6",
				Map.of("gemini-3.5-flash", 1),
				Instant.parse("2026-10-21T18:00:00Z"),
				1,
				0,
				1,
				0,
				0,
				1.0,
				1.0,
				1.0,
				1.0,
				1.0,
				0.0,
				0.0,
				LatencyStats.of(List.of(100L)),
				LatencyStats.of(List.of(2000L)),
				LatencyStats.of(List.of(2100L)),
				List.of(singleCase)
		);

		AnswerEvalReportWriter writer = new AnswerEvalReportWriter();
		String md = writer.renderMarkdown(report);

		assertThat(md).contains("- **Answer model:** gemini-3.5-flash (1)");
		assertThat(md).contains("- **Judge model:** n/a");
		assertThat(md).doesNotContain("Mixed models in this run; results are not from a single model.");
		assertThat(md).contains("| case-001 | gemini-3.5-flash | no | 1.000 | yes | 1.000 |");
	}
}
