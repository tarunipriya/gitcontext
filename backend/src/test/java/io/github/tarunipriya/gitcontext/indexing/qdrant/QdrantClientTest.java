package io.github.tarunipriya.gitcontext.indexing.qdrant;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class QdrantClientTest {

	private MockRestServiceServer mockServer;
	private QdrantClient client;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:6333");
		mockServer = MockRestServiceServer.bindTo(builder).build();
		client = new QdrantClient(builder.build(), "http://localhost:6333", 128);
	}

	@Test
	@DisplayName("ensureCollection creates collection and keyword payload indexes when missing (404)")
	void testEnsureCollectionCreatesWhenMissing() {
		mockServer.expect(requestTo("http://localhost:6333/collections/test_collection"))
				.andExpect(method(HttpMethod.GET))
				.andRespond(withStatus(HttpStatus.NOT_FOUND));

		mockServer.expect(requestTo("http://localhost:6333/collections/test_collection"))
				.andExpect(method(HttpMethod.PUT))
				.andExpect(content().json("{\"vectors\":{\"size\":384,\"distance\":\"Cosine\"}}"))
				.andRespond(withSuccess("{\"status\":\"ok\"}", MediaType.APPLICATION_JSON));

		mockServer.expect(ExpectedCount.times(6), requestTo("http://localhost:6333/collections/test_collection/index"))
				.andExpect(method(HttpMethod.PUT))
				.andRespond(withSuccess("{\"status\":\"ok\"}", MediaType.APPLICATION_JSON));

		client.ensureCollection("test_collection", 384);
		mockServer.verify();
	}

	@Test
	@DisplayName("ensureCollection throws IllegalStateException on vector size mismatch")
	void testEnsureCollectionSizeMismatch() {
		String existingJson = """
				{
				  "result": {
				    "config": {
				      "params": {
				        "vectors": {
				          "size": 768,
				          "distance": "Cosine"
				        }
				      }
				    }
				  },
				  "status": "ok"
				}
				""";

		mockServer.expect(requestTo("http://localhost:6333/collections/test_collection"))
				.andExpect(method(HttpMethod.GET))
				.andRespond(withSuccess(existingJson, MediaType.APPLICATION_JSON));

		assertThatThrownBy(() -> client.ensureCollection("test_collection", 384))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("768")
				.hasMessageContaining("384");

		mockServer.verify();
	}

	@Test
	@DisplayName("upsert batches points correctly (e.g. 300 points with batchSize 128 results in 3 calls)")
	void testUpsertBatching() {
		List<QdrantPoint> points = new ArrayList<>();
		for (int i = 0; i < 300; i++) {
			points.add(new QdrantPoint("id-" + i, new float[]{0.1f * i}, Map.of("key", "val-" + i)));
		}

		mockServer.expect(requestTo("http://localhost:6333/collections/test_collection/points?wait=true"))
				.andExpect(method(HttpMethod.PUT))
				.andExpect(content().string(org.hamcrest.Matchers.containsString("id-0")))
				.andExpect(content().string(org.hamcrest.Matchers.containsString("id-127")))
				.andRespond(withSuccess("{\"status\":\"ok\"}", MediaType.APPLICATION_JSON));

		mockServer.expect(requestTo("http://localhost:6333/collections/test_collection/points?wait=true"))
				.andExpect(method(HttpMethod.PUT))
				.andExpect(content().string(org.hamcrest.Matchers.containsString("id-128")))
				.andExpect(content().string(org.hamcrest.Matchers.containsString("id-255")))
				.andRespond(withSuccess("{\"status\":\"ok\"}", MediaType.APPLICATION_JSON));

		mockServer.expect(requestTo("http://localhost:6333/collections/test_collection/points?wait=true"))
				.andExpect(method(HttpMethod.PUT))
				.andExpect(content().string(org.hamcrest.Matchers.containsString("id-256")))
				.andExpect(content().string(org.hamcrest.Matchers.containsString("id-299")))
				.andRespond(withSuccess("{\"status\":\"ok\"}", MediaType.APPLICATION_JSON));

		client.upsert("test_collection", points, 128);
		mockServer.verify();
	}

	@Test
	@DisplayName("search request sends repo filter and returns mapped scored points")
	void testSearchRequestContainsRepoFilter() {
		String searchResponseJson = """
				{
				  "result": [
				    {
				      "id": "e6a2b5e0-1234-5678-9abc-def012345678",
				      "score": 0.892,
				      "payload": {
				        "repo": "testowner/testrepo",
				        "text": "class GitHubClient {}"
				      }
				    }
				  ],
				  "status": "ok"
				}
				""";

		mockServer.expect(requestTo("http://localhost:6333/collections/test_collection/points/search"))
				.andExpect(method(HttpMethod.POST))
				.andExpect(content().string(org.hamcrest.Matchers.containsString("\"repo\"")))
				.andExpect(content().string(org.hamcrest.Matchers.containsString("\"testowner/testrepo\"")))
				.andRespond(withSuccess(searchResponseJson, MediaType.APPLICATION_JSON));

		Map<String, Object> filter = Map.of(
				"must", List.of(Map.of("key", "repo", "match", Map.of("value", "testowner/testrepo")))
		);

		List<QdrantScoredPoint> results = client.search("test_collection", new float[]{0.1f, 0.2f}, filter, 10);

		assertThat(results).hasSize(1);
		QdrantScoredPoint point = results.get(0);
		assertThat(point.id()).isEqualTo("e6a2b5e0-1234-5678-9abc-def012345678");
		assertThat(point.score()).isEqualTo(0.892);
		assertThat(point.payload()).containsEntry("repo", "testowner/testrepo");
		assertThat(point.payload()).containsEntry("text", "class GitHubClient {}");

		mockServer.verify();
	}

	@Test
	@DisplayName("Connection failure translates into QdrantUnavailableException with actionable guidance")
	void testConnectionFailureGivesQdrantUnavailableException() {
		RestClient deadClient = RestClient.builder()
				.baseUrl("http://127.0.0.1:65534")
				.build();
		QdrantClient unreachableClient = new QdrantClient(deadClient, "http://127.0.0.1:65534", 128);

		assertThatThrownBy(unreachableClient::ping)
				.isInstanceOf(QdrantUnavailableException.class)
				.hasMessageContaining("http://127.0.0.1:65534")
				.hasMessageContaining("docker compose up -d");
	}
}
