package io.github.tarunipriya.gitcontext.ingestion.demo;

import io.github.tarunipriya.gitcontext.ingestion.IngestionResult;
import io.github.tarunipriya.gitcontext.ingestion.IngestionService;
import io.github.tarunipriya.gitcontext.model.Document;
import io.github.tarunipriya.gitcontext.model.RepoRef;
import io.github.tarunipriya.gitcontext.model.SourceType;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Demo runner active only when running with the {@code ingest-demo} Spring profile.
 *
 * <p>Ingests the configured demo repository via {@link IngestionService},
 * prints diagnostic summary statistics and warnings to the log, and exits.
 */
@Component
@Profile("ingest-demo")
public class IngestDemoRunner implements CommandLineRunner {

	private static final Logger log = LoggerFactory.getLogger(IngestDemoRunner.class);

	private final IngestionService ingestionService;
	private final String demoRepoString;

	/**
	 * Creates a new {@link IngestDemoRunner}.
	 *
	 * @param ingestionService unified ingestion service
	 * @param demoRepoString repository reference string to ingest
	 */
	public IngestDemoRunner(
			IngestionService ingestionService,
			@Value("${gitcontext.demo.repo}") String demoRepoString
	) {
		this.ingestionService = Objects.requireNonNull(ingestionService);
		this.demoRepoString = Objects.requireNonNull(demoRepoString);
	}

	@Override
	public void run(String... args) {
		log.info("=================================================");
		log.info("Starting GitContext Ingestion Demo for: {}", demoRepoString);
		log.info("=================================================");

		RepoRef repoRef = RepoRef.parse(demoRepoString);
		IngestionResult result = ingestionService.ingest(repoRef);

		List<Document> allDocs = result.documents();
		List<Document> fileDocs = allDocs.stream()
				.filter(doc -> doc.metadata().sourceType() == SourceType.CODE || doc.metadata().sourceType() == SourceType.DOC)
				.toList();
		List<Document> commitDocs = allDocs.stream()
				.filter(doc -> doc.metadata().sourceType() == SourceType.COMMIT)
				.toList();
		List<Document> prDocs = allDocs.stream()
				.filter(doc -> doc.metadata().sourceType() == SourceType.PULL_REQUEST)
				.toList();
		List<Document> issueDocs = allDocs.stream()
				.filter(doc -> doc.metadata().sourceType() == SourceType.ISSUE)
				.toList();

		long incidentIssueCount = issueDocs.stream()
				.filter(doc -> "true".equals(doc.metadata().extra().get("incident")))
				.count();

		log.info("---------------- Ingestion Summary ----------------");
		log.info("Total Documents Ingested: {}", allDocs.size());
		log.info("PR Count: {}", prDocs.size());
		log.info("Issue Count: {}", issueDocs.size());
		log.info("Incident Issue Count: {}", incidentIssueCount);

		// Counts by Source Type
		Map<SourceType, Long> bySourceType = allDocs.stream()
				.collect(Collectors.groupingBy(doc -> doc.metadata().sourceType(), Collectors.counting()));
		log.info("Documents by Source Type:");
		bySourceType.forEach((type, count) -> log.info("  - {}: {}", type, count));

		// Counts by Language (for files)
		Map<String, Long> byLanguage = fileDocs.stream()
				.collect(Collectors.groupingBy(
						doc -> doc.metadata().language() != null ? doc.metadata().language() : "(none)",
						Collectors.counting()
				));
		log.info("File Documents by Language:");
		byLanguage.entrySet().stream()
				.sorted(Map.Entry.<String, Long>comparingByValue().reversed())
				.forEach(entry -> log.info("  - {}: {}", entry.getKey(), entry.getValue()));

		// Top 5 Largest Files
		log.info("Top 5 Largest Files:");
		fileDocs.stream()
				.sorted(Comparator.comparingInt((Document doc) -> doc.content().length()).reversed())
				.limit(5)
				.forEach(doc -> log.info("  - {} ({} characters)", doc.metadata().path(), doc.content().length()));

		// First 3 Commit Titles
		log.info("First 3 Commit Titles (Most Recent):");
		commitDocs.stream()
				.limit(3)
				.forEach(doc -> log.info("  - [{}] {}", doc.metadata().commitSha().substring(0, 7), doc.metadata().title()));

		// 5 Most Recent Pull Requests
		log.info("5 Most Recent Pull Requests:");
		prDocs.stream()
				.limit(5)
				.forEach(doc -> log.info("  - PR #{}: {}", doc.metadata().prNumber(), doc.metadata().title()));

		// Warnings
		log.info("Warnings ({}):", result.warnings().size());
		result.warnings().forEach(w -> log.warn("  - {}", w));

		log.info("=================================================");
		log.info("Ingestion Demo Finished Successfully.");
		log.info("=================================================");

		System.exit(0);
	}
}
