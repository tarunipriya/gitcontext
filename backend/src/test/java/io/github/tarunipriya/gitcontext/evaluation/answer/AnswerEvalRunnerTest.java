package io.github.tarunipriya.gitcontext.evaluation.answer;

import io.github.tarunipriya.gitcontext.config.GitContextProperties;
import io.github.tarunipriya.gitcontext.evaluation.LatencyStats;
import io.github.tarunipriya.gitcontext.ingestion.IngestionResult;
import io.github.tarunipriya.gitcontext.ingestion.IngestionService;
import io.github.tarunipriya.gitcontext.model.RepoRef;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AnswerEvalRunnerTest {

	private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

	@Test
	@DisplayName("run writes reports and deletes checkpoint when evaluation completes successfully")
	void run_whenEvaluationComplete_writesReportsAndDeletesCheckpoint(@TempDir Path tempDir) throws IOException {
		IngestionService ingestionService = mock(IngestionService.class);
		AnswerEvaluator answerEvaluator = mock(AnswerEvaluator.class);

		Path datasetFile = tempDir.resolve("test-dataset.jsonl");
		String jsonlContent = """
				{"id":"c-1","question":"Q1","expectedPaths":["A.java"],"expectedFacts":["F1"],"category":"cat","answerable":true}
				""";
		Files.writeString(datasetFile, jsonlContent, StandardCharsets.UTF_8);

		Path outputDir = tempDir.resolve("results");
		Files.createDirectories(outputDir);
		Path checkpointFile = outputDir.resolve("answers-test-dataset.checkpoint.jsonl");
		Files.writeString(checkpointFile, "checkpoint-data\n", StandardCharsets.UTF_8);

		GitContextProperties.AnswerEvalProperties answerProps = new GitContextProperties.AnswerEvalProperties(
				datasetFile.toString(),
				"tarunipriya/gitcontext",
				true,
				outputDir.toString(),
				0L,
				true,
				6000,
				0,
				3,
				true,
				"gemini-3.8-flash",
				true
		);

		GitContextProperties properties = new GitContextProperties(
				null, null, "http://localhost:6333", null, null, null, null, null, null, null, null, null, null, null, null, answerProps
		);

		when(ingestionService.ingest(any())).thenReturn(createIngestionResult("sha-123"));

		AnswerCaseResult caseResult = new AnswerCaseResult(
				"c-1", "Q1", true, "Answer 1", false, "gemini-3.5-flash", List.of(), List.of("A.java"), 1.0, true, 1.0, true, List.of(), 10L, 20L, 30L, List.of()
		);
		AnswerEvalReport report = new AnswerEvalReport(
				"test-dataset",
				"tarunipriya/gitcontext",
				"sha-123",
				Map.of("gemini-3.5-flash", 1),
				"gemini-3.8-flash",
				Instant.parse("2026-10-22T00:00:00Z"),
				1,
				0,
				1,
				0,
				0,
				1.0,
				1.0,
				1.0,
				1.0,
				1.0,
				0.0,
				0.0,
				LatencyStats.of(List.of(10L)),
				LatencyStats.of(List.of(20L)),
				LatencyStats.of(List.of(30L)),
				List.of(caseResult)
		);

		when(answerEvaluator.evaluate(any(), any(), eq("sha-123"), any())).thenReturn(Optional.of(report));

		AnswerEvalRunner runner = new AnswerEvalRunner(properties, ingestionService, answerEvaluator);
		runner.run();

		// Checkpoint file must have been deleted
		assertThat(checkpointFile).doesNotExist();

		// Reports must have been generated
		List<Path> writtenFiles;
		try (var stream = Files.list(outputDir)) {
			writtenFiles = stream.toList();
		}
		assertThat(writtenFiles).anyMatch(p -> p.getFileName().toString().endsWith(".json"));
		assertThat(writtenFiles).anyMatch(p -> p.getFileName().toString().endsWith(".md"));
	}

	@Test
	@DisplayName("run preserves checkpoint and does not write final report when evaluation is incomplete")
	void run_whenEvaluationIncomplete_preservesCheckpointAndDoesNotWriteReport(@TempDir Path tempDir) throws IOException {
		IngestionService ingestionService = mock(IngestionService.class);
		AnswerEvaluator answerEvaluator = mock(AnswerEvaluator.class);

		Path datasetFile = tempDir.resolve("test-dataset.jsonl");
		String jsonlContent = """
				{"id":"c-1","question":"Q1","expectedPaths":["A.java"],"expectedFacts":["F1"],"category":"cat","answerable":true}
				""";
		Files.writeString(datasetFile, jsonlContent, StandardCharsets.UTF_8);

		Path outputDir = tempDir.resolve("results");
		Files.createDirectories(outputDir);
		Path checkpointFile = outputDir.resolve("answers-test-dataset.checkpoint.jsonl");
		Files.writeString(checkpointFile, "checkpoint-data\n", StandardCharsets.UTF_8);

		GitContextProperties.AnswerEvalProperties answerProps = new GitContextProperties.AnswerEvalProperties(
				datasetFile.toString(),
				"tarunipriya/gitcontext",
				true,
				outputDir.toString(),
				0L,
				true,
				6000,
				0,
				3,
				true,
				"gemini-3.8-flash",
				true
		);

		GitContextProperties properties = new GitContextProperties(
				null, null, "http://localhost:6333", null, null, null, null, null, null, null, null, null, null, null, null, answerProps
		);

		when(ingestionService.ingest(any())).thenReturn(createIngestionResult("sha-123"));
		when(answerEvaluator.evaluate(any(), any(), eq("sha-123"), any())).thenReturn(Optional.empty());

		AnswerEvalRunner runner = new AnswerEvalRunner(properties, ingestionService, answerEvaluator);
		runner.run();

		// Checkpoint file must be preserved
		assertThat(checkpointFile).exists();

		// No .json or .md report written
		List<Path> writtenFiles;
		try (var stream = Files.list(outputDir)) {
			writtenFiles = stream.toList();
		}
		assertThat(writtenFiles).noneMatch(p -> p.getFileName().toString().endsWith(".md"));
		assertThat(writtenFiles).allMatch(p -> p.getFileName().toString().endsWith(".checkpoint.jsonl"));
	}

	@Test
	@DisplayName("run with reingest=false takes indexedCommitSha from existing checkpoint")
	void run_withReingestFalse_takesShaFromCheckpoint(@TempDir Path tempDir) throws IOException {
		IngestionService ingestionService = mock(IngestionService.class);
		AnswerEvaluator answerEvaluator = mock(AnswerEvaluator.class);

		Path datasetFile = tempDir.resolve("test-dataset.jsonl");
		String jsonlContent = """
				{"id":"c-1","question":"Q1","expectedPaths":["A.java"],"expectedFacts":["F1"],"category":"cat","answerable":true}
				""";
		Files.writeString(datasetFile, jsonlContent, StandardCharsets.UTF_8);

		Path outputDir = tempDir.resolve("results");
		Files.createDirectories(outputDir);
		Path checkpointFile = outputDir.resolve("answers-test-dataset.checkpoint.jsonl");

		AnswerCaseResult caseResult = new AnswerCaseResult(
				"c-1", "Q1", true, "Ans", false, "gemini-3.5-flash", List.of(), List.of("A.java"), 1.0, true, 1.0, true, List.of(), 10L, 20L, 30L, List.of()
		);
		AnswerCheckpointEntry entry = AnswerCheckpointEntry.from(caseResult, "sha-from-checkpoint", "gemini-3.5-flash", "gemini-3.8-flash");
		Files.writeString(checkpointFile, JSON_MAPPER.writeValueAsString(entry) + "\n", StandardCharsets.UTF_8);

		GitContextProperties.AnswerEvalProperties answerProps = new GitContextProperties.AnswerEvalProperties(
				datasetFile.toString(),
				"tarunipriya/gitcontext",
				false, // reingest=false
				outputDir.toString(),
				0L,
				true,
				6000,
				0,
				3,
				true,
				"gemini-3.8-flash",
				true
		);

		GitContextProperties properties = new GitContextProperties(
				null, null, "http://localhost:6333", null, null, null, null, null, null, null, null, null, null, null, null, answerProps
		);

		when(answerEvaluator.evaluate(any(), any(), any(), any())).thenReturn(Optional.empty());

		AnswerEvalRunner runner = new AnswerEvalRunner(properties, ingestionService, answerEvaluator);
		runner.run();

		// IngestionService was never called
		verify(ingestionService, never()).ingest(any());

		// AnswerEvaluator was called with sha from checkpoint
		ArgumentCaptor<String> shaCaptor = ArgumentCaptor.forClass(String.class);
		verify(answerEvaluator).evaluate(any(), any(), shaCaptor.capture(), any());
		assertThat(shaCaptor.getValue()).isEqualTo("sha-from-checkpoint");
	}

	private IngestionResult createIngestionResult(String sha) {
		return new IngestionResult(
				new RepoRef("tarunipriya", "gitcontext"),
				sha,
				List.of(),
				Map.of(),
				Map.of(),
				100L,
				List.of()
		);
	}
}
