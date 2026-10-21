package io.github.tarunipriya.gitcontext.evaluation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Pure, stateless evaluation metrics for ranked retrieval in information retrieval systems.
 * <p>
 * Standard metrics implemented include:
 * <ul>
 *   <li>Hit Rate @ K</li>
 *   <li>Recall @ K</li>
 *   <li>Precision @ K</li>
 *   <li>Reciprocal Rank (RR)</li>
 *   <li>Normalized Discounted Cumulative Gain (nDCG @ K)</li>
 * </ul>
 * <p>
 * All methods automatically normalize file paths (converting Windows backslashes to forward slashes
 * and stripping leading {@code "./"} or {@code "/"}) and deduplicate retrieved items in order of
 * first occurrence before computing metrics.
 */
public final class RetrievalMetrics {

	private RetrievalMetrics() {
		// Utility class; prevent instantiation
	}

	/**
	 * Normalizes a repository file path by converting all backslashes to forward slashes and
	 * stripping any leading {@code "./"} or {@code "/"} prefixes.
	 * <p>
	 * <b>When useful:</b> Ensures cross-platform consistency between retrieved chunks (often
	 * formatted with OS-specific path separators on Windows) and canonical ground-truth file paths.
	 *
	 * @param path raw path string
	 * @return normalized path string
	 * @throws NullPointerException if {@code path} is null
	 */
	public static String normalizePath(String path) {
		Objects.requireNonNull(path, "path must not be null");
		String normalized = path.replace('\\', '/');
		while (normalized.startsWith("./") || normalized.startsWith("/")) {
			if (normalized.startsWith("./")) {
				normalized = normalized.substring(2);
			}
			else {
				normalized = normalized.substring(1);
			}
		}
		return normalized;
	}

	/**
	 * Computes Hit Rate at cutoff {@code k}.
	 * <p>
	 * <b>Formula:</b>
	 * <pre>
	 * HitRate@k = 1.0 if |relevant &cap; top_k| &gt; 0, else 0.0
	 * </pre>
	 * <b>When useful:</b> Measures whether at least one relevant document was retrieved in the
	 * top-k results; useful for evaluating whether the user receives at least one useful piece of
	 * context to answer a question.
	 *
	 * @param retrieved ranked list of retrieved document/file paths (best first)
	 * @param relevant expected relevant document/file paths
	 * @param k rank cutoff (must be &gt; 0)
	 * @return 1.0 if any relevant path is in the top {@code k}, otherwise 0.0
	 * @throws NullPointerException if {@code retrieved} or {@code relevant} is null
	 * @throws IllegalArgumentException if {@code k <= 0} or {@code relevant} is empty
	 */
	public static double hitRateAtK(List<String> retrieved, Set<String> relevant, int k) {
		validateArgs(retrieved, relevant, k);
		List<String> deduped = deduplicateRetrieved(retrieved);
		Set<String> normalizedRelevant = normalizeRelevantSet(relevant);

		int limit = Math.min(k, deduped.size());
		for (int i = 0; i < limit; i++) {
			if (normalizedRelevant.contains(deduped.get(i))) {
				return 1.0;
			}
		}
		return 0.0;
	}

	/**
	 * Computes Recall at cutoff {@code k}.
	 * <p>
	 * <b>Formula:</b>
	 * <pre>
	 * Recall@k = |relevant &cap; top_k| / |relevant|
	 * </pre>
	 * <b>When useful:</b> Measures the fraction of all known relevant documents retrieved in the
	 * top-k; useful for evaluating context completeness when multi-file reasoning is required.
	 *
	 * @param retrieved ranked list of retrieved document/file paths (best first)
	 * @param relevant expected relevant document/file paths
	 * @param k rank cutoff (must be &gt; 0)
	 * @return fraction of relevant documents retrieved in top {@code k}, in range {@code [0.0, 1.0]}
	 * @throws NullPointerException if {@code retrieved} or {@code relevant} is null
	 * @throws IllegalArgumentException if {@code k <= 0} or {@code relevant} is empty
	 */
	public static double recallAtK(List<String> retrieved, Set<String> relevant, int k) {
		validateArgs(retrieved, relevant, k);
		List<String> deduped = deduplicateRetrieved(retrieved);
		Set<String> normalizedRelevant = normalizeRelevantSet(relevant);

		int limit = Math.min(k, deduped.size());
		long hits = 0;
		for (int i = 0; i < limit; i++) {
			if (normalizedRelevant.contains(deduped.get(i))) {
				hits++;
			}
		}
		return (double) hits / normalizedRelevant.size();
	}

	/**
	 * Computes Precision at cutoff {@code k}.
	 * <p>
	 * <b>Formula:</b>
	 * <pre>
	 * Precision@k = |relevant &cap; top_k| / k
	 * </pre>
	 * <i>Note: The denominator is always {@code k}, not the retrieved list size.</i>
	 * <p>
	 * <b>When useful:</b> Measures retrieval signal-to-noise ratio in the top-k context window;
	 * useful for evaluating LLM prompt pollution and token budget efficiency.
	 *
	 * @param retrieved ranked list of retrieved document/file paths (best first)
	 * @param relevant expected relevant document/file paths
	 * @param k rank cutoff (must be &gt; 0)
	 * @return proportion of the top {@code k} slots filled by relevant documents
	 * @throws NullPointerException if {@code retrieved} or {@code relevant} is null
	 * @throws IllegalArgumentException if {@code k <= 0} or {@code relevant} is empty
	 */
	public static double precisionAtK(List<String> retrieved, Set<String> relevant, int k) {
		validateArgs(retrieved, relevant, k);
		List<String> deduped = deduplicateRetrieved(retrieved);
		Set<String> normalizedRelevant = normalizeRelevantSet(relevant);

		int limit = Math.min(k, deduped.size());
		long hits = 0;
		for (int i = 0; i < limit; i++) {
			if (normalizedRelevant.contains(deduped.get(i))) {
				hits++;
			}
		}
		return (double) hits / k;
	}

