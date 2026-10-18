package io.github.tarunipriya.gitcontext.indexing.qdrant;

import io.github.tarunipriya.gitcontext.config.GitContextProperties;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;

@ExtendWith(MockitoExtension.class)
class QdrantHealthIndicatorTest {

	@Mock
	private QdrantClient qdrantClient;

	private GitContextProperties properties;
	private QdrantHealthIndicator healthIndicator;

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
		healthIndicator = new QdrantHealthIndicator(qdrantClient, properties);
	}

	@Test
	@DisplayName("health() reports UP with url and collection when ping succeeds")
	void testHealthUpWhenPingSucceeds() {
		doNothing().when(qdrantClient).ping();

		Health health = healthIndicator.health();

		assertThat(health.getStatus()).isEqualTo(Status.UP);
		assertThat(health.getDetails())
				.containsEntry("url", "http://localhost:6333")
				.containsEntry("collection", "gitcontext_chunks");
	}

	@Test
	@DisplayName("health() reports DOWN with reason when ping fails")
	void testHealthDownWhenPingFails() {
		doThrow(new QdrantUnavailableException("http://localhost:6333", new RuntimeException("Connection refused")))
				.when(qdrantClient).ping();

		Health health = healthIndicator.health();

		assertThat(health.getStatus()).isEqualTo(Status.DOWN);
		assertThat(health.getDetails())
				.containsEntry("url", "http://localhost:6333")
				.containsEntry("collection", "gitcontext_chunks")
				.containsKey("reason");
		assertThat(health.getDetails().get("reason").toString())
				.contains("Qdrant is not reachable at http://localhost:6333");
	}
}
