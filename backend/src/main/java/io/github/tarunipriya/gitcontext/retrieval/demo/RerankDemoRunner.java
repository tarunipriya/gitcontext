package io.github.tarunipriya.gitcontext.retrieval.demo;

import io.github.tarunipriya.gitcontext.indexing.VectorStore;
import io.github.tarunipriya.gitcontext.ingestion.IngestionResult;
import io.github.tarunipriya.gitcontext.ingestion.IngestionService;
import io.github.tarunipriya.gitcontext.model.RepoRef;
import io.github.tarunipriya.gitcontext.retrieval.HybridSearchHit;
import io.github.tarunipriya.gitcontext.retrieval.HybridSearchResult;
import io.github.tarunipriya.gitcontext.retrieval.HybridSearchService;
import io.github.tarunipriya.gitcontext.retrieval.SearchMode;
import io.github.tarunipriya.gitcontext.retrieval.keyword.KeywordIndex;
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
 * Demo runner active only when running with the {@code rerank-demo} Spring profile.
 *
 * <p>Reuses already-indexed vector and keyword repositories (ingesting only if missing from both indexes),
 * then evaluates and compares retrieval rankings and latencies between HYBRID (RRF) and HYBRID+RERANK
 * (with {@code excludeTests=true}) across benchmark questions.
 */
@Component
@Profile("rerank-demo")
public class RerankDemoRunner implements CommandLineRunner {

	private static final Logger log = LoggerFactory.getLogger(RerankDemoRunner.class);

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
	private final VectorStore vectorStore;
	private final KeywordIndex keywordIndex;
	private final String demoRepoString;

	/**
	 * Creates a new {@link RerankDemoRunner}.
	 *
	 * @param ingestionService unified ingestion service
	 * @param hybridSearchService hybrid search retrieval service
	 * @param vectorStore vector store facade
	 * @param keywordIndex keyword index facade
	 * @param demoRepoString demo repository string identifier
	 */
	public RerankDemoRunner(
			IngestionService ingestionService,
			HybridSearchService hybridSearchService,
			VectorStore vectorStore,
			KeywordIndex keywordIndex,
			@Value("${gitcontext.demo.repo:tarunipriya/gitcontext}") String demoRepoString
	) {
		this.ingestionService = Objects.requireNonNull(ingestionService, "ingestionService must not be null");
		this.hybridSearchService = Objects.requireNonNull(hybridSearchService, "hybridSearchService must not be null");
		this.vectorStore = Objects.requireNonNull(vectorStore, "vectorStore must not be null");
		this.keywordIndex = Objects.requireNonNull(keywordIndex, "keywordIndex must not be null");
		this.demoRepoString = Objects.requireNonNull(demoRepoString, "demoRepoString must not be null");
	}

	@Override
	public void run(String... args) {
		log.info("==========================================================================================");
		log.info("Starting GitContext Cross-Encoder Reranking Demo for: {}", demoRepoString);
		log.info("==========================================================================================");

		RepoRef repoRef = RepoRef.parse(demoRepoString);

		boolean vectorExists = false;
		try {
			vectorExists = vectorStore.countRepoPoints(repoRef) > 0;
		} catch (Exception e) {
			log.warn("Vector store check encountered error: {}", e.getMessage());
		}
		boolean keywordExists = keywordIndex.exists(repoRef);

		if (!vectorExists && !keywordExists) {
			log.info("Repository missing from both vector and keyword indexes. Performing ingestion...");
			IngestionResult ingestionResult = ingestionService.ingest(repoRef);
			log.info("Ingestion complete. Indexed {} points, {} docs in {} ms",
					ingestionResult.indexedPoints(), ingestionResult.documents().size(), ingestionResult.totalDurationMillis());
		} else {
			log.info("Reusing already-indexed data for {} (vectorExists={}, keywordExists={})",
					repoRef.fullName(), vectorExists, keywordExists);
		}

		log.info("==========================================================================================");
		log.info("Comparing HYBRID vs HYBRID + RERANK (excludeTests=true)");
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

			// 1. HYBRID without reranking (excludeTests = true, filtered for /demo/)
			HybridSearchResult hybridRaw = hybridSearchService.search(
					repoRef,
					question,
					15,
					null,
					null,
					SearchMode.HYBRID,
					false,
					true
			);
			HybridSearchResult hybridResult = filterDemoHits(hybridRaw, 5);

			// 2. HYBRID + RERANK (excludeTests = true, filtered for /demo/)
			HybridSearchResult rerankRaw = hybridSearchService.search(
					repoRef,
					question,
					15,
					null,
					null,
					SearchMode.HYBRID,
					true,
					true
			);
			HybridSearchResult rerankResult = filterDemoHits(rerankRaw, 5);

			printHitsTable("HYBRID", hybridResult);
			printHitsTable("HYBRID + RERANK", rerankResult);

			String hybridRank = findExpectedRank(hybridResult.hits(), expected);
			String rerankRank = findExpectedRank(rerankResult.hits(), expected);

			long hybridMs = hybridResult.tookMillis();
			long rerankMs = rerankResult.rerankMillis();
			long totalRerankTookMs = rerankResult.tookMillis();

			log.info(">> Expected Answer Rank:");
			log.info("   HYBRID:          {} (took {} ms)", hybridRank, hybridMs);
			log.info("   HYBRID + RERANK: {} (hybrid: {} ms, rerank: {} ms | total: {} ms)",
					rerankRank, totalRerankTookMs - rerankMs, rerankMs, totalRerankTookMs);
		}

		log.info("");
		log.info("==========================================================================================");
		log.info("Cross-Encoder Reranking Demo Completed Successfully.");
		log.info("==========================================================================================");

		System.exit(0);
	}

	private void printHitsTable(String label, HybridSearchResult result) {
		log.info("[{}] (took {} ms | vector: {} ms, keyword: {} ms, fusion: {} ms, rerank: {} ms, reranked: {})",
				label,
				result.tookMillis(),
				result.vectorMillis(),
				result.keywordMillis(),
				result.fusionMillis(),
				result.rerankMillis(),
				result.reranked());

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
			if (hit.rerankScore() != null) {
				scoreDisplay = String.format(Locale.ROOT, "RerankScore=%.4f (fusedRank=#%s, RRF=%.4f)",
						hit.rerankScore(),
						hit.fusedRank() != null ? String.valueOf(hit.fusedRank()) : "-",
						hit.fusedScore() != null ? hit.fusedScore() : 0.0);
			} else if (hit.fusedScore() != null) {
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
