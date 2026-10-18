package io.github.tarunipriya.gitcontext.indexing.qdrant;

import io.github.tarunipriya.gitcontext.config.GitContextProperties;
import io.github.tarunipriya.gitcontext.indexing.EmbeddingService;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Initialises the Qdrant vector database collection on application startup if reachable.
 */
@Component
public class QdrantStartupInitializer {

	private static final Logger log = LoggerFactory.getLogger(QdrantStartupInitializer.class);

	private final QdrantClient qdrantClient;
	private final EmbeddingService embeddingService;
	private final GitContextProperties properties;

	/**
	 * Creates a new {@link QdrantStartupInitializer}.
	 *
	 * @param qdrantClient low-level Qdrant REST client
	 * @param embeddingService embedding service providing vector dimension
	 * @param properties application properties
	 */
	public QdrantStartupInitializer(
			QdrantClient qdrantClient,
			EmbeddingService embeddingService,
			GitContextProperties properties
	) {
		this.qdrantClient = Objects.requireNonNull(qdrantClient, "qdrantClient must not be null");
		this.embeddingService = Objects.requireNonNull(embeddingService, "embeddingService must not be null");
		this.properties = Objects.requireNonNull(properties, "properties must not be null");
	}

	/**
	 * Tries to ensure collection existence when the application context is fully started.
	 * If Qdrant is offline, logs a warning and allows the application to proceed.
	 */
	@EventListener(ApplicationReadyEvent.class)
	public void onApplicationReady() {
		String collection = properties.qdrant().collection();
		try {
			qdrantClient.ensureCollection(collection, embeddingService.dimension());
			log.info("Qdrant vector store collection '{}' verified and ready", collection);
		} catch (QdrantUnavailableException ex) {
			log.warn("Qdrant vector store could not be reached at startup: {}. The application will retry on first use.", ex.getMessage());
		} catch (Exception ex) {
			log.warn("Failed to initialise Qdrant collection '{}' at startup: {}", collection, ex.getMessage());
		}
	}
}
