package io.github.tarunipriya.gitcontext.retrieval.rerank;

import dev.langchain4j.model.scoring.ScoringModel;
import io.github.tarunipriya.gitcontext.config.GitContextProperties;
import io.github.tarunipriya.gitcontext.indexing.SearchHit;
import io.github.tarunipriya.gitcontext.model.ChunkType;
import io.github.tarunipriya.gitcontext.model.SourceType;
import io.github.tarunipriya.gitcontext.retrieval.HybridSearchHit;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real-model integration test for the local ONNX cross-encoder reranker.
 *
 * <p>Annotated and conditionalized so it gracefully skips when model files cannot be downloaded
 * (e.g. offline environment or no internet connectivity).
 */
@Tag("integration")
class CrossEncoderRerankerRealModelTest {

	private SearchHit createHit(String chunkId, String content) {
		return new SearchHit(
				"id-" + chunkId,
				0.5,
				"demo/repo",
				"run1",
				chunkId,
				"doc-" + chunkId,
				0,
				SourceType.CODE,
				ChunkType.FUNCTION,
				"src/" + chunkId + ".java",
				"java",
				"fn",
				null,
				null,
				1,
				20,
				30,
				"https://github.com/demo/repo/blob/main/src/" + chunkId + ".java",
				"sha1",
				content,
				content
		);
	}

	@Test
	@DisplayName("Real ONNX model scores a relevant passage higher than an unrelated one")
	void realModel_relevantPassageScoresHigherThanUnrelated() {
		GitContextProperties.RerankProperties rerankProps = new GitContextProperties.RerankProperties(
				true,
				"ms-marco-MiniLM-L-6-v2",
				"https://huggingface.co/Xenova/ms-marco-MiniLM-L-6-v2/resolve/main/onnx/model_quantized.onnx",
				"https://huggingface.co/Xenova/ms-marco-MiniLM-L-6-v2/resolve/main/tokenizer.json",
				30,
				2000
		);

		GitContextProperties props = new GitContextProperties(
				null,
				Path.of("./data/repos"),
				"http://localhost:6333",
				null,
				null,
				null,
				null,
				null,
				null,
				null,
				null,
				rerankProps
		);

		RerankModelProvider provider = new RerankModelProvider(props);
		ScoringModel model;
		try {
			model = provider.getModel();
		} catch (Exception e) {
			Assumptions.abort("Skipping real-model test: unable to download or load model (no internet or runtime error): " + e.getMessage());
			return;
		}

		CrossEncoderReranker reranker = new CrossEncoderReranker(model, 2000);

		String query = "how to stage and commit changes in git";
		String relevantText = "The git commit command saves staged changes in a new commit object along with a commit message and author info.";
		String unrelatedText = "To bake chocolate chip cookies, preheat the oven to 375 degrees and cream the butter and sugars together.";

		HybridSearchHit relevantHit = new HybridSearchHit(createHit("relevant", relevantText), 0.5, null, null, null, null);
		HybridSearchHit unrelatedHit = new HybridSearchHit(createHit("unrelated", unrelatedText), 0.5, null, null, null, null);

		// Pass unrelated first to verify reranking flips order
		List<RerankedHit> results = reranker.rerank(query, List.of(unrelatedHit, relevantHit));

		assertThat(results).hasSize(2);

		RerankedHit topHit = results.get(0);
		RerankedHit bottomHit = results.get(1);

		assertThat(topHit.candidate().hit().chunkId()).isEqualTo("relevant");
		assertThat(bottomHit.candidate().hit().chunkId()).isEqualTo("unrelated");
		assertThat(topHit.rerankScore())
				.as("Relevant passage should score significantly higher than unrelated passage")
				.isGreaterThan(bottomHit.rerankScore());
	}
}
