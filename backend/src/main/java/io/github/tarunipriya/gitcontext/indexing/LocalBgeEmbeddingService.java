package io.github.tarunipriya.gitcontext.indexing;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.bgesmallenv15q.BgeSmallEnV15QuantizedEmbeddingModel;
import io.github.tarunipriya.gitcontext.config.GitContextProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Local in-process embedding service using the quantized BAAI/bge-small-en-v1.5 ONNX model.
 *
 * <p>Loads the ONNX neural network lazily on first access in a thread-safe manner, batches
 * document embedding requests according to {@link GitContextProperties.EmbeddingProperties#batchSize()},
 * and prepends BGE's query instruction prefix to search queries.
 */
@Service
public class LocalBgeEmbeddingService implements EmbeddingService {

	private static final Logger log = LoggerFactory.getLogger(LocalBgeEmbeddingService.class);
	private static final int DIMENSION = 384;

	private final GitContextProperties properties;
	private volatile EmbeddingModel model;

	/**
	 * Creates a new {@link LocalBgeEmbeddingService}.
	 *
	 * @param properties application configuration properties
	 */
	public LocalBgeEmbeddingService(GitContextProperties properties) {
		this.properties = Objects.requireNonNull(properties, "properties must not be null");
	}

	@Override
	public int dimension() {
		return DIMENSION;
	}

	@Override
	public List<float[]> embedDocuments(List<String> texts) {
		Objects.requireNonNull(texts, "texts must not be null");
		if (texts.isEmpty()) {
			return List.of();
		}

		EmbeddingModel embeddingModel = getOrInitModel();
		int batchSize = properties.embedding().batchSize();
		List<float[]> allVectors = new ArrayList<>(texts.size());

		for (int i = 0; i < texts.size(); i += batchSize) {
			int end = Math.min(i + batchSize, texts.size());
			List<String> batchTexts = texts.subList(i, end);

			long start = System.currentTimeMillis();
			List<TextSegment> segments = batchTexts.stream()
					.map(TextSegment::from)
					.toList();
			List<Embedding> embeddings = embeddingModel.embedAll(segments).content();
			long elapsed = System.currentTimeMillis() - start;

			log.debug("Embedded batch of {} documents in {} ms", batchTexts.size(), elapsed);

			for (Embedding embedding : embeddings) {
				allVectors.add(embedding.vector());
			}
		}

		return allVectors;
	}

	@Override
	public float[] embedQuery(String text) {
		Objects.requireNonNull(text, "query text must not be null");
		EmbeddingModel embeddingModel = getOrInitModel();

		String prefix = properties.embedding().queryPrefix();
		String prefixedText = (prefix != null ? prefix : "") + text;

		long start = System.currentTimeMillis();
		Embedding embedding = embeddingModel.embed(TextSegment.from(prefixedText)).content();
		long elapsed = System.currentTimeMillis() - start;

		log.debug("Embedded query (length: {}) in {} ms", text.length(), elapsed);
		return embedding.vector();
	}

	/**
	 * Thread-safe lazy initialisation of the local ONNX embedding model.
	 */
	private EmbeddingModel getOrInitModel() {
		EmbeddingModel current = this.model;
		if (current == null) {
			synchronized (this) {
				current = this.model;
				if (current == null) {
					long start = System.currentTimeMillis();
					log.debug("Initialising local BGE-small-en-v1.5 quantized embedding model...");
					current = new BgeSmallEnV15QuantizedEmbeddingModel();
					long elapsed = System.currentTimeMillis() - start;
					log.debug("Local BGE embedding model initialised in {} ms", elapsed);
					this.model = current;
				}
			}
		}
		return current;
	}
}
