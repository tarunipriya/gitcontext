package io.github.tarunipriya.gitcontext.retrieval.rerank;

import io.github.tarunipriya.gitcontext.retrieval.HybridSearchHit;
import java.util.List;

/**
 * Contract for cross-encoder rerankers scoring and re-ordering hybrid search candidates.
 */
public interface Reranker {

	/**
	 * Reranks a list of candidate hits against the specified query.
	 *
	 * @param query search query or question
	 * @param candidates candidate search hits
	 * @return list of reranked hits sorted in descending order of rerank score
	 */
	List<RerankedHit> rerank(String query, List<HybridSearchHit> candidates);
}
