package io.github.tarunipriya.gitcontext.retrieval.demo;

import io.github.tarunipriya.gitcontext.ingestion.IngestionResult;
import io.github.tarunipriya.gitcontext.ingestion.IngestionService;
import io.github.tarunipriya.gitcontext.model.RepoRef;
import io.github.tarunipriya.gitcontext.retrieval.HybridSearchHit;
import io.github.tarunipriya.gitcontext.retrieval.HybridSearchResult;
import io.github.tarunipriya.gitcontext.retrieval.HybridSearchService;
import io.github.tarunipriya.gitcontext.retrieval.SearchMode;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Demo runner active only when running with the {@code hybrid-demo} Spring profile.
 *
 * <p>Ingests and indexes the demo repository into both Qdrant (dense vector embeddings)
 * and Apache Lucene (sparse BM25 index), then evaluates retrieval quality across
 * VECTOR, KEYWORD, and HYBRID (RRF) modes for evaluation questions.
 */
@Component
@Profile("hybrid-demo")
public class HybridDemoRunner implements CommandLineRunner {

	private static final Logger log = LoggerFactory.getLogger(HybridDemoRunner.class);

	private record DemoEvaluationItem(String question, Set<String> expectedSymbols) {
	}

	private static final List<DemoEvaluationItem> EVALUATION_ITEMS = List.of(
			new DemoEvaluationItem("how does the app handle GitHub API rate limits?", Set.of("checkRateLimit")),
			new DemoEvaluationItem("GitHub API rate limit exhausted", Set.of("checkRateLimit", "getPaged")),
			new DemoEvaluationItem("where are markdown headings parsed into sections?", Set.of("parseSections")),
			new DemoEvaluationItem("how is a repository cloned or updated?", Set.of("cloneOrUpdate")),
			new DemoEvaluationItem("extractParagraphs", Set.of("extractParagraphs"))
	);

	private final IngestionService ingestionService;
	private final HybridSearchService hybridSearchService;
	private final String demoRepoString;

	/**
	 * Creates a new {@link HybridDemoRunner}.
	 *
	 * @param ingestionService unified ingestion service
	 * @param hybridSearchService hybrid search retrieval service
	 * @param demoRepoString demo repository string identifier
	 */
	public HybridDemoRunner(
			IngestionService ingestionService,
			HybridSearchService hybridSearchService,
			@Value("${gitcontext.demo.repo:tarunipriya/gitcontext}") String demoRepoString
	) {
		this.ingestionService = Objects.requireNonNull(ingestionService, "ingestionService must not be null");
		this.hybridSearchService = Objects.requireNonNull(hybridSearchService, "hybridSearchService must not be null");
		this.demoRepoString = Objects.requireNonNull(demoRepoString, "demoRepoString must not be null");
	}

	@Override
	public void run(String... args) {
		log.info("==========================================================================================");
		log.info("Starting GitContext Hybrid Retrieval (Lucene BM25 + Qdrant RRF) Demo for: {}", demoRepoString);
		log.info("==========================================================================================");

		RepoRef repoRef = RepoRef.parse(demoRepoString);
		IngestionResult ingestionResult = ingestionService.ingest(repoRef);

		log.info("---------------- Ingestion & Indexing Summary ----------------");
		log.info("Total Documents:       {}", ingestionResult.documents().size());
		log.info("Total Chunks:          {}", ingestionResult.chunks().size());
		log.info("Vector Points Indexed: {}", ingestionResult.indexedPoints());
		log.info("Embedding Duration:    {} ms", ingestionResult.embedMillis());
		log.info("Qdrant Upsert:         {} ms", ingestionResult.upsertMillis());
		log.info("Total Ingestion Took:  {} ms", ingestionResult.totalDurationMillis());
		if (!ingestionResult.warnings().isEmpty()) {
			log.warn("Ingestion Warnings:    {}", ingestionResult.warnings());
		}

		log.info("==========================================================================================");
		log.info("Comparing Retrieval Modes: VECTOR vs KEYWORD vs HYBRID (RRF)");
		log.info("Note: Running with excludeTests=true and excluding paths containing '/demo/' to prevent data");
		log.info("leakage, because demo runner source files literally contain the benchmark questions.");
		log.info("==========================================================================================");

		for (DemoEvaluationItem item : EVALUATION_ITEMS) {
			String question = item.question();
			Set<String> expected = item.expectedSymbols();

			log.info("");
			log.info("------------------------------------------------------------------------------------------");
			log.info("QUESTION: \"{}\"", question);
			log.info("EXPECTED ANSWER SYMBOL(S): {}", expected);
			log.info("------------------------------------------------------------------------------------------");

			HybridSearchResult vectorRaw = hybridSearchService.search(repoRef, question, 15, null, null, SearchMode.VECTOR, false, true);
			HybridSearchResult vectorResult = filterDemoHits(vectorRaw, 5);

			HybridSearchResult keywordRaw = hybridSearchService.search(repoRef, question, 15, null, null, SearchMode.KEYWORD, false, true);
			HybridSearchResult keywordResult = filterDemoHits(keywordRaw, 5);

			HybridSearchResult hybridRaw = hybridSearchService.search(repoRef, question, 15, null, null, SearchMode.HYBRID, false, true);
			HybridSearchResult hybridResult = filterDemoHits(hybridRaw, 5);

			printModeTable(SearchMode.VECTOR, vectorResult);
			printModeTable(SearchMode.KEYWORD, keywordResult);
			printModeTable(SearchMode.HYBRID, hybridResult);

			String vectorRank = findExpectedRank(vectorResult.hits(), expected);
			String keywordRank = findExpectedRank(keywordResult.hits(), expected);
			String hybridRank = findExpectedRank(hybridResult.hits(), expected);

			log.info(">> Summary of Expected Rank for: {}", expected);
			log.info("   VECTOR:  {}", vectorRank);
			log.info("   KEYWORD: {}", keywordRank);
			log.info("   HYBRID:  {}", hybridRank);
		}

		log.info("");
		log.info("==========================================================================================");
		log.info("Hybrid Retrieval Demo Finished Successfully.");
		log.info("==========================================================================================");

		System.exit(0);
	}

