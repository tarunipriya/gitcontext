package io.github.tarunipriya.gitcontext.evaluation.answer;

import io.github.tarunipriya.gitcontext.config.GitContextProperties;
import io.github.tarunipriya.gitcontext.evaluation.LatencyStats;
import io.github.tarunipriya.gitcontext.evaluation.SourceKeys;
import io.github.tarunipriya.gitcontext.evaluation.dataset.EvalCase;
import io.github.tarunipriya.gitcontext.evaluation.dataset.EvalDataset;
import io.github.tarunipriya.gitcontext.generation.AnswerOptions;
import io.github.tarunipriya.gitcontext.generation.AnswerResult;
import io.github.tarunipriya.gitcontext.generation.AnswerService;
import io.github.tarunipriya.gitcontext.generation.Source;
import io.github.tarunipriya.gitcontext.model.RepoRef;
import io.github.tarunipriya.gitcontext.retrieval.HybridSearchService;
import io.github.tarunipriya.gitcontext.retrieval.SearchMode;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

/**
 * Service that drives answer quality evaluation across a benchmark dataset.
 * <p>
 * Generates answers for each case via {@link AnswerService#ask}, tracks citation recall,
 * tests refusal accuracy, and invokes the {@link AnswerJudge} to score factual coverage
 * and grounding against cited source passages while enforcing inter-request delays,
 * checkpoint persistence, and fail-fast abort on exhausted quotas.
 */
@Service
public class AnswerEvaluator {

	private static final Logger log = LoggerFactory.getLogger(AnswerEvaluator.class);
	private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

	@FunctionalInterface
	public interface Sleeper {
		void sleep(long millis) throws InterruptedException;
	}

	private final AnswerService answerService;
	private final AnswerJudge answerJudge;
	private final HybridSearchService hybridSearchService;
	private final GitContextProperties properties;
	private final Sleeper sleeper;

	@Autowired
	public AnswerEvaluator(
			AnswerService answerService,
			AnswerJudge answerJudge,
			HybridSearchService hybridSearchService,
			GitContextProperties properties
	) {
		this(answerService, answerJudge, hybridSearchService, properties, Thread::sleep);
	}

	public AnswerEvaluator(
			AnswerService answerService,
			AnswerJudge answerJudge,
			HybridSearchService hybridSearchService,
			GitContextProperties properties,
			Sleeper sleeper
	) {
		this.answerService = Objects.requireNonNull(answerService, "answerService must not be null");
		this.answerJudge = Objects.requireNonNull(answerJudge, "answerJudge must not be null");
		this.hybridSearchService = Objects.requireNonNull(hybridSearchService, "hybridSearchService must not be null");
		this.properties = Objects.requireNonNull(properties, "properties must not be null");
		this.sleeper = Objects.requireNonNull(sleeper, "sleeper must not be null");
	}

	/**
	 * Evaluates all cases in the provided benchmark dataset using default answer-eval properties.
	 *
	 * @param dataset benchmark dataset of evaluation cases
	 * @param repo target repository reference
	 * @param indexedCommitSha commit SHA indexed during evaluation
	 * @return aggregated answer evaluation report if all cases completed successfully, or empty if incomplete
	 */
	public Optional<AnswerEvalReport> evaluate(
			EvalDataset dataset,
			RepoRef repo,
			String indexedCommitSha
	) {
		return evaluate(dataset, repo, indexedCommitSha, properties.answerEval());
	}

