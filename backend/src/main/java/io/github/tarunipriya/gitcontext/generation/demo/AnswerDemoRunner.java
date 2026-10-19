package io.github.tarunipriya.gitcontext.generation.demo;

import io.github.tarunipriya.gitcontext.generation.AnswerOptions;
import io.github.tarunipriya.gitcontext.generation.AnswerResult;
import io.github.tarunipriya.gitcontext.generation.AnswerService;
import io.github.tarunipriya.gitcontext.generation.Citation;
import io.github.tarunipriya.gitcontext.indexing.SearchHit;
import io.github.tarunipriya.gitcontext.indexing.VectorStore;
import io.github.tarunipriya.gitcontext.ingestion.IngestionResult;
import io.github.tarunipriya.gitcontext.ingestion.IngestionService;
import io.github.tarunipriya.gitcontext.model.RepoRef;
import io.github.tarunipriya.gitcontext.retrieval.SearchMode;
import io.github.tarunipriya.gitcontext.retrieval.keyword.KeywordIndex;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Demo runner active under the {@code answer-demo} profile.
 *
 * <p>Demonstrates grounded answer generation with Gemini and verified citations against indexed repositories.
 */
@Component
@Profile("answer-demo")
public class AnswerDemoRunner implements CommandLineRunner {

	private static final Logger log = LoggerFactory.getLogger(AnswerDemoRunner.class);

	private static final List<String> DEMO_QUESTIONS = List.of(
			"How does GitContext handle GitHub API rate limits?",
			"How are Java files split into chunks?",
			"What does the hybrid search do, and how are the results combined?",
			"Which database does GitContext use to store user passwords?"
	);

	private final AnswerService answerService;
	private final IngestionService ingestionService;
	private final VectorStore vectorStore;
	private final KeywordIndex keywordIndex;
	private final String demoRepoString;
	private final boolean forceReingest;

	public AnswerDemoRunner(
			AnswerService answerService,
			IngestionService ingestionService,
			VectorStore vectorStore,
			KeywordIndex keywordIndex,
			@Value("${gitcontext.demo.repo:tarunipriya/gitcontext}") String demoRepoString,
			@Value("${gitcontext.demo.force-reingest:false}") boolean forceReingest
	) {
		this.answerService = Objects.requireNonNull(answerService, "answerService must not be null");
		this.ingestionService = Objects.requireNonNull(ingestionService, "ingestionService must not be null");
		this.vectorStore = Objects.requireNonNull(vectorStore, "vectorStore must not be null");
		this.keywordIndex = Objects.requireNonNull(keywordIndex, "keywordIndex must not be null");
		this.demoRepoString = Objects.requireNonNull(demoRepoString, "demoRepoString must not be null");
		this.forceReingest = forceReingest;
	}

	@Override
	public void run(String... args) {
		log.info("==========================================================================================");
		log.info("Starting GitContext Grounded Answer Generation Demo for: {}", demoRepoString);
		log.info("==========================================================================================");

		RepoRef repoRef = RepoRef.parse(demoRepoString);

		boolean vectorExists = false;
		try {
			vectorExists = vectorStore.countRepoPoints(repoRef) > 0;
		} catch (Exception e) {
			log.warn("Vector store check encountered error: {}", e.getMessage());
		}
		boolean keywordExists = keywordIndex.exists(repoRef);

		String indexedCommitSha = null;

		if (forceReingest) {
			log.info("gitcontext.demo.force-reingest=true: re-running full ingestion for {}...", repoRef.fullName());
			IngestionResult ingestionResult = ingestionService.ingest(repoRef);
			indexedCommitSha = ingestionResult.headCommitSha();
			log.info("Full ingestion complete. Indexed {} points, {} docs in {} ms (HEAD: {})",
					ingestionResult.indexedPoints(), ingestionResult.documents().size(),
					ingestionResult.totalDurationMillis(), indexedCommitSha);
		} else if (!vectorExists && !keywordExists) {
			log.info("Repository missing from both vector and keyword indexes. Performing ingestion...");
			IngestionResult ingestionResult = ingestionService.ingest(repoRef);
			indexedCommitSha = ingestionResult.headCommitSha();
			log.info("Ingestion complete. Indexed {} points, {} docs in {} ms (HEAD: {})",
					ingestionResult.indexedPoints(), ingestionResult.documents().size(),
					ingestionResult.totalDurationMillis(), indexedCommitSha);
		} else {
			try {
				List<SearchHit> sampleHits = keywordIndex.search(repoRef, "*:*", 1, null, null);
				if (!sampleHits.isEmpty() && sampleHits.getFirst().commitSha() != null) {
					indexedCommitSha = sampleHits.getFirst().commitSha();
				}
			} catch (Exception e) {
				log.debug("Could not inspect existing commit SHA from keyword index: {}", e.getMessage());
			}
			log.info("Reusing already-indexed data for {} (vectorExists={}, keywordExists={})",
					repoRef.fullName(), vectorExists, keywordExists);
		}

		log.info("Searching repository snapshot commit SHA: {}", indexedCommitSha != null ? indexedCommitSha : "unknown");

		log.info("==========================================================================================");
		log.info("Running Grounded Answer Queries with Gemini & Verified Citations");
		log.info("Note: Running with excludeTests=true and excluding paths containing '/demo/' to prevent data");
		log.info("leakage, because demo runner source files literally contain the benchmark questions.");
		log.info("==========================================================================================");

		DemoSummary summary = executeQuestions(repoRef, DEMO_QUESTIONS);

		log.info("");
		log.info("==========================================================================================");
		if (summary.failed() > 0) {
			log.warn("Grounded Answer Generation Demo Completed with {} failed question(s).", summary.failed());
		} else {
			log.info("Grounded Answer Generation Demo Completed Successfully.");
		}
		log.info("==========================================================================================");

		System.exit(0);
	}