	private void printModeTable(SearchMode mode, HybridSearchResult result) {
		log.info("[MODE: {}] (took {} ms | vector: {} ms, keyword: {} ms, fusion: {} ms)",
				mode, result.tookMillis(), result.vectorMillis(), result.keywordMillis(), result.fusionMillis());

		List<HybridSearchHit> hits = result.hits();
		if (hits.isEmpty()) {
			log.info("   (No matching hits)");
			return;
		}

		for (int i = 0; i < hits.size(); i++) {
			HybridSearchHit hit = hits.get(i);
			String symbol = hit.hit().symbolName() != null ? hit.hit().symbolName() : "(none)";
			String lines = (hit.hit().startLine() != null && hit.hit().endLine() != null)
					? hit.hit().startLine() + "-" + hit.hit().endLine()
					: (hit.hit().startLine() != null ? String.valueOf(hit.hit().startLine()) : "n/a");

			String scoreDisplay;
			if (mode == SearchMode.HYBRID && hit.fusedScore() != null) {
				scoreDisplay = String.format(Locale.ROOT, "RRF=%.4f (vec=%.3f #%s, kw=%.2f #%s)",
						hit.fusedScore(),
						hit.vectorScore() != null ? hit.vectorScore() : 0.0,
						hit.vectorRank() != null ? String.valueOf(hit.vectorRank()) : "-",
						hit.keywordScore() != null ? hit.keywordScore() : 0.0,
						hit.keywordRank() != null ? String.valueOf(hit.keywordRank()) : "-");
			} else {
				scoreDisplay = String.format(Locale.ROOT, "Score=%.3f", hit.primaryScore());
			}

			log.info("   #{} [{}] Type: {}, Symbol: {}, Path: {}, Lines: {}",
					i + 1,
					scoreDisplay,
					hit.hit().chunkType(),
					symbol,
					hit.hit().path(),
					lines);
		}
	}

	private String findExpectedRank(List<HybridSearchHit> hits, Set<String> expectedSymbols) {
		for (int i = 0; i < hits.size(); i++) {
			String symbol = hits.get(i).hit().symbolName();
			if (symbol != null && expectedSymbols.contains(symbol)) {
				return "#" + (i + 1) + " (" + symbol + ")";
			}
		}
		return "not in top " + hits.size();
	}

	private HybridSearchResult filterDemoHits(HybridSearchResult result, int limit) {
		List<HybridSearchHit> filtered = result.hits().stream()
				.filter(h -> h.hit().path() == null || !h.hit().path().replace('\\', '/').contains("/demo/"))
				.limit(limit)
				.toList();
		return new HybridSearchResult(
				filtered,
				result.mode(),
				result.reranked(),
				result.warnings(),
				result.vectorMillis(),
				result.keywordMillis(),
				result.fusionMillis(),
				result.rerankMillis(),
				result.tookMillis()
		);
	}
}