	/**
	 * Evaluates all cases in the provided benchmark dataset using specified evaluation properties.
	 *
	 * @param dataset benchmark dataset of evaluation cases
	 * @param repo target repository reference
	 * @param indexedCommitSha commit SHA indexed during evaluation
	 * @param config answer evaluation configuration properties
	 * @return aggregated answer evaluation report if all cases completed successfully, or empty if incomplete
	 */
	public Optional<AnswerEvalReport> evaluate(
			EvalDataset dataset,
			RepoRef repo,
			String indexedCommitSha,
			GitContextProperties.AnswerEvalProperties config
	) {
		Objects.requireNonNull(dataset, "dataset must not be null");
		Objects.requireNonNull(repo, "repo must not be null");
		GitContextProperties.AnswerEvalProperties effectiveConfig =
				(config != null) ? config : properties.answerEval();

		Instant startedAt = Instant.now();
		List<EvalCase> cases = dataset.cases();
		int totalCases = cases.size();
		int answerableCount = (int) cases.stream().filter(EvalCase::answerable).count();
		int unanswerableCount = totalCases - answerableCount;

		log.info("Starting answer quality evaluation on dataset '{}' ({} total, {} answerable, {} unanswerable)",
				dataset.name(), totalCases, answerableCount, unanswerableCount);

		Path outputDir = GitContextProperties.resolveToRepoRoot(Path.of(effectiveConfig.outputDir()));
		Path checkpointPath = outputDir.resolve("answers-" + dataset.name() + ".checkpoint.jsonl");

		String expectedAnswerModel = properties.llm().model();
		String expectedJudgeModel = (effectiveConfig.judgeModel() != null && !effectiveConfig.judgeModel().isBlank())
				? effectiveConfig.judgeModel()
				: expectedAnswerModel;

		String effectiveCommitSha = indexedCommitSha;
		Map<String, AnswerCheckpointEntry> checkpointMap = new LinkedHashMap<>();

		if (Files.exists(checkpointPath)) {
			List<AnswerCheckpointEntry> existingEntries = readCheckpointEntries(checkpointPath);
			if (existingEntries == null || existingEntries.isEmpty()) {
				log.warn("Checkpoint {} is empty or unreadable; renaming to *.stale and starting fresh", checkpointPath.getFileName());
				renameToStale(checkpointPath, outputDir, dataset.name());
			} else {
				String firstSha = existingEntries.get(0).indexedCommitSha();
				String firstAnswerModel = existingEntries.get(0).answerModel();

				if (!effectiveConfig.reingest() && ("existing-index".equals(effectiveCommitSha) || effectiveCommitSha == null || effectiveCommitSha.isBlank())) {
					if (firstSha != null && !firstSha.isBlank()) {
						effectiveCommitSha = firstSha;
						log.info("Reingest is false; taking indexedCommitSha '{}' from checkpoint and assuming index is unchanged.", effectiveCommitSha);
					}
				}

				boolean shaMatches = Objects.equals(firstSha, effectiveCommitSha);
				boolean answerModelMatches = Objects.equals(firstAnswerModel, expectedAnswerModel);

				if (!effectiveConfig.resume() || !shaMatches || !answerModelMatches) {
					log.warn("Checkpoint {} mismatch (resume={}, shaMatches={}, answerModelMatches={}; expected SHA={}, answerModel={}; found SHA={}, answerModel={}); renaming to *.stale and starting fresh",
							checkpointPath.getFileName(), effectiveConfig.resume(), shaMatches, answerModelMatches,
							effectiveCommitSha, expectedAnswerModel,
							firstSha, firstAnswerModel);
					renameToStale(checkpointPath, outputDir, dataset.name());
				} else {
					// Last entry per caseId wins
					Map<String, AnswerCheckpointEntry> latestByCase = new LinkedHashMap<>();
					for (AnswerCheckpointEntry entry : existingEntries) {
						latestByCase.put(entry.caseId(), entry);
					}

					List<AnswerCheckpointEntry> differingJudgeEntries = latestByCase.values().stream()
							.filter(e -> !Objects.equals(e.judgeModel(), expectedJudgeModel))
							.toList();

					if (!differingJudgeEntries.isEmpty()) {
						String oldJudgeModel = differingJudgeEntries.stream()
								.map(AnswerCheckpointEntry::judgeModel)
								.filter(jm -> jm != null && !jm.isBlank())
								.findFirst()
								.orElse(existingEntries.get(0).judgeModel());

						int answersKept = (int) differingJudgeEntries.stream()
								.filter(e -> e.answer() != null && !e.answer().isBlank())
								.count();

						log.info("Judge model changed from {} to {}: keeping {} answers and re-judging them",
								oldJudgeModel, expectedJudgeModel, answersKept);

						for (Map.Entry<String, AnswerCheckpointEntry> mapEntry : latestByCase.entrySet()) {
							AnswerCheckpointEntry entry = mapEntry.getValue();
							if (!Objects.equals(entry.judgeModel(), expectedJudgeModel)) {
								mapEntry.setValue(entry.withClearedVerdicts());
							}
						}
					} else {
						log.info("Resuming evaluation from checkpoint {}: found {} recorded entries",
								checkpointPath.getFileName(), latestByCase.size());
					}

					checkpointMap.putAll(latestByCase);
				}
			}
		}

		// Run untimed retrieval-only warm-up using HybridSearchService if dataset has cases
		if (!cases.isEmpty()) {
			String firstQuestion = cases.get(0).question();
			log.info("Running untimed retrieval warm-up with question: '{}'", firstQuestion);
			try {
				hybridSearchService.search(repo, firstQuestion, 8, null, null, null, false, false);
			} catch (Exception ex) {
				log.warn("Retrieval warm-up failed (continuing evaluation): {}", ex.getMessage());
			}
		}

		boolean hasMadeLlmCall = false;
		long requestDelayMs = effectiveConfig.requestDelayMs();
		String modelOverride = effectiveConfig.disableFallback() ? expectedAnswerModel : null;
		AnswerOptions answerOptions = new AnswerOptions(
				SearchMode.HYBRID,
				true,
				false,
				AnswerOptions.DEFAULT_MAX_SOURCES,
				AnswerOptions.DEFAULT_CANDIDATES,
				List.of(),
				modelOverride
		);

		int consecutiveFailures = 0;

		for (int i = 0; i < totalCases; i++) {
			EvalCase evalCase = cases.get(i);
			AnswerCheckpointEntry existing = checkpointMap.get(evalCase.id());
			if (existing != null && existing.isComplete()) {
				log.info("Skipping case [{}/{}]: {} (already completed in checkpoint)", i + 1, totalCases, evalCase.id());
				continue;
			}

			boolean hasExistingAnswer = existing != null && existing.answer() != null && !existing.answer().isBlank();
			boolean needsOnlyJudge = hasExistingAnswer && existing.judgeRequired() && existing.factCoverage() == null;

			if (needsOnlyJudge) {
				log.info("Evaluating case [{}/{}]: {} - '{}' (retrying judge for existing answer)",
						i + 1, totalCases, evalCase.id(), evalCase.question());

				delayIfNeeded(requestDelayMs, hasMadeLlmCall);

				Optional<JudgeVerdict> verdictOpt;
				try {
					verdictOpt = answerJudge.judge(
							evalCase.question(),
							evalCase.expectedFacts(),
							existing.answer(),
							existing.citedSourcesText(),
							expectedJudgeModel
					);
				} catch (Exception ex) {
					log.warn("Judge evaluation call failed with exception for case {}: {}", evalCase.id(), ex.getMessage());
					verdictOpt = Optional.empty();
				}
				hasMadeLlmCall = true;

				if (verdictOpt.isPresent()) {
					JudgeVerdict verdict = verdictOpt.get();
					consecutiveFailures = 0;
					long supportedCount = verdict.factsSupported().stream()
							.filter(Boolean::booleanValue)
							.count();
					Double factCoverage = (double) supportedCount / evalCase.expectedFacts().size();
					Boolean grounded = verdict.grounded();
					List<String> unsupportedClaims = verdict.unsupportedClaims();

					List<String> caseWarnings = new ArrayList<>(existing.warnings());
					caseWarnings.removeIf(w -> w.startsWith("Judge evaluation failed"));

					AnswerCheckpointEntry updated = new AnswerCheckpointEntry(
							existing.caseId(),
							existing.question(),
							existing.answerable(),
							existing.answer(),
							existing.refused(),
							existing.model(),
							existing.citedKeys(),
							existing.expectedKeys(),
							existing.citedExpectedRecall(),
							existing.citedAnyExpected(),
							factCoverage,
							grounded,
							unsupportedClaims,
							existing.retrievalMillis(),
							existing.generationMillis(),
							existing.totalMillis(),
							caseWarnings,
							effectiveCommitSha,
							expectedAnswerModel,
							expectedJudgeModel,
							true,
							existing.citedSourcesText()
					);
					appendCheckpoint(checkpointPath, updated);
					checkpointMap.put(evalCase.id(), updated);
				} else {
					consecutiveFailures++;
					log.warn("Judge evaluation failed for case {} (consecutive failures: {})", evalCase.id(), consecutiveFailures);

					List<String> caseWarnings = new ArrayList<>(existing.warnings());
					if (caseWarnings.stream().noneMatch(w -> w.startsWith("Judge evaluation failed"))) {
						caseWarnings.add("Judge evaluation failed (invalid JSON, wrong fact count, or call error)");
					}

					AnswerCheckpointEntry failedJudge = new AnswerCheckpointEntry(
							existing.caseId(),
							existing.question(),
							existing.answerable(),
							existing.answer(),
							existing.refused(),
							existing.model(),
							existing.citedKeys(),
							existing.expectedKeys(),
							existing.citedExpectedRecall(),
							existing.citedAnyExpected(),
							null,
							null,
							existing.unsupportedClaims(),
							existing.retrievalMillis(),
							existing.generationMillis(),
							existing.totalMillis(),
							caseWarnings,
							effectiveCommitSha,
							expectedAnswerModel,
							expectedJudgeModel,
							true,
							existing.citedSourcesText()
					);
					appendCheckpoint(checkpointPath, failedJudge);
					checkpointMap.put(evalCase.id(), failedJudge);

					if (consecutiveFailures >= effectiveConfig.maxConsecutiveFailures()) {
						log.error("Generation/judge failed consecutively {} times (maxConsecutiveFailures={}). The daily quota is probably exhausted. Aborting evaluation; resume after the reset.",
								consecutiveFailures, effectiveConfig.maxConsecutiveFailures());
						break;
					}
				}
				continue;
			}

			if (existing != null) {
				log.info("Evaluating case [{}/{}]: {} - '{}' (retrying failed generation)", i + 1, totalCases, evalCase.id(), evalCase.question());
			} else {
				log.info("Evaluating case [{}/{}]: {} - '{}'", i + 1, totalCases, evalCase.id(), evalCase.question());
			}

			List<String> expectedKeys = evalCase.expectedPaths().stream()
					.filter(Objects::nonNull)
					.map(SourceKeys::normalizeExpected)
					.distinct()
					.toList();

			// Delay before LLM generation if previous LLM call was dispatched
			delayIfNeeded(requestDelayMs, hasMadeLlmCall);

			AnswerResult answerResult;
			try {
				answerResult = answerService.ask(repo, evalCase.question(), answerOptions);
				hasMadeLlmCall = true;
			} catch (Exception ex) {
				hasMadeLlmCall = true;
				consecutiveFailures++;
				log.warn("Answer generation failed for case {} (consecutive failures: {}): {}", evalCase.id(), consecutiveFailures, ex.getMessage());

				boolean judgeRequired = evalCase.answerable()
						&& effectiveConfig.judgeEnabled()
						&& !evalCase.expectedFacts().isEmpty();

				AnswerCaseResult failedCase = new AnswerCaseResult(
						evalCase.id(),
						evalCase.question(),
						evalCase.answerable(),
						"",
						false,
						null,
						List.of(),
						expectedKeys,
						0.0,
						false,
						null,
						null,
						List.of(),
						0L,
						0L,
						0L,
						List.of("Generation failed: " + ex.getMessage())
				);
				AnswerCheckpointEntry entry = AnswerCheckpointEntry.from(
						failedCase,
						effectiveCommitSha,
						expectedAnswerModel,
						expectedJudgeModel,
						judgeRequired,
						""
				);
				appendCheckpoint(checkpointPath, entry);
				checkpointMap.put(evalCase.id(), entry);

				if (consecutiveFailures >= effectiveConfig.maxConsecutiveFailures()) {
					log.error("Generation/judge failed consecutively {} times (maxConsecutiveFailures={}). The daily quota is probably exhausted. Aborting evaluation; resume after the reset.",
							consecutiveFailures, effectiveConfig.maxConsecutiveFailures());
					break;
				}
				continue;
			}

			// Generation succeeded
			List<String> citedKeys = AnswerCaseResult.extractCitedKeys(answerResult.sources());
			boolean refused = AnswerCaseResult.isRefusal(answerResult.answer());
			double citedExpectedRecall = AnswerCaseResult.calculateCitedExpectedRecall(citedKeys, expectedKeys, evalCase.answerable());
			boolean citedAnyExpected = AnswerCaseResult.calculateCitedAnyExpected(citedKeys, expectedKeys, evalCase.answerable());

			boolean judgeRequired = evalCase.answerable()
					&& !refused
					&& effectiveConfig.judgeEnabled()
					&& !evalCase.expectedFacts().isEmpty();

			String citedSourcesText = judgeRequired
					? formatCitedSourcesText(answerResult.sources(), effectiveConfig.judgeMaxSourceChars())
					: "";

			if (!judgeRequired) {
				consecutiveFailures = 0;
				AnswerCaseResult successCase = new AnswerCaseResult(
						evalCase.id(),
						evalCase.question(),
						evalCase.answerable(),
						answerResult.answer(),
						refused,
						answerResult.model(),
						citedKeys,
						expectedKeys,
						citedExpectedRecall,
						citedAnyExpected,
						null,
						null,
						List.of(),
						answerResult.retrievalMillis(),
						answerResult.generationMillis(),
						answerResult.totalMillis(),
						answerResult.warnings()
				);
				AnswerCheckpointEntry entry = AnswerCheckpointEntry.from(
						successCase,
						effectiveCommitSha,
						expectedAnswerModel,
						expectedJudgeModel,
						false,
						""
				);
				appendCheckpoint(checkpointPath, entry);
				checkpointMap.put(evalCase.id(), entry);
				continue;
			}

			// Judge required for this case
			delayIfNeeded(requestDelayMs, hasMadeLlmCall);

			Optional<JudgeVerdict> verdictOpt;
			try {
				verdictOpt = answerJudge.judge(
						evalCase.question(),
						evalCase.expectedFacts(),
						answerResult.answer(),
						citedSourcesText,
						expectedJudgeModel
				);
			} catch (Exception ex) {
				log.warn("Judge evaluation call failed with exception for case {}: {}", evalCase.id(), ex.getMessage());
				verdictOpt = Optional.empty();
			}
			hasMadeLlmCall = true;

			List<String> caseWarnings = new ArrayList<>(answerResult.warnings());
			if (verdictOpt.isPresent()) {
				JudgeVerdict verdict = verdictOpt.get();
				consecutiveFailures = 0;
				Boolean grounded = verdict.grounded();
				List<String> unsupportedClaims = verdict.unsupportedClaims();

				long supportedCount = verdict.factsSupported().stream()
						.filter(Boolean::booleanValue)
						.count();
				Double factCoverage = (double) supportedCount / evalCase.expectedFacts().size();

				AnswerCheckpointEntry entry = new AnswerCheckpointEntry(
						evalCase.id(),
						evalCase.question(),
						evalCase.answerable(),
						answerResult.answer(),
						refused,
						answerResult.model(),
						citedKeys,
						expectedKeys,
						citedExpectedRecall,
						citedAnyExpected,
						factCoverage,
						grounded,
						unsupportedClaims,
						answerResult.retrievalMillis(),
						answerResult.generationMillis(),
						answerResult.totalMillis(),
						caseWarnings,
						effectiveCommitSha,
						expectedAnswerModel,
						expectedJudgeModel,
						true,
						citedSourcesText
				);
				appendCheckpoint(checkpointPath, entry);
				checkpointMap.put(evalCase.id(), entry);
			} else {
				consecutiveFailures++;
				log.warn("Judge evaluation failed for case {} (consecutive failures: {})", evalCase.id(), consecutiveFailures);
				caseWarnings.add("Judge evaluation failed (invalid JSON, wrong fact count, or call error)");

				AnswerCheckpointEntry entry = new AnswerCheckpointEntry(
						evalCase.id(),
						evalCase.question(),
						evalCase.answerable(),
						answerResult.answer(),
						refused,
						answerResult.model(),
						citedKeys,
						expectedKeys,
						citedExpectedRecall,
						citedAnyExpected,
						null,
						null,
						List.of(),
						answerResult.retrievalMillis(),
						answerResult.generationMillis(),
						answerResult.totalMillis(),
						caseWarnings,
						effectiveCommitSha,
						expectedAnswerModel,
						expectedJudgeModel,
						true,
						citedSourcesText
				);
				appendCheckpoint(checkpointPath, entry);
				checkpointMap.put(evalCase.id(), entry);

				if (consecutiveFailures >= effectiveConfig.maxConsecutiveFailures()) {
					log.error("Generation/judge failed consecutively {} times (maxConsecutiveFailures={}). The daily quota is probably exhausted. Aborting evaluation; resume after the reset.",
							consecutiveFailures, effectiveConfig.maxConsecutiveFailures());
					break;
				}
			}
		}

		// Check if every case in the dataset has completed successfully
		boolean allComplete = cases.stream().allMatch(c -> {
			AnswerCheckpointEntry entry = checkpointMap.get(c.id());
			return entry != null && entry.isComplete() && (!entry.judgeRequired() || Objects.equals(entry.judgeModel(), expectedJudgeModel));
		});

		if (!allComplete) {
			long completeCount = cases.stream().filter(c -> {
				AnswerCheckpointEntry entry = checkpointMap.get(c.id());
				return entry != null && entry.isComplete() && (!entry.judgeRequired() || Objects.equals(entry.judgeModel(), expectedJudgeModel));
			}).count();
			log.info("Evaluation incomplete ({} of {} cases completed). Checkpoint written; no final report generated.",
					completeCount, totalCases);
			return Optional.empty();
		}

		// All cases completed successfully: build final report from checkpoint entries
		List<AnswerCaseResult> caseResults = new ArrayList<>(totalCases);
		List<Long> retrievalMillisList = new ArrayList<>();
		List<Long> generationMillisList = new ArrayList<>();
		List<Long> totalMillisList = new ArrayList<>();
		Map<String, Integer> modelUsage = new LinkedHashMap<>();

		int judgeFailures = 0;
		int judgedCases = 0;

		for (EvalCase evalCase : cases) {
			AnswerCheckpointEntry entry = checkpointMap.get(evalCase.id());
			AnswerCaseResult cr = entry.toCaseResult();
			caseResults.add(cr);

			retrievalMillisList.add(cr.retrievalMillis());
			generationMillisList.add(cr.generationMillis());
			totalMillisList.add(cr.totalMillis());

			String model = (cr.model() != null && !cr.model().isBlank())
					? cr.model()
					: expectedAnswerModel;
			modelUsage.merge(model, 1, Integer::sum);

			if (cr.factCoverage() != null && Objects.equals(entry.judgeModel(), expectedJudgeModel)) {
				judgedCases++;
			} else if (entry.judgeRequired()) {
				judgeFailures++;
			}
		}

		List<AnswerCaseResult> successfulAnswerable = caseResults.stream()
				.filter(AnswerCaseResult::answerable)
				.toList();
		List<AnswerCaseResult> successfulUnanswerable = caseResults.stream()
				.filter(c -> !c.answerable())
				.toList();
		List<AnswerCaseResult> judgedCasesList = caseResults.stream()
				.filter(c -> c.factCoverage() != null)
				.toList();

		double meanFactCoverage = judgedCasesList.isEmpty()
				? 0.0
				: judgedCasesList.stream().mapToDouble(AnswerCaseResult::factCoverage).average().orElse(0.0);

		double fullyCorrectRate = judgedCasesList.isEmpty()
				? 0.0
				: (double) judgedCasesList.stream().filter(c -> c.factCoverage() >= 1.0 - 1e-9).count() / judgedCasesList.size();

		double groundedRate = judgedCasesList.isEmpty()
				? 0.0
				: (double) judgedCasesList.stream().filter(c -> Boolean.TRUE.equals(c.grounded())).count() / judgedCasesList.size();

		double citedAnyExpectedRate = successfulAnswerable.isEmpty()
				? 0.0
				: (double) successfulAnswerable.stream().filter(AnswerCaseResult::citedAnyExpected).count() / successfulAnswerable.size();

		double meanCitedExpectedRecall = successfulAnswerable.isEmpty()
				? 0.0
				: successfulAnswerable.stream().mapToDouble(AnswerCaseResult::citedExpectedRecall).average().orElse(0.0);

		double refusalAccuracy = successfulUnanswerable.isEmpty()
				? 0.0
				: (double) successfulUnanswerable.stream().filter(AnswerCaseResult::refused).count() / successfulUnanswerable.size();

		double falseRefusalRate = successfulAnswerable.isEmpty()
				? 0.0
				: (double) successfulAnswerable.stream().filter(AnswerCaseResult::refused).count() / successfulAnswerable.size();

		LatencyStats retrievalLatency = !retrievalMillisList.isEmpty()
				? LatencyStats.of(retrievalMillisList)
				: new LatencyStats(0, 0L, 0.0, 0L, 0L, 0L, 0L);

		LatencyStats generationLatency = !generationMillisList.isEmpty()
				? LatencyStats.of(generationMillisList)
				: new LatencyStats(0, 0L, 0.0, 0L, 0L, 0L, 0L);

		LatencyStats totalLatency = !totalMillisList.isEmpty()
				? LatencyStats.of(totalMillisList)
				: new LatencyStats(0, 0L, 0.0, 0L, 0L, 0L, 0L);

		AnswerEvalReport report = new AnswerEvalReport(
				dataset.name(),
				repo.fullName(),
				effectiveCommitSha,
				modelUsage,
				expectedJudgeModel,
				startedAt,
				answerableCount,
				unanswerableCount,
				judgedCases,
				judgeFailures,
				0,
				meanFactCoverage,
				fullyCorrectRate,
				groundedRate,
				citedAnyExpectedRate,
				meanCitedExpectedRecall,
				refusalAccuracy,
				falseRefusalRate,
				retrievalLatency,
				generationLatency,
				totalLatency,
				caseResults
		);

		return Optional.of(report);
	}

