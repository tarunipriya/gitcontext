package io.github.tarunipriya.gitcontext.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI configuration declaring the top-level API metadata for GitContext.
 */
@Configuration
public class OpenApiConfiguration {

	/**
	 * Configures the OpenAPI specification metadata for the GitContext API.
	 *
	 * @return configured {@link OpenAPI} instance
	 */
	@Bean
	public OpenAPI gitContextOpenAPI() {
		return new OpenAPI()
				.info(new Info()
						.title("GitContext API")
						.version("0.0.1-SNAPSHOT")
						.description("GitContext API for repository ingestion, AST code chunking, hybrid retrieval, and grounded citation-backed question answering."));
	}
}