	/**
	 * Computes Reciprocal Rank (RR).
	 * <p>
	 * <b>Formula:</b>
	 * <pre>
	 * RR = 1 / rank_1 (if at least one relevant document is found), else 0.0
	 * </pre>
	 * where {@code rank_1} is the 1-based index of the first relevant document in {@code retrieved}.
	 * <p>
	 * <b>When useful:</b> Measures how quickly the first relevant document appears; useful for
	 * question answering and search interfaces where the user primarily examines the first hit.
	 *
	 * @param retrieved ranked list of retrieved document/file paths (best first)
	 * @param relevant expected relevant document/file paths
	 * @return reciprocal of the 1-based rank of the first relevant item, or 0.0 if none found
	 * @throws NullPointerException if {@code retrieved} or {@code relevant} is null
	 * @throws IllegalArgumentException if {@code relevant} is empty
	 */
	public static double reciprocalRank(List<String> retrieved, Set<String> relevant) {
		Objects.requireNonNull(retrieved, "retrieved list must not be null");
		Objects.requireNonNull(relevant, "relevant set must not be null");
		if (relevant.isEmpty()) {
			throw new IllegalArgumentException("relevant set must not be empty");
		}

		List<String> deduped = deduplicateRetrieved(retrieved);
		Set<String> normalizedRelevant = normalizeRelevantSet(relevant);

		for (int i = 0; i < deduped.size(); i++) {
			if (normalizedRelevant.contains(deduped.get(i))) {
				return 1.0 / (i + 1);
			}
		}
		return 0.0;
	}

	/**
	 * Computes Normalized Discounted Cumulative Gain at cutoff {@code k} (nDCG@k) with binary relevance
	 * and standard log2 position discount.
	 * <p>
	 * <b>Formula:</b>
	 * <pre>
	 * DCG@k  = &sum;_{i=1}^{min(k, |retrieved|)} (rel_i / log2(i + 1))
	 * IDCG@k = &sum;_{j=1}^{min(k, |relevant|)}  (1.0 / log2(j + 1))
	 * nDCG@k = DCG@k / IDCG@k  (0.0 if DCG@k == 0.0)
	 * </pre>
	 * where {@code rel_i &isin; {0, 1}} indicates binary relevance of the item at 1-based rank {@code i}.
	 * <p>
	 * <b>When useful:</b> Evaluates ranking quality with a smooth logarithmic penalty for relevant
	 * documents appearing at lower ranks; useful when high-ranking hits have higher utility to the reader.
	 *
	 * @param retrieved ranked list of retrieved document/file paths (best first)
	 * @param relevant expected relevant document/file paths
	 * @param k rank cutoff (must be &gt; 0)
	 * @return normalized discounted cumulative gain in range {@code [0.0, 1.0]}
	 * @throws NullPointerException if {@code retrieved} or {@code relevant} is null
	 * @throws IllegalArgumentException if {@code k <= 0} or {@code relevant} is empty
	 */
	public static double ndcgAtK(List<String> retrieved, Set<String> relevant, int k) {
		validateArgs(retrieved, relevant, k);
		List<String> deduped = deduplicateRetrieved(retrieved);
		Set<String> normalizedRelevant = normalizeRelevantSet(relevant);

		int limit = Math.min(k, deduped.size());
		double dcg = 0.0;
		for (int i = 0; i < limit; i++) {
			if (normalizedRelevant.contains(deduped.get(i))) {
				int rank = i + 1;
				dcg += 1.0 / log2(rank + 1);
			}
		}

		if (dcg == 0.0) {
			return 0.0;
		}

		int idealCount = Math.min(k, normalizedRelevant.size());
		double idcg = 0.0;
		for (int j = 0; j < idealCount; j++) {
			int rank = j + 1;
			idcg += 1.0 / log2(rank + 1);
		}

		return dcg / idcg;
	}

	private static double log2(double x) {
		return Math.log(x) / Math.log(2.0);
	}

	private static void validateArgs(List<String> retrieved, Set<String> relevant, int k) {
		Objects.requireNonNull(retrieved, "retrieved list must not be null");
		Objects.requireNonNull(relevant, "relevant set must not be null");
		if (k <= 0) {
			throw new IllegalArgumentException("k must be greater than 0, got: " + k);
		}
		if (relevant.isEmpty()) {
			throw new IllegalArgumentException("relevant set must not be empty");
		}
	}

	private static List<String> deduplicateRetrieved(List<String> retrieved) {
		List<String> deduped = new ArrayList<>(retrieved.size());
		Set<String> seen = new HashSet<>();
		for (String item : retrieved) {
			Objects.requireNonNull(item, "retrieved path item must not be null");
			String normalized = normalizePath(item);
			if (seen.add(normalized)) {
				deduped.add(normalized);
			}
		}
		return deduped;
	}

	private static Set<String> normalizeRelevantSet(Set<String> relevant) {
		Set<String> normalized = new HashSet<>(relevant.size());
		for (String item : relevant) {
			Objects.requireNonNull(item, "relevant path item must not be null");
			normalized.add(normalizePath(item));
		}
		return normalized;
	}
}