	public DemoSummary executeQuestions(RepoRef repoRef, List<String> questions) {
		Objects.requireNonNull(repoRef, "repoRef must not be null");
		Objects.requireNonNull(questions, "questions must not be null");

		int answered = 0;
		int partial = 0;
		int refused = 0;
		int failed = 0;

		for (int qIdx = 0; qIdx < questions.size(); qIdx++) {
			String question = questions.get(qIdx);

			log.info("");
			log.info("------------------------------------------------------------------------------------------");
			log.info("QUERY [{} of {}]: \"{}\"", qIdx + 1, questions.size(), question);
			log.info("------------------------------------------------------------------------------------------");

			try {
				AnswerOptions options = new AnswerOptions(
						SearchMode.HYBRID,
						true,
						false,
						AnswerOptions.DEFAULT_MAX_SOURCES,
						AnswerOptions.DEFAULT_CANDIDATES,
						List.of("/demo/")
				);
				AnswerResult result = answerService.ask(repoRef, question, options);

				log.info("ANSWER:\n{}", result.answer());
				log.info("");

				List<Citation> citations = result.citations();
				if (citations.isEmpty()) {
					log.info("CITATIONS: (None)");
				} else {
					log.info("CITATIONS ({}):", citations.size());
					for (Citation cit : citations) {
						String lineInfo = (cit.startLine() != null && cit.endLine() != null)
								? "lines " + cit.startLine() + "-" + cit.endLine()
								: (cit.startLine() != null ? "line " + cit.startLine() : "whole document");
						log.info("   [{}] {} ({}) | URL: {}",
								cit.number(),
								cit.path() != null ? cit.path() : "(commit/issue)",
								lineInfo,
								cit.url() != null ? cit.url() : "n/a");
					}
				}

				log.info("");
				log.info("EXECUTION METRICS:");
				log.info("   Model:           {}", result.model() != null ? result.model() : "none (short-circuited)");
				int thoughts = result.thoughtsTokens() != null ? result.thoughtsTokens() : 0;
				log.info("   Tokens:          {} prompt + {} completion (thoughtsTokenCount: {}) = {} total",
						result.promptTokens(), result.outputTokens(), thoughts,
						result.promptTokens() + result.outputTokens());
				log.info("   Timings:         retrieval: {} ms, generation: {} ms | total: {} ms",
						result.retrievalMillis(), result.generationMillis(), result.totalMillis());
				if (result.generationMillis() > 20_000L) {
					log.warn("Answer generation took {} ms (> 20 s) for question: \"{}\"",
							result.generationMillis(), question);
				}
				if (!result.warnings().isEmpty()) {
					log.info("   Warnings:        {}", result.warnings());
				}

				boolean hasCitations = !citations.isEmpty();
				boolean missingStated = indicatesMissing(result.answer());

				if (!hasCitations) {
					refused++;
				} else if (missingStated) {
					partial++;
				} else {
					answered++;
				}
			} catch (Exception ex) {
				failed++;
				log.error("Failed to generate answer for query [{} of {}] \"{}\": {}",
						qIdx + 1, questions.size(), question, ex.getMessage(), ex);
			}
		}

		DemoSummary summary = new DemoSummary(answered, partial, refused, failed);
		log.info("");
		log.info("==========================================================================================");
		log.info("Demo Summary: {} answered / {} partial / {} refused (\"couldn't find\") / {} failed",
				summary.answered(), summary.partial(), summary.refused(), summary.failed());
		log.info("==========================================================================================");

		return summary;
	}

	private boolean indicatesMissing(String answer) {
		if (answer == null || answer.isBlank()) {
			return true;
		}
		String lower = answer.toLowerCase();
		return lower.contains("couldn't find")
				|| lower.contains("could not find")
				|| lower.contains("cannot find")
				|| lower.contains("can't find")
				|| lower.contains("is missing")
				|| lower.contains("are missing")
				|| lower.contains("was not found")
				|| lower.contains("were not found")
				|| lower.contains("not found in")
				|| lower.contains("not provided in")
				|| lower.contains("not mentioned in");
	}

	/**
	 * Summary metrics of demo query execution.
	 */
	public record DemoSummary(int answered, int partial, int refused, int failed) {
		public int total() {
			return answered + partial + refused + failed;
		}
	}
}
