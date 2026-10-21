package io.github.tarunipriya.gitcontext.evaluation.runner;

import io.github.tarunipriya.gitcontext.evaluation.MetricSummary;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Plain serialization class that formats and persists evaluation reports to JSON and Markdown.
 * <p>
 * Writes:
 * <ul>
 *   <li>{@code <outputDir>/<dataset>-<yyyyMMdd-HHmmss>.json} (pretty-printed via Jackson 3)</li>
 *   <li>{@code <outputDir>/<dataset>-<yyyyMMdd-HHmmss>.md} (formatted markdown tables and breakdowns)</li>
 * </ul>
 */
public class EvalReportWriter {

	private static final DateTimeFormatter TIMESTAMP_FORMAT =
			DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);

	private static final DateTimeFormatter DISPLAY_DATE_FORMAT =
			DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'").withZone(ZoneOffset.UTC);

	private static final JsonMapper JSON_MAPPER = JsonMapper.builder()
			.enable(SerializationFeature.INDENT_OUTPUT)
			.disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
			.build();

	/**
	 * Pair of output file paths for JSON and Markdown reports.
	 *
	 * @param jsonPath filesystem path to the JSON report
	 * @param markdownPath filesystem path to the Markdown report
	 */
	public record ReportPaths(Path jsonPath, Path markdownPath) {
	}

	/**
	 * Writes the evaluation report to JSON and Markdown files in the given directory.
	 *
	 * @param report evaluation report to write
	 * @param outputDir target output directory (created if missing)
	 * @return generated report file paths
	 */
	public ReportPaths write(EvalReport report, Path outputDir) {
		try {
			Files.createDirectories(outputDir);
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to create output directory: " + outputDir, e);
		}

		String timestamp = TIMESTAMP_FORMAT.format(report.startedAt());
		String datasetName = (report.datasetName() != null && !report.datasetName().isBlank())
				? report.datasetName()
				: "eval";
		String baseFilename = datasetName + "-" + timestamp;

		Path jsonPath = outputDir.resolve(baseFilename + ".json");
		Path markdownPath = outputDir.resolve(baseFilename + ".md");

		try {
			String jsonContent = JSON_MAPPER.writeValueAsString(report);
			Files.writeString(jsonPath, jsonContent, StandardCharsets.UTF_8);
		} catch (Exception e) {
			throw new UncheckedIOException("Failed to serialize evaluation report to JSON: " + jsonPath,
					(e instanceof IOException ioe) ? ioe : new IOException(e));
		}

		String markdownContent = renderMarkdown(report);
		try {
			Files.writeString(markdownPath, markdownContent, StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to write Markdown evaluation report: " + markdownPath, e);
		}

		return new ReportPaths(jsonPath, markdownPath);
	}

	/**
	 * Renders an evaluation report as a human-readable Markdown document.
	 *
	 * @param report evaluation report
	 * @return formatted Markdown string
	 */
	public String renderMarkdown(EvalReport report) {
		StringBuilder sb = new StringBuilder();

		// Header
		sb.append("# Retrieval Evaluation: ").append(report.datasetName()).append("\n\n");
		sb.append("- **Repository:** ").append(report.repo()).append("\n");
		sb.append("- **Commit SHA:** ").append(report.indexedCommitSha()).append("\n");
		sb.append("- **Started At:** ").append(DISPLAY_DATE_FORMAT.format(report.startedAt())).append("\n");
		sb.append("- **Answerable Cases:** ").append(report.answerableCases()).append("\n");
		sb.append("- **Skipped Unanswerable Cases:** ").append(report.skippedUnanswerable()).append("\n\n");

		// Find all k values across all variants
		List<Integer> ks = report.variants().stream()
				.flatMap(v -> v.summaryByK().keySet().stream())
				.distinct()
				.sorted()
				.toList();

		// Tables per k
		for (Integer k : ks) {
			sb.append("## Metrics @ k=").append(k).append("\n\n");
			sb.append("| Variant | Hit@").append(k)
					.append(" | Recall@").append(k)
					.append(" | Precision@").append(k)
					.append(" | MRR | nDCG@").append(k)
					.append(" |\n");
			sb.append("|---|---|---|---|---|---|\n");

			for (VariantReport vr : report.variants()) {
				MetricSummary summary = vr.summaryByK().get(k);
				if (summary != null) {
					sb.append(String.format(
							Locale.ROOT,
							"| %s | %.3f | %.3f | %.3f | %.3f | %.3f |\n",
							vr.variant(),
							summary.meanHitRateAtK(),
							summary.meanRecallAtK(),
							summary.meanPrecisionAtK(),
							summary.mrr(),
							summary.meanNdcgAtK()
					));
				} else {
					sb.append(String.format(Locale.ROOT, "| %s | - | - | - | - | - |\n", vr.variant()));
				}
			}
			sb.append("\n");
		}

		// Latency table
		sb.append("## Latency\n\n");
		sb.append("| Variant | p50 | p95 | p99 | mean (ms) | failed |\n");
		sb.append("|---|---|---|---|---|---|\n");
		for (VariantReport vr : report.variants()) {
			if (vr.latency().count() == 0) {
				sb.append(String.format(
						Locale.ROOT,
						"| %s | n/a | n/a | n/a | n/a | %d |\n",
						vr.variant(),
						vr.failedCases()
				));
			} else {
				sb.append(String.format(
						Locale.ROOT,
						"| %s | %d | %d | %d | %.1f | %d |\n",
						vr.variant(),
						vr.latency().p50Ms(),
						vr.latency().p95Ms(),
						vr.latency().p99Ms(),
						vr.latency().meanMs(),
						vr.failedCases()
				));
			}
		}
		sb.append("\n");

		// Missing expected paths
		sb.append("## Missing Expected Paths\n\n");
		if (report.missingExpectedPaths().isEmpty()) {
			sb.append("- None\n\n");
		} else {
			for (String path : report.missingExpectedPaths()) {
				sb.append("- `").append(path).append("`\n");
			}
			sb.append("\n");
		}

		// Misses at k=5 (or the largest k <= 5)
		int missK;
		List<Integer> ksAtOrBelow5 = ks.stream().filter(k -> k <= 5).toList();
		if (!ksAtOrBelow5.isEmpty()) {
			missK = ksAtOrBelow5.get(ksAtOrBelow5.size() - 1);
		} else if (!ks.isEmpty()) {
			missK = ks.get(0);
		} else {
			missK = 5;
		}

		sb.append("## Misses at k=").append(missK).append("\n\n");
		for (VariantReport vr : report.variants()) {
			sb.append("### ").append(vr.variant()).append("\n\n");
			List<CaseResult> misses = vr.cases().stream()
					.filter(c -> {
						MetricSummary ms = vr.summaryByK().get(missK);
						if (ms == null) return false;
						var qm = c.metricsByK().get(missK);
						return qm != null && qm.hitRateAtK() == 0.0;
					})
					.toList();

			if (misses.isEmpty()) {
				sb.append("- No misses at k=").append(missK).append("\n\n");
			} else {
				for (CaseResult miss : misses) {
					List<String> top3 = miss.retrievedPaths().stream().limit(3).toList();
					String top3Display = top3.isEmpty()
							? "(none)"
							: top3.stream().map(p -> "`" + p + "`").collect(Collectors.joining(", "));
					sb.append("- **").append(miss.caseId()).append("**: Top-3 retrieved: ")
							.append(top3Display).append("\n");
				}
				sb.append("\n");
			}
		}

		return sb.toString();
	}
}
