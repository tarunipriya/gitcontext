package io.github.tarunipriya.gitcontext.api;

import io.github.tarunipriya.gitcontext.ingestion.IngestionJob;
import io.github.tarunipriya.gitcontext.ingestion.IngestionJobService;
import io.github.tarunipriya.gitcontext.ingestion.IngestionResult;
import io.github.tarunipriya.gitcontext.model.RepoRef;
import io.github.tarunipriya.gitcontext.retrieval.keyword.KeywordIndex;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for submitting and monitoring repository ingestion jobs and summaries.
 */
@Tag(name = "Ingestion", description = "Repository ingestion management and status tracking")
@RestController
public class IngestionController {

	private final IngestionJobService ingestionJobService;
	private final KeywordIndex keywordIndex;

	public IngestionController(IngestionJobService ingestionJobService, KeywordIndex keywordIndex) {
		this.ingestionJobService = Objects.requireNonNull(ingestionJobService, "ingestionJobService must not be null");
		this.keywordIndex = Objects.requireNonNull(keywordIndex, "keywordIndex must not be null");
	}

	/**
	 * Submits an ingestion job for the specified repository.
	 *
	 * @param request validated request containing repository reference
	 * @return 202 Accepted with Location header to the job resource and job details in body
	 */
	@Operation(summary = "Submit an ingestion job for a repository")
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
	@Operation(summary = "Get status and details of an ingestion job")
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
	@Operation(summary = "List recent ingestion jobs")
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
	@Operation(summary = "Get diagnostic ingestion summary for a repository")
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

	/**
	 * Returns summary statistics for all indexed repositories found in the keyword index directory.
	 *
	 * @return list of repository summaries ordered by repository name
	 */
	@Operation(summary = "List all indexed repositories")
	@GetMapping("/api/repos")
	public ResponseEntity<List<RepoSummaryResponse>> listRepos() {
		List<RepoRef> repos = keywordIndex.listIndexedRepos();
		List<RepoSummaryResponse> summaries = new ArrayList<>();
		for (RepoRef repo : repos) {
			RepoSummaryResponse summary = ingestionJobService.getLatestResult(repo)
					.map(RepoSummaryResponse::from)
					.orElseGet(() -> new RepoSummaryResponse(
							repo.fullName(),
							null,
							Map.of(),
							keywordIndex.getDocumentCount(repo),
							Map.of(),
							0.0,
							0,
							keywordIndex.getDocumentCount(repo),
							0L,
							0L,
							keywordIndex.getLastModified(repo),
							List.of()
					));
			summaries.add(summary);
		}
		return ResponseEntity.ok(summaries);
	}
}
