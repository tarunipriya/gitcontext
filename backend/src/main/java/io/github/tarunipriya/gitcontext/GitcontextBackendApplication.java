package io.github.tarunipriya.gitcontext;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Main Spring Boot entry point for GitContext.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class GitcontextBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(GitcontextBackendApplication.class, args);
	}

}
