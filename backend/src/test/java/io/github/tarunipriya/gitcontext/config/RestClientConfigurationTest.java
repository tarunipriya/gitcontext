package io.github.tarunipriya.gitcontext.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

class RestClientConfigurationTest {

	@Test
	@DisplayName("restClientBuilder returns a non-null RestClient.Builder instance")
	void testRestClientBuilder() {
		RestClientConfiguration configuration = new RestClientConfiguration();
		RestClient.Builder builder = configuration.restClientBuilder();

		assertThat(builder).isNotNull();
		assertThat(builder.build()).isNotNull();
	}
}
