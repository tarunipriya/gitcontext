package io.github.tarunipriya.gitcontext.config;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Configuration for asynchronous task execution in GitContext.
 *
 * <p>Provides a bounded executor dedicated to repository ingestion jobs
 * to prevent overwhelming system resources or the common ForkJoinPool,
 * configured to shut down gracefully.
 */
@Configuration
public class IngestionAsyncConfiguration {

	/**
	 * Dedicated 2-thread bounded executor for asynchronous repository ingestion jobs.
	 *
	 * @return configured {@link Executor}
	 */
	@Bean(name = "ingestionExecutor")
	public Executor ingestionExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(2);
		executor.setMaxPoolSize(2);
		executor.setQueueCapacity(100);
		executor.setThreadNamePrefix("ingestion-worker-");
		executor.setWaitForTasksToCompleteOnShutdown(true);
		executor.setAwaitTerminationSeconds(30);
		executor.initialize();
		return executor;
	}
}
