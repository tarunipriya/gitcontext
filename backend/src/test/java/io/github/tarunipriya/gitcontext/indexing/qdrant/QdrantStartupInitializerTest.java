package io.github.tarunipriya.gitcontext.indexing.qdrant;

import io.github.tarunipriya.gitcontext.config.GitContextProperties;
import io.github.tarunipriya.gitcontext.indexing.EmbeddingService;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QdrantStartupInitializerTest {

	@Mock
	private QdrantClient qdrantClient;

	@Mock
	private EmbeddingService embeddingService;

	private GitContextProperties properties;
	private QdrantStartupInitializer initializer;

	@BeforeEach
	void setUp() {
		when(embeddingService.dimension()).thenReturn(384);
		properties = new GitContextProperties(
				null,
				Path.of("./data/repos"),
				"http://localhost:6333",
				null,
				null,
				null,
				null
		);
		initializer = new QdrantStartupInitializer(qdrantClient, embeddingService, properties);
	}

	@Test
	@DisplayName("onApplicationReady verifies collection on normal startup")
	void testStartupSuccess() {
		doNothing().when(qdrantClient).ensureCollection("gitcontext_chunks", 384);

		assertThatCode(() -> initializer.onApplicationReady()).doesNotThrowAnyException();
		verify(qdrantClient).ensureCollection("gitcontext_chunks", 384);
	}

	@Test
	@DisplayName("onApplicationReady catches QdrantUnavailableException and allows app to proceed")
	void testStartupQdrantUnavailableHandledGracefully() {
		doThrow(new QdrantUnavailableException("http://localhost:6333", new RuntimeException("connection refused")))
				.when(qdrantClient).ensureCollection(anyString(), anyInt());

		assertThatCode(() -> initializer.onApplicationReady()).doesNotThrowAnyException();
		verify(qdrantClient).ensureCollection("gitcontext_chunks", 384);
	}
}
