package io.github.tarunipriya.gitcontext.evaluation.runner;

import io.github.tarunipriya.gitcontext.config.GitContextProperties;
import io.github.tarunipriya.gitcontext.evaluation.SourceKeys;
import io.github.tarunipriya.gitcontext.evaluation.dataset.EvalDataset;
import io.github.tarunipriya.gitcontext.evaluation.dataset.EvalDatasetLoader;
import io.github.tarunipriya.gitcontext.ingestion.IngestionResult;
import io.github.tarunipriya.gitcontext.ingestion.IngestionService;
import io.github.tarunipriya.gitcontext.model.RepoRef;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Command-line runner active when running with the {@code eval} Spring profile.
 * <p>
 * Loads the evaluation benchmark dataset, optionally ingests the repository to gather fresh documents
 * and the indexed commit SHA, runs multi-variant retrieval evaluation via {@link RetrievalEvaluator},
 * writes JSON and Markdown evaluation reports, and logs formatted summary tables.
 */
@Component
@Profile("eval")
public class EvalRunner implements CommandLineRunner {

	private static final Logger log = LoggerFactory.getLogger(EvalRunner.class);

	private final GitContextProperties properties;
	private final IngestionService ingestionService;
	private final RetrievalEvaluator retrievalEvaluator;
	private final EvalReportWriter reportWriter;

	/**
	 * Creates a new {@link EvalRunner} with constructor-injected dependencies.
	 *
	 * @param properties configuration properties
	 * @param ingestionService repository ingestion service
	 * @param retrievalEvaluator retrieval evaluation engine
	 */
	public EvalRunner(
			GitContextProperties properties,
			IngestionService ingestionService,
			RetrievalEvaluator retrievalEvaluator
	) {
		this.properties = Objects.requireNonNull(properties, "properties must not be null");
		this.ingestionService = Objects.requireNonNull(ingestionService, "ingestionService must not be null");
		this.retrievalEvaluator = Objects.requireNonNull(retrievalEvaluator, "retrievalEvaluator must not be null");
		this.reportWriter = new EvalReportWriter();
	}

	@Override
	public void run(String... args) {
		log.info("==========================================================================================");
		log.info("Starting GitContext Retrieval Evaluation Benchmark");
		log.info("==========================================================================================");

		GitContextProperties.EvalProperties evalProps = properties.eval();

		// Resolve dataset and output directory paths (absolute as-is, relative resolved to repo root)
		Path datasetPath = GitContextProperties.resolveToRepoRoot(Path.of(evalProps.dataset()));
		Path outputDir = GitContextProperties.resolveToRepoRoot(Path.of(evalProps.outputDir()));

		log.info("Resolved evaluation dataset path: {}", datasetPath.toAbsolutePath());
		log.info("Resolved output directory path:     {}", outputDir.toAbsolutePath());

		log.info("Loading evaluation dataset from: {}", datasetPath.toAbsolutePath());
		EvalDataset dataset = EvalDatasetLoader.load(datasetPath);
		log.info("Dataset '{}' loaded: {} total cases ({} answerable, {} unanswerable)",
				dataset.name(), dataset.cases().size(), dataset.answerableCases().size(), dataset.unanswerableCases().size());

		RepoRef repoRef = RepoRef.parse(evalProps.repo());
		List<EvalVariant> variants = evalProps.variants().stream()
				.map(EvalVariant::parse)
				.toList();
		log.info("Configured evaluation variants ({}): {}", variants.size(),
				variants.stream().map(EvalVariant::label).toList());

		Collection<String> indexedPaths = null;
		String indexedCommitSha = "existing-index";

		if (evalProps.reingest()) {
			log.info("Re-ingesting repository {} before evaluation (reingest=true)...", repoRef.fullName());
			IngestionResult ingestionResult = ingestionService.ingest(repoRef);
			indexedCommitSha = ingestionResult.headCommitSha();
			log.info("Ingestion completed: indexedCommitSha={}, {} documents, {} chunks",
					indexedCommitSha, ingestionResult.documents().size(), ingestionResult.chunks().size());
			indexedPaths = ingestionResult.documents().stream()
					.map(doc -> SourceKeys.of(doc.metadata()))
					.filter(Objects::nonNull)
					.collect(Collectors.toSet());
		} else {
			log.info("Reingest is false. Missing-path checks are skipped because existing index paths are not loaded.");
		}

		log.info("Executing retrieval evaluation across variants: {} for ks: {}", variants, evalProps.ks());
		EvalReport report = retrievalEvaluator.evaluate(
				dataset,
				repoRef,
				indexedPaths,
				indexedCommitSha,
				variants,
				evalProps.ks(),
				evalProps.excludeTests(),
				evalProps.warmupQueries()
		);

		EvalReportWriter.ReportPaths paths = reportWriter.write(report, outputDir);

		log.info("==========================================================================================");
		log.info("Evaluation Complete!");
		log.info("JSON report written to:     {}", paths.jsonPath().toAbsolutePath());
		log.info("Markdown report written to: {}", paths.markdownPath().toAbsolutePath());
		log.info("==========================================================================================");
		log.info("\n{}", reportWriter.renderMarkdown(report));
	}
}
