package io.github.tarunipriya.gitcontext.retrieval.rerank;

import dev.langchain4j.model.scoring.ScoringModel;
import dev.langchain4j.model.scoring.onnx.OnnxScoringModel;
import io.github.tarunipriya.gitcontext.config.GitContextProperties;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Manages acquisition and lifecycle of local cross-encoder ONNX scoring models.
 *
 * <p>On first use, downloads model weights and tokenizer configuration into {@code data/models/<model-name>/}
 * (resolved relative to the repository root), writing to temporary files before moving into place.
 * Initializes and caches the {@link ScoringModel} instance lazily and thread-safely.
 */
@Component
public class RerankModelProvider {

	private static final Logger log = LoggerFactory.getLogger(RerankModelProvider.class);

	private final GitContextProperties properties;
	private final HttpClient httpClient;
	private volatile ScoringModel scoringModel;

	/**
	 * Creates a new {@link RerankModelProvider}.
	 *
	 * @param properties application configuration properties
	 */
	@org.springframework.beans.factory.annotation.Autowired
	public RerankModelProvider(GitContextProperties properties) {
		this(properties, HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build());
	}

	/**
	 * Creates a new {@link RerankModelProvider} with a custom {@link HttpClient}.
	 *
	 * @param properties application configuration properties
	 * @param httpClient HTTP client configured for downloading model assets
	 */
	public RerankModelProvider(GitContextProperties properties, HttpClient httpClient) {
		this.properties = Objects.requireNonNull(properties, "properties must not be null");
		this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
	}

	/**
	 * Returns the lazily loaded singleton {@link ScoringModel}.
	 *
	 * @return initialized scoring model
	 * @throws IllegalStateException if model assets cannot be downloaded or loaded
	 */
	public ScoringModel getModel() {
		ScoringModel local = this.scoringModel;
		if (local != null) {
			return local;
		}
		synchronized (this) {
			if (this.scoringModel != null) {
				return this.scoringModel;
			}
			this.scoringModel = loadModel();
			return this.scoringModel;
		}
	}

	/**
	 * Returns the local filesystem directory for the current reranker model.
	 *
	 * @return absolute path to the model directory
	 */
	public Path getModelDirectory() {
		String modelName = properties.rerank().modelName();
		return GitContextProperties.resolveToRepoRoot(Path.of("./data/models").resolve(modelName));
	}

	private ScoringModel loadModel() {
		try {
			Path modelDir = getModelDirectory();
			Path modelPath = resolveModelPath(modelDir);
			Path tokenizerPath = resolveTokenizerPath(modelDir);

			ensureAssetExists(properties.rerank().modelUrl(), modelPath, "Model weights");
			ensureAssetExists(properties.rerank().tokenizerUrl(), tokenizerPath, "Tokenizer configuration");

			log.info("Loading local ONNX cross-encoder model from {} and {}", modelPath, tokenizerPath);
			return new OnnxScoringModel(modelPath.toString(), tokenizerPath.toString());
		} catch (Exception e) {
			log.error("Failed to initialize cross-encoder reranking model: {}", e.getMessage(), e);
			throw new IllegalStateException("Failed to initialize cross-encoder reranking model: " + e.getMessage(), e);
		}
	}

	private Path resolveModelPath(Path modelDir) {
		String url = properties.rerank().modelUrl();
		String filename = extractFilename(url, "model.onnx");
		Path preferred = modelDir.resolve(filename);
		if (Files.exists(preferred)) {
			return preferred;
		}
		if (Files.exists(modelDir.resolve("model.onnx"))) {
			return modelDir.resolve("model.onnx");
		}
		if (Files.exists(modelDir.resolve("model_quantized.onnx"))) {
			return modelDir.resolve("model_quantized.onnx");
		}
		return preferred;
	}

	private Path resolveTokenizerPath(Path modelDir) {
		String url = properties.rerank().tokenizerUrl();
		String filename = extractFilename(url, "tokenizer.json");
		return modelDir.resolve(filename);
	}

	private void ensureAssetExists(String urlString, Path targetPath, String description) throws IOException, InterruptedException {
		if (Files.exists(targetPath) && Files.size(targetPath) > 0) {
			log.debug("{} already present at {}", description, targetPath);
			return;
		}

		Files.createDirectories(targetPath.getParent());
		Path tempFile = Files.createTempFile(targetPath.getParent(), targetPath.getFileName().toString() + "-", ".tmp");

		try {
			log.info("Downloading {} from {} ...", description, urlString);
			URI uri = URI.create(urlString);
			HttpRequest request = HttpRequest.newBuilder(uri)
					.GET()
					.header("User-Agent", "gitcontext/1.0")
					.build();

			HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
			if (response.statusCode() >= 400) {
				throw new IOException("HTTP " + response.statusCode() + " downloading " + uri);
			}

			long contentLength = response.headers().firstValueAsLong("Content-Length").orElse(-1L);
			long totalBytesRead = 0L;
			long lastLoggedBytes = 0L;
			long startTime = System.currentTimeMillis();

			try (InputStream in = response.body();
				 OutputStream out = Files.newOutputStream(tempFile)) {
				byte[] buffer = new byte[64 * 1024];
				int read;
				while ((read = in.read(buffer)) != -1) {
					out.write(buffer, 0, read);
					totalBytesRead += read;
					if (totalBytesRead - lastLoggedBytes >= 5 * 1024 * 1024) {
						if (contentLength > 0) {
							int pct = (int) ((totalBytesRead * 100) / contentLength);
							log.info("Downloading {}: {} MB / {} MB ({}%)", description,
									totalBytesRead / (1024 * 1024), contentLength / (1024 * 1024), pct);
						} else {
							log.info("Downloading {}: {} MB", description, totalBytesRead / (1024 * 1024));
						}
						lastLoggedBytes = totalBytesRead;
					}
				}
			}

			long duration = System.currentTimeMillis() - startTime;
			log.info("Downloaded {} ({} bytes in {} ms). Installing to {}", description, totalBytesRead, duration, targetPath);

			try {
				Files.move(tempFile, targetPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(tempFile, targetPath, StandardCopyOption.REPLACE_EXISTING);
			}
			log.info("Successfully installed {} to {}", description, targetPath);
		} finally {
			Files.deleteIfExists(tempFile);
		}
	}

	private static String extractFilename(String urlString, String fallback) {
		try {
			URI uri = URI.create(urlString);
			String path = uri.getPath();
			if (path != null && !path.isBlank()) {
				int idx = path.lastIndexOf('/');
				if (idx >= 0 && idx < path.length() - 1) {
					return path.substring(idx + 1);
				}
			}
		} catch (Exception ignored) {
		}
		return fallback;
	}
}
