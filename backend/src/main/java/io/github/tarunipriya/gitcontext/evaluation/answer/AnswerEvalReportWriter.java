package io.github.tarunipriya.gitcontext.evaluation.answer;

import io.github.tarunipriya.gitcontext.evaluation.LatencyStats;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Persists answer quality evaluation reports to JSON and human-readable Markdown.
 * <p>
 * Output formats:
 * <ul>
 *   <li>{@code <outputDir>/answers-<dataset>-<yyyyMMdd-HHmmss>.json} (pretty-printed structured data via Jackson 3)</li>
 *   <li>{@code <outputDir>/answers-<dataset>-<yyyyMMdd-HHmmss>.md} (summary metrics, latency, per-case table, and problem breakdown)</li>
 * </ul>
 */
public class AnswerEvalReportWriter {

	private static final DateTimeFormatter TIMESTAMP_FORMAT =
			DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);

	private static final DateTimeFormatter DISPLAY_DATE_FORMAT =
			DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'").withZone(ZoneOffset.UTC);

	private static final JsonMapper JSON_MAPPER = JsonMapper.builder()
			.enable(SerializationFeature.INDENT_OUTPUT)
			.disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
			.build();

	/**
	 * Pair of filesystem output paths for JSON and Markdown reports.
	 *
	 * @param jsonPath filesystem path to the JSON report
	 * @param markdownPath filesystem path to the Markdown report
	 */
	public record ReportPaths(Path jsonPath, Path markdownPath) {
	}

	/**
	 * Writes the answer evaluation report to JSON and Markdown files in the target directory.
	 *
	 * @param report answer evaluation report to persist
	 * @param outputDir directory path where reports are written
	 * @return generated report file paths
	 */
	public ReportPaths write(AnswerEvalReport report, Path outputDir) {
		try {
			Files.createDirectories(outputDir);
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to create output directory: " + outputDir, e);
		}

		String timestamp = TIMESTAMP_FORMAT.format(report.startedAt());
		String datasetName = (report.datasetName() != null && !report.datasetName().isBlank())
				? report.datasetName()
				: "eval";
		String baseFilename = "answers-" + datasetName + "-" + timestamp;

		Path jsonPath = outputDir.resolve(baseFilename + ".json");
		Path markdownPath = outputDir.resolve(baseFilename + ".md");

		try {
			String jsonContent = JSON_MAPPER.writeValueAsString(report);
			Files.writeString(jsonPath, jsonContent, StandardCharsets.UTF_8);
		} catch (Exception e) {
			throw new UncheckedIOException("Failed to serialize answer evaluation report to JSON: " + jsonPath,
					(e instanceof IOException ioe) ? ioe : new IOException(e));
		}

		String markdownContent = renderMarkdown(report);
		try {
			Files.writeString(markdownPath, markdownContent, StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to write Markdown answer evaluation report: " + markdownPath, e);
		}

		return new ReportPaths(jsonPath, markdownPath);
	}

	/**
	 * Renders an answer evaluation report as a human-readable Markdown document.
	 *
	 * @param report answer evaluation report
	 * @return formatted Markdown document string
	 */
	public String renderMarkdown(AnswerEvalReport report) {
		StringBuilder sb = new StringBuilder();

		// Header
		sb.append("# Answer Quality Evaluation: ").append(report.datasetName()).append("\n\n");
		sb.append("- **Repository:** ").append(report.repo()).append("\n");
		sb.append("- **Commit SHA:** ").append(report.indexedCommitSha()).append("\n");
		sb.append("- **Answer model:** ").append(formatModels(report.modelUsage())).append("\n");
		sb.append("- **Judge model:** ").append((report.judgeModel() != null && !report.judgeModel().isBlank()) ? report.judgeModel() : "n/a").append("\n");
		sb.append("- **Started At:** ").append(DISPLAY_DATE_FORMAT.format(report.startedAt())).append("\n");
		sb.append("- **Answerable Cases:** ").append(report.answerableCases()).append("\n");
		sb.append("- **Unanswerable Cases:** ").append(report.unanswerableCases()).append("\n");
		sb.append("- **Judged Cases:** ").append(report.judgedCases()).append("\n");
		sb.append("- **Judge Failures:** ").append(report.judgeFailures()).append("\n");
		sb.append("- **Generation Failures:** ").append(report.generationFailures()).append("\n\n");

		if (report.modelUsage() != null && report.modelUsage().size() > 1) {
			sb.append("> **WARNING:** Mixed models in this run; results are not from a single model.\n\n");
		}

		// Summary Metrics Table
		sb.append("## Metrics\n\n");
		sb.append("| Metric | Value |\n");
		sb.append("|---|---|\n");
		sb.append(String.format(Locale.ROOT, "| Mean Fact Coverage | %.3f |\n", report.meanFactCoverage()));
		sb.append(String.format(Locale.ROOT, "| Fully Correct Rate | %.3f |\n", report.fullyCorrectRate()));
		sb.append(String.format(Locale.ROOT, "| Grounded Rate | %.3f |\n", report.groundedRate()));
		sb.append(String.format(Locale.ROOT, "| Cited Any Expected Rate | %.3f |\n", report.citedAnyExpectedRate()));
		sb.append(String.format(Locale.ROOT, "| Mean Cited-Expected Recall | %.3f |\n", report.meanCitedExpectedRecall()));
		sb.append(String.format(Locale.ROOT, "| Refusal Accuracy | %.3f |\n", report.refusalAccuracy()));
		sb.append(String.format(Locale.ROOT, "| False Refusal Rate | %.3f |\n\n", report.falseRefusalRate()));

		// Latency Table
		sb.append("## Latency\n\n");
		sb.append("| Phase | p50 (ms) | p95 (ms) | mean (ms) |\n");
		sb.append("|---|---|---|---|\n");
		formatLatencyRow(sb, "Retrieval", report.retrievalLatency());
		formatLatencyRow(sb, "Generation", report.generationLatency());
		formatLatencyRow(sb, "Total", report.totalLatency());
		sb.append("\n");

		// Judge Caveat
		sb.append("> [!NOTE]\n");
		sb.append("> **Judge caveat:** The LLM judge uses the same model family as answer generation to evaluate its own answers (temperature 0). The evaluation may be lenient towards stylistic variations or subtle inaccuracies.\n\n");

		// Per-case table
		sb.append("## Per-Case Results\n\n");
		sb.append("| id | model | refused | fact coverage | grounded | cited expected |\n");
		sb.append("|---|---|---|---|---|---|\n");
		for (AnswerCaseResult c : report.cases()) {
			String modelStr = (c.model() != null && !c.model().isBlank()) ? c.model() : "n/a";
			String refusedStr = c.refused() ? "yes" : "no";
			String coverageStr = c.factCoverage() != null
					? String.format(Locale.ROOT, "%.3f", c.factCoverage())
					: "n/a";
			String groundedStr = c.grounded() != null
					? (c.grounded() ? "yes" : "no")
					: "n/a";
			String citedExpectedStr = String.format(Locale.ROOT, "%.3f", c.citedExpectedRecall());

			sb.append(String.format(Locale.ROOT, "| %s | %s | %s | %s | %s | %s |\n",
					c.caseId(), modelStr, refusedStr, coverageStr, groundedStr, citedExpectedStr));
		}
		sb.append("\n");

		// Problems section
		sb.append("## Problems\n\n");
		List<String> problemLines = new ArrayList<>();
		for (AnswerCaseResult c : report.cases()) {
			List<String> issues = new ArrayList<>();

			if (c.answer().isBlank()) {
				issues.add("Generation failed (" + String.join(", ", c.warnings()) + ")");
			} else {
				if (c.answerable() && c.refused()) {
					issues.add("False refusal (answerable question was refused)");
				}
				if (!c.answerable() && !c.refused()) {
					issues.add("Refusal failure (unanswerable question was answered without refusal)");
				}
				if (c.factCoverage() != null && c.factCoverage() < 1.0 - 1e-9) {
					issues.add(String.format(Locale.ROOT, "Incomplete fact coverage (%.1f%%)", c.factCoverage() * 100.0));
				}
				if (Boolean.FALSE.equals(c.grounded())) {
					String claims = !c.unsupportedClaims().isEmpty()
							? ": " + c.unsupportedClaims()
							: "";
					issues.add("Ungrounded claims" + claims);
				}
				if (c.answerable() && !c.refused() && c.factCoverage() == null) {
					issues.add("Judge evaluation failed");
				}
			}

			if (!issues.isEmpty()) {
				problemLines.add("- **" + c.caseId() + "**: " + String.join("; ", issues));
			}
		}

		if (problemLines.isEmpty()) {
			sb.append("- None\n");
		} else {
			for (String line : problemLines) {
				sb.append(line).append("\n");
			}
		}

		return sb.toString();
	}

	private void formatLatencyRow(StringBuilder sb, String phase, LatencyStats stats) {
		if (stats == null || stats.count() == 0) {
			sb.append(String.format(Locale.ROOT, "| %s | n/a | n/a | n/a |\n", phase));
		} else {
			sb.append(String.format(Locale.ROOT, "| %s | %d | %d | %.1f |\n",
					phase, stats.p50Ms(), stats.p95Ms(), stats.meanMs()));
		}
	}

	private String formatModels(Map<String, Integer> modelUsage) {
		if (modelUsage == null || modelUsage.isEmpty()) {
			return "n/a";
		}
		return modelUsage.entrySet().stream()
				.map(e -> e.getKey() + " (" + e.getValue() + ")")
				.collect(Collectors.joining(", "));
	}
}
