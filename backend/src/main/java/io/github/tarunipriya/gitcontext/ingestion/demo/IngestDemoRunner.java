package io.github.tarunipriya.gitcontext.ingestion.demo;

import io.github.tarunipriya.gitcontext.ingestion.CommitIngestor;
import io.github.tarunipriya.gitcontext.ingestion.FileIngestor;
import io.github.tarunipriya.gitcontext.ingestion.RepositoryCloner;
import io.github.tarunipriya.gitcontext.ingestion.RepositoryCloner.ClonedRepo;
import io.github.tarunipriya.gitcontext.model.Document;
import io.github.tarunipriya.gitcontext.model.RepoRef;
import io.github.tarunipriya.gitcontext.model.SourceType;
import java.util.ArrayList;
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
 * <p>Clones or updates the configured demo repository, ingests its files and commits,
 * prints diagnostic summary statistics to the log, and exits.
 */
@Component
@Profile("ingest-demo")
public class IngestDemoRunner implements CommandLineRunner {

	private static final Logger log = LoggerFactory.getLogger(IngestDemoRunner.class);

	private final RepositoryCloner repositoryCloner;
	private final FileIngestor fileIngestor;
	private final CommitIngestor commitIngestor;
	private final String demoRepoString;

	/**
	 * Creates a new {@link IngestDemoRunner}.
	 *
	 * @param repositoryCloner repository cloner
	 * @param fileIngestor file ingestor
	 * @param commitIngestor commit ingestor
	 * @param demoRepoString repository reference string to ingest
	 */
	public IngestDemoRunner(
			RepositoryCloner repositoryCloner,
			FileIngestor fileIngestor,
			CommitIngestor commitIngestor,
			@Value("${gitcontext.demo.repo}") String demoRepoString
	) {
		this.repositoryCloner = Objects.requireNonNull(repositoryCloner);
		this.fileIngestor = Objects.requireNonNull(fileIngestor);
		this.commitIngestor = Objects.requireNonNull(commitIngestor);
		this.demoRepoString = Objects.requireNonNull(demoRepoString);
	}

	@Override
	public void run(String... args) {
		log.info("=================================================");
		log.info("Starting GitContext Ingestion Demo for: {}", demoRepoString);
		log.info("=================================================");

		RepoRef repoRef = RepoRef.parse(demoRepoString);
		ClonedRepo clonedRepo = repositoryCloner.cloneOrUpdate(repoRef);

		List<Document> fileDocs = fileIngestor.ingest(clonedRepo);
		List<Document> commitDocs = commitIngestor.ingest(clonedRepo);

		List<Document> allDocs = new ArrayList<>(fileDocs.size() + commitDocs.size());
		allDocs.addAll(fileDocs);
		allDocs.addAll(commitDocs);

		log.info("---------------- Ingestion Summary ----------------");
		log.info("Total Documents Ingested: {}", allDocs.size());

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

		log.info("=================================================");
		log.info("Ingestion Demo Finished Successfully.");
		log.info("=================================================");

		System.exit(0);
	}
}
