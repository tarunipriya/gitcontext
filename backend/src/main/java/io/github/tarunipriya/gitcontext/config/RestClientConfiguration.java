package io.github.tarunipriya.gitcontext.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.web.client.RestClient;

/**
 * Spring configuration providing HTTP client builders.
 */
@Configuration(proxyBeanMethods = false)
public class RestClientConfiguration {

	/**
	 * Provides a {@link RestClient.Builder} bean for HTTP client components.
	 *
	 * @return a new RestClient builder
	 */
	@Bean
	@Scope("prototype")
	public RestClient.Builder restClientBuilder() {
		return RestClient.builder();
	}
}
