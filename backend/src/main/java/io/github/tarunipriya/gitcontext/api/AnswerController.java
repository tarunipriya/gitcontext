package io.github.tarunipriya.gitcontext.api;

import io.github.tarunipriya.gitcontext.api.dto.AskRequest;
import io.github.tarunipriya.gitcontext.api.dto.AskResponse;
import io.github.tarunipriya.gitcontext.generation.AnswerOptions;
import io.github.tarunipriya.gitcontext.generation.AnswerResult;
import io.github.tarunipriya.gitcontext.generation.AnswerService;
import io.github.tarunipriya.gitcontext.indexing.VectorStore;
import io.github.tarunipriya.gitcontext.indexing.qdrant.QdrantUnavailableException;
import io.github.tarunipriya.gitcontext.model.RepoRef;
import io.github.tarunipriya.gitcontext.retrieval.SearchMode;
import io.github.tarunipriya.gitcontext.retrieval.keyword.KeywordIndex;
import jakarta.validation.Valid;
import java.util.Arrays;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller providing grounded answer generation with verified citations against indexed repository knowledge.
 */
@RestController
public class AnswerController {

	private static final Logger log = LoggerFactory.getLogger(AnswerController.class);

	private final AnswerService answerService;
	private final VectorStore vectorStore;
	private final KeywordIndex keywordIndex;

	public AnswerController(
			AnswerService answerService,
			VectorStore vectorStore,
			KeywordIndex keywordIndex
	) {
		this.answerService = Objects.requireNonNull(answerService, "answerService must not be null");
		this.vectorStore = Objects.requireNonNull(vectorStore, "vectorStore must not be null");
		this.keywordIndex = Objects.requireNonNull(keywordIndex, "keywordIndex must not be null");
	}

	/**
	 * Generates a cited answer to a natural language question about an indexed repository.
	 *
	 * @param owner repository owner
	 * @param name repository name
	 * @param request inquiry payload with question and search options
	 * @return grounded answer response
	 */
	@PostMapping("/api/repos/{owner}/{name}/ask")
	public ResponseEntity<AskResponse> ask(
			@PathVariable String owner,
			@PathVariable String name,
			@Valid @RequestBody AskRequest request
	) {
		RepoRef repo = new RepoRef(owner, name);

		if (request == null || request.question() == null || request.question().isBlank()) {
			throw new IllegalArgumentException("question must not be blank");
		}
		String question = request.question().trim();
		if (question.length() > 1000) {
			throw new IllegalArgumentException("question must not exceed 1000 characters");
		}

		if (request.maxSources() != null && (request.maxSources() < 1 || request.maxSources() > 12)) {
			throw new IllegalArgumentException("maxSources must be between 1 and 12");
		}

		SearchMode mode = parseSearchMode(request.mode());
		boolean excludeTests = request.excludeTests() != null ? request.excludeTests() : true;
		boolean rerank = request.rerank() != null ? request.rerank() : false;
		int maxSources = request.maxSources() != null ? request.maxSources() : AnswerOptions.DEFAULT_MAX_SOURCES;

		// Repository existence check in both indexes
		boolean keywordExists = keywordIndex.exists(repo);
		boolean vectorExists = false;
		boolean vectorCheckFailed = false;
		QdrantUnavailableException qdrantEx = null;

		try {
			vectorExists = vectorStore.countRepoPoints(repo) > 0;
		} catch (QdrantUnavailableException ex) {
			vectorCheckFailed = true;
			qdrantEx = ex;
		}

		if (mode == SearchMode.VECTOR && vectorCheckFailed) {
			throw qdrantEx;
		}
		if (mode == SearchMode.HYBRID && vectorCheckFailed && !keywordExists) {
			throw qdrantEx;
		}
		if (!keywordExists && !vectorExists && !vectorCheckFailed) {
			throw new RepoNotFoundException(repo.fullName());
		}

		AnswerOptions options = new AnswerOptions(
				mode,
				excludeTests,
				rerank,
				maxSources,
				AnswerOptions.DEFAULT_CANDIDATES
		);

		AnswerResult result = answerService.ask(repo, question, options);

		String tokensInfo = (result.thoughtsTokens() != null && result.thoughtsTokens() > 0)
				? (result.promptTokens() + "/" + result.outputTokens() + " (thoughts=" + result.thoughtsTokens() + ")")
				: (result.promptTokens() + "/" + result.outputTokens());

		log.info("Ask repo='{}' hitSources={} citations={} model='{}' tokens={} timings: retrieval={}ms, gen={}ms, total={}ms",
				repo.fullName(),
				result.sources().size(),
				result.citations().size(),
				result.model() != null ? result.model() : "none",
				tokensInfo,
				result.retrievalMillis(),
				result.generationMillis(),
				result.totalMillis());

		return ResponseEntity.ok(AskResponse.from(question, result));
	}

	private SearchMode parseSearchMode(String rawMode) {
		if (rawMode == null || rawMode.isBlank()) {
			return SearchMode.HYBRID;
		}
		try {
			return SearchMode.valueOf(rawMode.trim().toUpperCase());
		} catch (IllegalArgumentException ex) {
			String allowed = Arrays.toString(SearchMode.values());
			throw new IllegalArgumentException("Unknown search mode: '" + rawMode + "'. Allowed values are: " + allowed);
		}
	}
}