	private void renameToStale(Path checkpointPath, Path outputDir, String datasetName) {
		Path stalePath = outputDir.resolve("answers-" + datasetName + ".stale");
		try {
			Files.move(checkpointPath, stalePath, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			log.warn("Failed to rename stale checkpoint file {} to {}: {}", checkpointPath, stalePath, e.getMessage());
		}
	}

	private synchronized void appendCheckpoint(Path checkpointPath, AnswerCheckpointEntry entry) {
		try {
			if (checkpointPath.getParent() != null) {
				Files.createDirectories(checkpointPath.getParent());
			}
			String jsonLine = JSON_MAPPER.writeValueAsString(entry);
			Files.writeString(
					checkpointPath,
					jsonLine + "\n",
					StandardCharsets.UTF_8,
					StandardOpenOption.CREATE,
					StandardOpenOption.APPEND
			);
		} catch (IOException e) {
			log.warn("Failed to append checkpoint entry for case {}: {}", entry.caseId(), e.getMessage());
		}
	}

	/**
	 * Reads the indexed commit SHA from an existing checkpoint file if available.
	 *
	 * @param checkpointPath filesystem path to the checkpoint .jsonl file
	 * @return indexed commit SHA from first entry, or null if file missing or empty
	 */
	public static String readCheckpointSha(Path checkpointPath) {
		if (checkpointPath == null || !Files.exists(checkpointPath)) {
			return null;
		}
		List<AnswerCheckpointEntry> entries = readCheckpointEntries(checkpointPath);
		if (entries == null || entries.isEmpty()) {
			return null;
		}
		return entries.get(0).indexedCommitSha();
	}

	/**
	 * Reads all checkpoint entries from the specified checkpoint .jsonl file.
	 *
	 * @param checkpointPath filesystem path to the checkpoint .jsonl file
	 * @return list of entries, or null if reading failed due to corruption
	 */
	public static List<AnswerCheckpointEntry> readCheckpointEntries(Path checkpointPath) {
		if (checkpointPath == null || !Files.exists(checkpointPath)) {
			return List.of();
		}
		List<AnswerCheckpointEntry> entries = new ArrayList<>();
		try (BufferedReader reader = Files.newBufferedReader(checkpointPath, StandardCharsets.UTF_8)) {
			String line;
			while ((line = reader.readLine()) != null) {
				String trimmed = line.trim();
				if (!trimmed.isEmpty()) {
					entries.add(JSON_MAPPER.readValue(trimmed, AnswerCheckpointEntry.class));
				}
			}
			return entries;
		} catch (Exception e) {
			log.warn("Failed to read checkpoint file {}: {}", checkpointPath, e.getMessage());
			return null;
		}
	}

	private void delayIfNeeded(long delayMs, boolean hasMadeLlmCall) {
		if (hasMadeLlmCall && delayMs > 0) {
			try {
				sleeper.sleep(delayMs);
			} catch (InterruptedException ie) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException("Interrupted while waiting between LLM calls", ie);
			}
		}
	}

	static String formatCitedSourcesText(List<Source> sources, int maxChars) {
		if (sources == null || sources.isEmpty()) {
			return "";
		}
		List<Source> citedSources = sources.stream()
				.filter(s -> s != null && s.cited())
				.toList();
		if (citedSources.isEmpty()) {
			return "";
		}

		int citedCount = citedSources.size();
		int perSource = Math.max(500, maxChars / citedCount);

		StringBuilder sb = new StringBuilder();
		for (Source s : citedSources) {
			String header = (s.hit() != null && s.hit().path() != null)
					? s.hit().path()
					: "Source " + s.number();
			String content = (s.hit() != null && s.hit().content() != null)
					? s.hit().content()
					: (s.hit() != null && s.hit().text() != null ? s.hit().text() : "");

			if (content.length() > perSource) {
				content = content.substring(0, perSource) + "…[truncated]";
			}

			if (!sb.isEmpty()) {
				sb.append("\n\n");
			}
			sb.append("[").append(s.number()).append("] ").append(header).append(":\n").append(content);
		}
		return sb.toString();
	}
}
