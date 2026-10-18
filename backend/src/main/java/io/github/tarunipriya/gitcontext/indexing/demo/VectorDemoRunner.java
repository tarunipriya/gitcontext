package io.github.tarunipriya.gitcontext.indexing.demo;

import io.github.tarunipriya.gitcontext.ingestion.IngestionResult;
import io.github.tarunipriya.gitcontext.ingestion.IngestionService;
import io.github.tarunipriya.gitcontext.indexing.IndexSummary;
import io.github.tarunipriya.gitcontext.indexing.SearchHit;
import io.github.tarunipriya.gitcontext.indexing.VectorStore;
import io.github.tarunipriya.gitcontext.model.RepoRef;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Demo runner active only when running with the {@code vector-demo} Spring profile.
 *
 * <p>Ingests the configured demo repository, creates chunks, computes local BGE embeddings,
 * indexes into Qdrant, calls {@link VectorStore#deleteStale(RepoRef, String)}, and evaluates
 * vector similarity search queries.
 */
@Component
@Profile("vector-demo")
public class VectorDemoRunner implements CommandLineRunner {

	private static final Logger log = LoggerFactory.getLogger(VectorDemoRunner.class);

	private static final List<String> DEMO_QUESTIONS = List.of(
			"how does the app handle GitHub API rate limits?",
			"where are markdown headings parsed into sections?",
			"how is a repository cloned or updated?"
	);

	private final IngestionService ingestionService;
	private final VectorStore vectorStore;
	private final String demoRepoString;

	/**
	 * Creates a new {@link VectorDemoRunner}.
	 *
	 * @param ingestionService unified ingestion service
	 * @param vectorStore vector store facade
	 * @param demoRepoString demo repository string identifier
	 */
	public VectorDemoRunner(
			IngestionService ingestionService,
			VectorStore vectorStore,
			@Value("${gitcontext.demo.repo:tarunipriya/gitcontext}") String demoRepoString
	) {
		this.ingestionService = Objects.requireNonNull(ingestionService, "ingestionService must not be null");
		this.vectorStore = Objects.requireNonNull(vectorStore, "vectorStore must not be null");
		this.demoRepoString = Objects.requireNonNull(demoRepoString, "demoRepoString must not be null");
	}

	@Override
	public void run(String... args) {
		log.info("=================================================");
		log.info("Starting GitContext Vector Store Demo for: {}", demoRepoString);
		log.info("=================================================");

		RepoRef repoRef = RepoRef.parse(demoRepoString);
		IngestionResult ingestionResult = ingestionService.ingest(repoRef);

		String runId = "run-" + System.currentTimeMillis();
		log.info("Indexing ingested chunks into Qdrant with runId: {}", runId);

		IndexSummary summary = vectorStore.index(repoRef, runId, ingestionResult.chunks());
		vectorStore.deleteStale(repoRef, runId);

		log.info("---------------- Indexing Performance ----------------");
		log.info("Total Points Indexed: {}", summary.count());
		log.info("Embedding Duration:   {} ms", summary.embedMillis());
		log.info("Upsert Duration:      {} ms", summary.upsertMillis());
		log.info("Total Duration:       {} ms", summary.embedMillis() + summary.upsertMillis());

		log.info("---------------- Evaluating Vector Similarity Search Queries ----------------");

		for (String question : DEMO_QUESTIONS) {
			log.info("");
			log.info(">>> Question: \"{}\"", question);
			List<SearchHit> hits = vectorStore.search(repoRef, question, 3, null, null);

			if (hits.isEmpty()) {
				log.info("  (No hits found)");
			} else {
				for (int i = 0; i < hits.size(); i++) {
					SearchHit hit = hits.get(i);
					String lines = (hit.startLine() != null && hit.endLine() != null)
							? hit.startLine() + "-" + hit.endLine()
							: (hit.startLine() != null ? String.valueOf(hit.startLine()) : "n/a");
					String symbol = hit.symbolName() != null ? hit.symbolName() : "(none)";

					log.info("  #{} [Score: {}] Type: {}, Symbol: {}, Path: {}, Lines: {}",
							i + 1,
							String.format(Locale.ROOT, "%.3f", hit.score()),
							hit.chunkType(),
							symbol,
							hit.path(),
							lines
					);
				}
			}
		}

		log.info("");
		log.info("=================================================");
		log.info("Vector Store Demo Finished Successfully.");
		log.info("=================================================");

		System.exit(0);
	}
}
