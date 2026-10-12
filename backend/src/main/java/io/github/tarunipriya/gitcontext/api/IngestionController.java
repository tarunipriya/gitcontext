package io.github.tarunipriya.gitcontext.api;

import io.github.tarunipriya.gitcontext.ingestion.IngestionJob;
import io.github.tarunipriya.gitcontext.ingestion.IngestionJobService;
import io.github.tarunipriya.gitcontext.ingestion.IngestionResult;
import io.github.tarunipriya.gitcontext.model.RepoRef;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for submitting and monitoring repository ingestion jobs and summaries.
 */
@RestController
public class IngestionController {

	private final IngestionJobService ingestionJobService;

	public IngestionController(IngestionJobService ingestionJobService) {
		this.ingestionJobService = Objects.requireNonNull(ingestionJobService, "ingestionJobService must not be null");
	}

	/**
	 * Submits an ingestion job for the specified repository.
	 *
	 * @param request validated request containing repository reference
	 * @return 202 Accepted with Location header to the job resource and job details in body
	 */
	@PostMapping("/api/ingestions")
	public ResponseEntity<JobResponse> submitIngestion(@Valid @RequestBody IngestRequest request) {
		RepoRef repo = RepoRef.parse(request.repo());
		IngestionJob job = ingestionJobService.submit(repo);
		URI location = URI.create("/api/ingestions/" + job.jobId());
		return ResponseEntity.accepted().location(location).body(JobResponse.from(job));
	}

	/**
	 * Returns the status and metadata for a specific ingestion job.
	 *
	 * @param jobId unique job identifier
	 * @return job status details
	 */
	@GetMapping("/api/ingestions/{jobId}")
	public ResponseEntity<JobResponse> getJob(@PathVariable UUID jobId) {
		IngestionJob job = ingestionJobService.getJob(jobId)
				.orElseThrow(() -> new JobNotFoundException(jobId));
		return ResponseEntity.ok(JobResponse.from(job));
	}

	/**
	 * Returns recent ingestion jobs ordered newest first.
	 *
	 * @return list of recent jobs
	 */
	@GetMapping("/api/ingestions")
	public ResponseEntity<List<JobResponse>> getRecentJobs() {
		List<JobResponse> jobs = ingestionJobService.getRecentJobs().stream()
				.map(JobResponse::from)
				.toList();
		return ResponseEntity.ok(jobs);
	}

	/**
	 * Returns diagnostic summary statistics for the latest completed ingestion of a repository.
	 *
	 * @param owner repository owner
	 * @param name repository name
	 * @return summary response containing counts, commit SHA, and warnings
	 */
	@GetMapping("/api/repos/{owner}/{name}/summary")
	public ResponseEntity<RepoSummaryResponse> getRepoSummary(
			@PathVariable String owner,
			@PathVariable String name
	) {
		RepoRef repo = new RepoRef(owner, name);
		IngestionResult result = ingestionJobService.getLatestResult(repo)
				.orElseThrow(() -> new RepoNotFoundException(repo.fullName()));
		return ResponseEntity.ok(RepoSummaryResponse.from(result));
	}
}
