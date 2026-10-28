import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Lightweight JVM-based health check executed by Docker daemon.
 *
 * <p>Uses standard {@link java.net.http.HttpClient} built into the Java 25 runtime
 * to query Spring Boot Actuator's /actuator/health endpoint without requiring
 * external binaries like curl or wget in the minimal JRE container image.
 */
public final class HealthCheck {

	public static void main(String[] args) {
		try {
			HttpClient client = HttpClient.newBuilder()
					.connectTimeout(Duration.ofSeconds(3))
					.build();
			HttpRequest request = HttpRequest.newBuilder()
					.uri(URI.create("http://localhost:8081/actuator/health"))
					.timeout(Duration.ofSeconds(3))
					.GET()
					.build();
			HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() == 200 && response.body().contains("\"status\":\"UP\"")) {
				System.exit(0);
			}
			System.exit(1);
		} catch (Exception ignored) {
			System.exit(1);
		}
	}
}
