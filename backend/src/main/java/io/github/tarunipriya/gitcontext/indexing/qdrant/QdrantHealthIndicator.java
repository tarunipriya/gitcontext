package io.github.tarunipriya.gitcontext.indexing.qdrant;

import io.github.tarunipriya.gitcontext.config.GitContextProperties;
import java.util.Objects;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Spring Boot Actuator HealthIndicator reporting the availability and status of Qdrant.
 */
@Component("qdrant")
public class QdrantHealthIndicator implements HealthIndicator {

	private final QdrantClient qdrantClient;
	private final GitContextProperties properties;

	/**
	 * Creates a new {@link QdrantHealthIndicator}.
	 *
	 * @param qdrantClient low-level Qdrant REST client
	 * @param properties application properties
	 */
	public QdrantHealthIndicator(QdrantClient qdrantClient, GitContextProperties properties) {
		this.qdrantClient = Objects.requireNonNull(qdrantClient, "qdrantClient must not be null");
		this.properties = Objects.requireNonNull(properties, "properties must not be null");
	}

	@Override
	public Health health() {
		String url = properties.qdrant().url();
		String collection = properties.qdrant().collection();
		try {
			qdrantClient.ping();
			return Health.up()
					.withDetail("url", url)
					.withDetail("collection", collection)
					.build();
		} catch (Exception ex) {
			return Health.down()
					.withDetail("url", url)
					.withDetail("collection", collection)
					.withDetail("reason", ex.getMessage())
					.build();
		}
	}
}
