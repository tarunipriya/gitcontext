package io.github.tarunipriya.gitcontext.indexing;

import io.github.tarunipriya.gitcontext.config.GitContextProperties;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LocalBgeEmbeddingServiceTest {

	private LocalBgeEmbeddingService embeddingService;
	private GitContextProperties properties;

	@BeforeEach
	void setUp() {
		properties = new GitContextProperties(
				null,
				Path.of("./data/repos"),
				"http://localhost:6333",
				null,
				null,
				null,
				null
		);
		embeddingService = new LocalBgeEmbeddingService(properties);
	}

	@Test
	@DisplayName("dimension returns 384 for bge-small-en-v1.5 model")
	void testDimensionIs384() {
		assertThat(embeddingService.dimension()).isEqualTo(384);
	}

	@Test
	@DisplayName("Cosine similarity ranks semantic match higher than unrelated text")
	void testSemanticCosineSimilarity() {
		String query = "how are GitHub API rate limits handled";
		String rateLimitSentence = "The GitHub REST client tracks response headers to handle rate limits and sleep when quota is exhausted.";
		String markdownSentence = "Markdown headings are parsed by section hierarchy to delineate document structure.";

		float[] queryVector = embeddingService.embedQuery(query);
		List<float[]> docVectors = embeddingService.embedDocuments(List.of(rateLimitSentence, markdownSentence));

		assertThat(queryVector).hasSize(384);
		assertThat(docVectors).hasSize(2);
		assertThat(docVectors.get(0)).hasSize(384);
		assertThat(docVectors.get(1)).hasSize(384);

		double rateLimitSimilarity = cosineSimilarity(queryVector, docVectors.get(0));
		double markdownSimilarity = cosineSimilarity(queryVector, docVectors.get(1));

		assertThat(rateLimitSimilarity).isGreaterThan(markdownSimilarity);
	}

	@Test
	@DisplayName("Query prefix instruction is applied to queries only, never to documents")
	void testQueryPrefixUsedOnlyForQueries() {
		String sampleText = "How are repository branches managed?";

		float[] queryEmbedding = embeddingService.embedQuery(sampleText);
		float[] docEmbedding = embeddingService.embedDocuments(List.of(sampleText)).get(0);

		// Without prefix, docEmbedding differs from queryEmbedding which had the BGE instruction prefix prepended
		assertThat(queryEmbedding).isNotEqualTo(docEmbedding);

		// Document explicitly prepended with the prefix matches the query vector
		String explicitPrefixed = properties.embedding().queryPrefix() + sampleText;
		float[] explicitPrefixedDocEmbedding = embeddingService.embedDocuments(List.of(explicitPrefixed)).get(0);

		// Within floating point precision, query embedding matches manually prefixed doc embedding
		double similarity = cosineSimilarity(queryEmbedding, explicitPrefixedDocEmbedding);
		assertThat(similarity).isGreaterThan(0.99999);
	}

	private static double cosineSimilarity(float[] v1, float[] v2) {
		double dot = 0.0;
		double normA = 0.0;
		double normB = 0.0;
		for (int i = 0; i < v1.length; i++) {
			dot += v1[i] * v2[i];
			normA += v1[i] * v1[i];
			normB += v2[i] * v2[i];
		}
		return dot / (Math.sqrt(normA) * Math.sqrt(normB));
	}
}
