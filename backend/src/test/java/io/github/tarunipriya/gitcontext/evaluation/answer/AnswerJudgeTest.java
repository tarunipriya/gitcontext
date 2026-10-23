package io.github.tarunipriya.gitcontext.evaluation.answer;

import io.github.tarunipriya.gitcontext.generation.LlmClient;
import io.github.tarunipriya.gitcontext.generation.LlmRequest;
import io.github.tarunipriya.gitcontext.generation.LlmResponse;
import io.github.tarunipriya.gitcontext.generation.LlmUnavailableException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AnswerJudgeTest {

	@Test
	@DisplayName("Parses valid raw JSON with fact coverage, groundedness, and claims")
	void testValidJson() {
		LlmClient llmClient = mock(LlmClient.class);
		String json = """
				{
				  "facts": [
				    {"index": 1, "supported": true},
				    {"index": 2, "supported": false}
				  ],
				  "grounded": true,
				  "unsupportedClaims": []
				}
				""";
		when(llmClient.generate(any(LlmRequest.class)))
				.thenReturn(new LlmResponse(json, "gemini-3.5-flash", 100, 50, 0, "STOP", 500L));

		AnswerJudge judge = new AnswerJudge(llmClient);
		Optional<JudgeVerdict> verdictOpt = judge.judge(
				"What vector database is used?",
				List.of("GitContext uses Qdrant", "It connects over port 6333"),
				"GitContext uses Qdrant for storing chunks [1].",
				"[1] QdrantProperties.java: url http://localhost:6333"
		);

		assertThat(verdictOpt).isPresent();
		JudgeVerdict verdict = verdictOpt.get();
		assertThat(verdict.factsSupported()).containsExactly(true, false);
		assertThat(verdict.grounded()).isTrue();
		assertThat(verdict.unsupportedClaims()).isEmpty();

		org.mockito.ArgumentCaptor<LlmRequest> captor = org.mockito.ArgumentCaptor.forClass(LlmRequest.class);
		verify(llmClient).generate(captor.capture());
		assertThat(captor.getValue().temperature()).isEqualTo(0.0);
	}

	@Test
	@DisplayName("Parses markdown fenced JSON with unsupported claims and grounded=false")
	void testFencedJson() {
		LlmClient llmClient = mock(LlmClient.class);
		String responseText = """
				Here is the evaluation:
				```json
				{
				  "facts": [
				    {"index": 1, "supported": true},
				    {"index": 2, "supported": true}
				  ],
				  "grounded": false,
				  "unsupportedClaims": [
				    "GitContext uses PostgreSQL for vector storage"
				  ]
				}
				```
				""";
		when(llmClient.generate(any(LlmRequest.class)))
				.thenReturn(new LlmResponse(responseText, "gemini-3.5-flash", 120, 60, 0, "STOP", 600L));

		AnswerJudge judge = new AnswerJudge(llmClient);
		Optional<JudgeVerdict> verdictOpt = judge.judge(
				"Where are embeddings stored?",
				List.of("Embeddings are stored in Qdrant", "Qdrant collection is gitcontext_chunks"),
				"Embeddings are stored in Qdrant and PostgreSQL [1].",
				"[1] Qdrant.java: gitcontext_chunks"
		);

		assertThat(verdictOpt).isPresent();
		JudgeVerdict verdict = verdictOpt.get();
		assertThat(verdict.factsSupported()).containsExactly(true, true);
		assertThat(verdict.grounded()).isFalse();
		assertThat(verdict.unsupportedClaims()).containsExactly("GitContext uses PostgreSQL for vector storage");
	}

	@Test
	@DisplayName("Returns empty Optional when JSON is malformed without throwing")
	void testInvalidJson() {
		LlmClient llmClient = mock(LlmClient.class);
		String invalidJson = "{ facts: [broken json, not formatted correctly";
		when(llmClient.generate(any(LlmRequest.class)))
				.thenReturn(new LlmResponse(invalidJson, "gemini-3.5-flash", 80, 20, 0, "STOP", 300L));

		AnswerJudge judge = new AnswerJudge(llmClient);
		Optional<JudgeVerdict> verdictOpt = judge.judge(
				"Question",
				List.of("Fact 1"),
				"Answer",
				"Source"
		);

		assertThat(verdictOpt).isEmpty();
	}

	@Test
	@DisplayName("Returns empty Optional when fact count does not match expected fact count")
	void testWrongFactCount() {
		LlmClient llmClient = mock(LlmClient.class);
		// Expected facts: 3, but JSON only provides 2
		String json = """
				{
				  "facts": [
				    {"index": 1, "supported": true},
				    {"index": 2, "supported": true}
				  ],
				  "grounded": true,
				  "unsupportedClaims": []
				}
				""";
		when(llmClient.generate(any(LlmRequest.class)))
				.thenReturn(new LlmResponse(json, "gemini-3.5-flash", 100, 40, 0, "STOP", 400L));

		AnswerJudge judge = new AnswerJudge(llmClient);
		Optional<JudgeVerdict> verdictOpt = judge.judge(
				"Question",
				List.of("Fact 1", "Fact 2", "Fact 3"),
				"Answer",
				"Source"
		);

		assertThat(verdictOpt).isEmpty();
	}

	@Test
	@DisplayName("Returns empty Optional when LLM client throws exception")
	void testLlmClientExceptionHandledGracefully() {
		LlmClient llmClient = mock(LlmClient.class);
		when(llmClient.generate(any(LlmRequest.class)))
				.thenThrow(new LlmUnavailableException("Rate limit exceeded"));

		AnswerJudge judge = new AnswerJudge(llmClient);
		Optional<JudgeVerdict> verdictOpt = judge.judge(
				"Question",
				List.of("Fact 1"),
				"Answer",
				"Source"
		);

		assertThat(verdictOpt).isEmpty();
	}

	@Test
	@DisplayName("Configured judge model in properties is passed to LlmRequest")
	void testJudgeModelOverridePassedThrough() {
		LlmClient llmClient = mock(LlmClient.class);
		String json = """
				{
				  "facts": [{"index": 1, "supported": true}],
				  "grounded": true,
				  "unsupportedClaims": []
				}
				""";
		when(llmClient.generate(any(LlmRequest.class)))
				.thenReturn(new LlmResponse(json, "gemini-3.8-flash", 100, 50, 0, "STOP", 500L));

		io.github.tarunipriya.gitcontext.config.GitContextProperties.AnswerEvalProperties answerProps =
				new io.github.tarunipriya.gitcontext.config.GitContextProperties.AnswerEvalProperties(
						"eval/datasets/test.jsonl", "repo", false, "eval/results", 0L, true, 6000, 0, 3, true, "gemini-3.8-flash", true
				);
		io.github.tarunipriya.gitcontext.config.GitContextProperties properties =
				new io.github.tarunipriya.gitcontext.config.GitContextProperties(
						null, null, "http://localhost:6333", null, null, null, null, null, null, null, null, null, null, null, null, answerProps
				);

		AnswerJudge judge = new AnswerJudge(llmClient, properties);
		judge.judge("Question", List.of("Fact 1"), "Answer", "Source");

		org.mockito.ArgumentCaptor<LlmRequest> captor = org.mockito.ArgumentCaptor.forClass(LlmRequest.class);
		verify(llmClient).generate(captor.capture());
		assertThat(captor.getValue().model()).isEqualTo("gemini-3.8-flash");
	}

	@Test
	@DisplayName("Default judgeModel (empty string) defaults to gitcontext.llm.model")
	void testDefaultJudgeModelResolvesToLlmModel() {
		LlmClient llmClient = mock(LlmClient.class);
		String json = """
				{
				  "facts": [{"index": 1, "supported": true}],
				  "grounded": true,
				  "unsupportedClaims": []
				}
				""";
		when(llmClient.generate(any(LlmRequest.class)))
				.thenReturn(new LlmResponse(json, "gemini-3.5-flash", 100, 50, 0, "STOP", 500L));

		io.github.tarunipriya.gitcontext.config.GitContextProperties properties =
				new io.github.tarunipriya.gitcontext.config.GitContextProperties(
						null, null, "http://localhost:6333", null, null, null, null, null, null, null, null, null, null, null, null, null
				);

		AnswerJudge judge = new AnswerJudge(llmClient, properties);
		judge.judge("Question", List.of("Fact 1"), "Answer", "Source");

		org.mockito.ArgumentCaptor<LlmRequest> captor = org.mockito.ArgumentCaptor.forClass(LlmRequest.class);
		verify(llmClient).generate(captor.capture());
		assertThat(captor.getValue().model()).isEqualTo("gemini-3.5-flash");
	}

	@Test
	@DisplayName("Explicit judge model override in judge method overrides properties")
	void testExplicitJudgeModelOverride() {
		LlmClient llmClient = mock(LlmClient.class);
		String json = """
				{
				  "facts": [{"index": 1, "supported": true}],
				  "grounded": true,
				  "unsupportedClaims": []
				}
				""";
		when(llmClient.generate(any(LlmRequest.class)))
				.thenReturn(new LlmResponse(json, "gemini-1.5-pro", 100, 50, 0, "STOP", 500L));

		AnswerJudge judge = new AnswerJudge(llmClient);
		judge.judge("Question", List.of("Fact 1"), "Answer", "Source", "gemini-1.5-pro");

		org.mockito.ArgumentCaptor<LlmRequest> captor = org.mockito.ArgumentCaptor.forClass(LlmRequest.class);
		verify(llmClient).generate(captor.capture());
		assertThat(captor.getValue().model()).isEqualTo("gemini-1.5-pro");
	}
}
