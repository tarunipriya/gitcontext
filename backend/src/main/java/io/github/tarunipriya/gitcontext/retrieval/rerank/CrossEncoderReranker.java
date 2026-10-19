package io.github.tarunipriya.gitcontext.retrieval.rerank;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.scoring.ScoringModel;
import io.github.tarunipriya.gitcontext.config.GitContextProperties;
import io.github.tarunipriya.gitcontext.retrieval.HybridSearchHit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Reranker implementation utilizing an in-process cross-encoder {@link ScoringModel}.
 *
 * <p>Scores query-chunk text pairs in a single batch, truncating candidate chunk content to
 * {@code maxTextChars}, and orders the resulting hits descending by cross-encoder score while
 * preserving their original fused rank position.
 */
@Component
public class CrossEncoderReranker implements Reranker {

	private static final Logger log = LoggerFactory.getLogger(CrossEncoderReranker.class);

	private final RerankModelProvider modelProvider;
	private final int maxTextChars;

	/**
	 * Creates a new {@link CrossEncoderReranker} with managed dependencies.
	 *
	 * @param modelProvider provider for the local ONNX scoring model
	 * @param properties application configuration properties
	 */
	@org.springframework.beans.factory.annotation.Autowired
	public CrossEncoderReranker(RerankModelProvider modelProvider, GitContextProperties properties) {
		this.modelProvider = Objects.requireNonNull(modelProvider, "modelProvider must not be null");
		Objects.requireNonNull(properties, "properties must not be null");
		this.maxTextChars = properties.rerank().maxTextChars();
	}

	/**
	 * Convenience constructor for testing with an explicit {@link ScoringModel} and text limit.
	 *
	 * @param scoringModel pre-configured scoring model
	 * @param maxTextChars maximum chunk character length
	 */
	public CrossEncoderReranker(ScoringModel scoringModel, int maxTextChars) {
		Objects.requireNonNull(scoringModel, "scoringModel must not be null");
		this.modelProvider = new FixedScoringModelProvider(scoringModel);
		this.maxTextChars = maxTextChars > 0 ? maxTextChars : 1000;
	}

	@Override
	public List<RerankedHit> rerank(String query, List<HybridSearchHit> candidates) {
		Objects.requireNonNull(query, "query must not be null");
		if (candidates == null || candidates.isEmpty()) {
			return List.of();
		}

		List<TextSegment> segments = new ArrayList<>(candidates.size());
		for (HybridSearchHit candidate : candidates) {
			String content = candidate.hit().content();
			String text = (content != null) ? content : "";
			if (text.length() > maxTextChars) {
				text = text.substring(0, maxTextChars);
			}
			segments.add(TextSegment.from(text));
		}

		ScoringModel model = modelProvider.getModel();
		log.debug("Scoring {} candidate passages against query '{}' with maxTextChars={}", candidates.size(), query, maxTextChars);
		long scoreStart = System.currentTimeMillis();
		Response<List<Double>> response = model.scoreAll(segments, query);
		long scoreDuration = System.currentTimeMillis() - scoreStart;
		double msPerCandidate = candidates.isEmpty() ? 0.0 : (double) scoreDuration / candidates.size();
		log.debug("Scored {} candidates in {} ms ({} ms/candidate)",
				candidates.size(), scoreDuration, String.format(Locale.ROOT, "%.1f", msPerCandidate));

		List<Double> scores = (response != null && response.content() != null) ? response.content() : List.of();

		if (scores.size() != candidates.size()) {
			throw new IllegalStateException("Cross-encoder scoring returned " + scores.size()
					+ " scores for " + candidates.size() + " candidates");
		}

		List<RerankedHit> rerankedHits = new ArrayList<>(candidates.size());
		for (int i = 0; i < candidates.size(); i++) {
			int fusedRank = i + 1;
			double score = scores.get(i);
			rerankedHits.add(new RerankedHit(candidates.get(i), score, fusedRank));
		}

		return rerankedHits.stream()
				.sorted(Comparator.comparingDouble(RerankedHit::rerankScore).reversed()
						.thenComparingInt(RerankedHit::fusedRank))
				.toList();
	}

	private static final class FixedScoringModelProvider extends RerankModelProvider {
		private final ScoringModel fixedModel;

		private FixedScoringModelProvider(ScoringModel fixedModel) {
			super(new GitContextProperties(null, null, null, null, null, null, null, null, null, null, null, null));
			this.fixedModel = fixedModel;
		}

		@Override
		public ScoringModel getModel() {
			return fixedModel;
		}
	}
}
