package io.github.tarunipriya.gitcontext.evaluation.answer;

import io.github.tarunipriya.gitcontext.config.GitContextProperties;
import io.github.tarunipriya.gitcontext.evaluation.dataset.EvalCase;
import io.github.tarunipriya.gitcontext.evaluation.dataset.EvalDataset;
import io.github.tarunipriya.gitcontext.evaluation.dataset.EvalDatasetLoader;
import io.github.tarunipriya.gitcontext.ingestion.IngestionResult;
import io.github.tarunipriya.gitcontext.ingestion.IngestionService;
import io.github.tarunipriya.gitcontext.model.RepoRef;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Command-line runner active under the {@code answer-eval} Spring profile.
 * <p>
 * Loads the evaluation benchmark dataset, limits cases if configured via {@code maxCases},
 * optionally reingests the target repository, evaluates answer quality with {@link AnswerEvaluator},
 * persists JSON and Markdown reports, and logs formatted summary tables.
 */
@Component
@Profile("answer-eval")
public class AnswerEvalRunner implements CommandLineRunner {

	private static final Logger log = LoggerFactory.getLogger(AnswerEvalRunner.class);

	private final GitContextProperties properties;
	private final IngestionService ingestionService;
	private final AnswerEvaluator answerEvaluator;
	private final AnswerEvalReportWriter reportWriter;

	public AnswerEvalRunner(
			GitContextProperties properties,
			IngestionService ingestionService,
			AnswerEvaluator answerEvaluator
	) {
		this.properties = Objects.requireNonNull(properties, "properties must not be null");
		this.ingestionService = Objects.requireNonNull(ingestionService, "ingestionService must not be null");
		this.answerEvaluator = Objects.requireNonNull(answerEvaluator, "answerEvaluator must not be null");
		this.reportWriter = new AnswerEvalReportWriter();
	}

	@Override
	public void run(String... args) {
		log.info("==========================================================================================");
		log.info("Starting GitContext Answer Quality Evaluation Benchmark");
		log.info("==========================================================================================");

		GitContextProperties.AnswerEvalProperties answerProps = properties.answerEval();

		Path datasetPath = GitContextProperties.resolveToRepoRoot(Path.of(answerProps.dataset()));
		Path outputDir = GitContextProperties.resolveToRepoRoot(Path.of(answerProps.outputDir()));

		log.info("Resolved evaluation dataset path: {}", datasetPath.toAbsolutePath());
		log.info("Resolved output directory path:     {}", outputDir.toAbsolutePath());

		EvalDataset dataset = EvalDatasetLoader.load(datasetPath);
		if (answerProps.maxCases() > 0 && answerProps.maxCases() < dataset.cases().size()) {
			List<EvalCase> limited = dataset.cases().stream().limit(answerProps.maxCases()).toList();
			dataset = new EvalDataset(dataset.name(), limited);
			log.info("Evaluation limited to first {} cases (maxCases={})", limited.size(), answerProps.maxCases());
		}

		log.info("Dataset '{}' loaded: {} total cases ({} answerable, {} unanswerable)",
				dataset.name(), dataset.cases().size(), dataset.answerableCases().size(), dataset.unanswerableCases().size());

		RepoRef repoRef = RepoRef.parse(answerProps.repo());
		String indexedCommitSha = "existing-index";
		Path checkpointFile = outputDir.resolve("answers-" + dataset.name() + ".checkpoint.jsonl");

		if (answerProps.reingest()) {
			log.info("Re-ingesting repository {} before answer evaluation (reingest=true)...", repoRef.fullName());
			IngestionResult ingestionResult = ingestionService.ingest(repoRef);
			indexedCommitSha = ingestionResult.headCommitSha();
			log.info("Ingestion completed: indexedCommitSha={}, {} documents, {} chunks",
					indexedCommitSha, ingestionResult.documents().size(), ingestionResult.chunks().size());
		} else {
			String checkpointSha = AnswerEvaluator.readCheckpointSha(checkpointFile);
			if (checkpointSha != null && !checkpointSha.isBlank()) {
				indexedCommitSha = checkpointSha;
				log.info("Reingest is false; taking indexedCommitSha '{}' from checkpoint and assuming index is unchanged.", indexedCommitSha);
			} else {
				log.info("Reingest is false; evaluating against current index state.");
			}
		}

		log.info("Executing answer evaluation benchmark (judgeEnabled={}, requestDelayMs={} ms)...",
				answerProps.judgeEnabled(), answerProps.requestDelayMs());

		Optional<AnswerEvalReport> reportOpt = answerEvaluator.evaluate(
				dataset,
				repoRef,
				indexedCommitSha,
				answerProps
		);

		if (reportOpt.isEmpty()) {
			log.info("Evaluation incomplete or paused. Checkpoint written to: {}. Re-run after quota reset to resume.",
					checkpointFile.toAbsolutePath());
			return;
		}

		AnswerEvalReport report = reportOpt.get();
		AnswerEvalReportWriter.ReportPaths paths = reportWriter.write(report, outputDir);
		try {
			Files.deleteIfExists(checkpointFile);
			log.info("Checkpoint file deleted after successful report creation: {}", checkpointFile.toAbsolutePath());
		} catch (IOException e) {
			log.warn("Failed to delete checkpoint file: {}", checkpointFile, e);
		}

		log.info("==========================================================================================");
		log.info("Answer Quality Evaluation Complete!");
		log.info("JSON report written to:     {}", paths.jsonPath().toAbsolutePath());
		log.info("Markdown report written to: {}", paths.markdownPath().toAbsolutePath());
		log.info("==========================================================================================");
		log.info("\n{}", reportWriter.renderMarkdown(report));
	}
}
