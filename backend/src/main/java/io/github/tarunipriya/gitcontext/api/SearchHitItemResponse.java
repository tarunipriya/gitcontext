package io.github.tarunipriya.gitcontext.api;

import io.github.tarunipriya.gitcontext.indexing.SearchHit;
import io.github.tarunipriya.gitcontext.model.ChunkType;
import io.github.tarunipriya.gitcontext.retrieval.HybridSearchHit;
import java.util.List;

/**
 * Detailed representation of a retrieved search hit in the search API response.
 *
 * @param score primary relevance score rounded to 3 decimal places
 * @param chunkType structural granularity of the chunk
 * @param symbolName AST symbol name or null
 * @param parentSymbol enclosing symbol name or null
 * @param headingPath hierarchy of section headings or null
 * @param path relative file path or null
 * @param language programming or markup language identifier
 * @param startLine 1-based start line or null
 * @param endLine 1-based end line or null
 * @param url citation URL with line anchors or null
 * @param snippet chunk content without context header, truncated to at most 20 lines
 * @param fusedScore Reciprocal Rank Fusion score rounded to 4 decimals (null if not fused)
 * @param vectorScore dense vector similarity score from Qdrant rounded to 3 decimals (null if absent)
 * @param vectorRank 1-based rank in vector search candidate list (null if absent)
 * @param keywordScore sparse lexical BM25 score from Lucene rounded to 3 decimals (null if absent)
 * @param keywordRank 1-based rank in keyword search candidate list (null if absent)
 * @param rerankScore cross-encoder relevance score rounded to 4 decimals (null if not reranked)
 * @param fusedRank 1-based rank in the fused candidate list before reranking (null if not reranked)
 */
public record SearchHitItemResponse(
		double score,
		ChunkType chunkType,
		String symbolName,
		String parentSymbol,
		String headingPath,
		String path,
		String language,
		Integer startLine,
		Integer endLine,
		String url,
		String snippet,
		Double fusedScore,
		Double vectorScore,
		Integer vectorRank,
		Double keywordScore,
		Integer keywordRank,
		Double rerankScore,
		Integer fusedRank
) {

	/**
	 * Backwards-compatible constructor without cross-encoder reranking fields.
	 */
	public SearchHitItemResponse(
			double score,
			ChunkType chunkType,
			String symbolName,
			String parentSymbol,
			String headingPath,
			String path,
			String language,
			Integer startLine,
			Integer endLine,
			String url,
			String snippet,
			Double fusedScore,
			Double vectorScore,
			Integer vectorRank,
			Double keywordScore,
			Integer keywordRank
	) {
		this(score, chunkType, symbolName, parentSymbol, headingPath, path, language,
				startLine, endLine, url, snippet, fusedScore, vectorScore, vectorRank,
				keywordScore, keywordRank, null, null);
	}

	/**
	 * Creates a {@link SearchHitItemResponse} from a {@link HybridSearchHit}.
	 *
	 * @param hybridHit hybrid search hit carrying scores and ranks
	 * @return formatted hit item response
	 */
	public static SearchHitItemResponse from(HybridSearchHit hybridHit) {
		SearchHit hit = hybridHit.hit();
		double primaryScore = hybridHit.primaryScore();
		double roundedScore = Math.round(primaryScore * 1000.0) / 1000.0;
		String snippet = extractSnippet(hit.content());

		Double roundedFused = round(hybridHit.fusedScore(), 4);
		Double roundedVector = round(hybridHit.vectorScore(), 3);
		Double roundedKeyword = round(hybridHit.keywordScore(), 3);
		Double roundedRerank = round(hybridHit.rerankScore(), 4);

		return new SearchHitItemResponse(
				roundedScore,
				hit.chunkType(),
				hit.symbolName(),
				hit.parentSymbol(),
				hit.headingPath(),
				hit.path(),
				hit.language(),
				hit.startLine(),
				hit.endLine(),
				hit.url(),
				snippet,
				roundedFused,
				roundedVector,
				hybridHit.vectorRank(),
				roundedKeyword,
				hybridHit.keywordRank(),
				roundedRerank,
				hybridHit.fusedRank()
		);
	}

	/**
	 * Backwards-compatible factory method from a standard {@link SearchHit}.
	 *
	 * @param hit source search hit
	 * @return formatted hit item response
	 */
	public static SearchHitItemResponse from(SearchHit hit) {
		double roundedScore = Math.round(hit.score() * 1000.0) / 1000.0;
		String snippet = extractSnippet(hit.content());
		return new SearchHitItemResponse(
				roundedScore,
				hit.chunkType(),
				hit.symbolName(),
				hit.parentSymbol(),
				hit.headingPath(),
				hit.path(),
				hit.language(),
				hit.startLine(),
				hit.endLine(),
				hit.url(),
				snippet,
				null,
				roundedScore,
				1,
				null,
				null,
				null,
				null
		);
	}

	private static Double round(Double val, int places) {
		if (val == null) {
			return null;
		}
		double scale = Math.pow(10, places);
		return Math.round(val * scale) / scale;
	}

	private static String extractSnippet(String content) {
		if (content == null || content.isEmpty()) {
			return "";
		}
		List<String> lines = content.lines().limit(20).toList();
		return String.join("\n", lines);
	}
}
