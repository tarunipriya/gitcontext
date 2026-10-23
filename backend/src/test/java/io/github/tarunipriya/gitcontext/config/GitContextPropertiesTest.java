package io.github.tarunipriya.gitcontext.config;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GitContextPropertiesTest {

	@Configuration(proxyBeanMethods = false)
	@EnableConfigurationProperties(GitContextProperties.class)
	static class TestConfiguration {
	}

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
			.withUserConfiguration(TestConfiguration.class);

	@Test
	@DisplayName("Default properties bind with expected defaults and normalised absolute path")
	void testDefaultProperties() {
		runner.run(context -> {
			assertThat(context).hasNotFailed();
			assertThat(context).hasSingleBean(GitContextProperties.class);

			GitContextProperties props = context.getBean(GitContextProperties.class);
			assertThat(props.qdrantUrl()).isEqualTo("http://localhost:6333");
			assertThat(props.reposDir()).isAbsolute();
			assertThat(props.reposDir().toString().replace('\\', '/')).endsWith("data/repos");
			assertThat(props.githubToken()).isNull();
			assertThat(props.llmApiKey()).isNull();
			assertThat(props.hasGithubToken()).isFalse();
			assertThat(props.hasLlmApiKey()).isFalse();

			// Ingestion defaults
			GitContextProperties.IngestionProperties ingestion = props.ingestion();
			assertThat(ingestion).isNotNull();
			assertThat(ingestion.maxFileSizeKb()).isEqualTo(512);
			assertThat(ingestion.maxCommits()).isEqualTo(500);
			assertThat(ingestion.maxDiffChars()).isEqualTo(4000);
			assertThat(ingestion.excludeDirs()).contains(".git", "node_modules", "target", "build", "dist", ".idea");
			assertThat(ingestion.excludePathPrefixes()).containsExactly("eval/");
			assertThat(ingestion.docExtensions()).contains("md", "rst", "txt", "adoc");
			assertThat(ingestion.codeExtensions()).contains("java", "py", "ts", "go", "rs", "cpp");
			assertThat(ingestion.excludeLockFiles()).contains("package-lock.json", "yarn.lock", "poetry.lock");
			assertThat(ingestion.excludeMinifiedPatterns()).contains("*.min.js", "*.min.css");

			// GitHub defaults
			GitContextProperties.GitHubProperties github = props.github();
			assertThat(github).isNotNull();
			assertThat(github.apiBaseUrl()).isEqualTo("https://api.github.com");
			assertThat(github.maxPullRequests()).isEqualTo(200);
			assertThat(github.maxIssues()).isEqualTo(200);
			assertThat(github.maxCommentsPerItem()).isEqualTo(50);
			assertThat(github.incidentLabels()).containsExactly("incident", "bug", "postmortem", "outage");
			assertThat(github.rateLimitWarnThreshold()).isEqualTo(50);

			// Chunking defaults
			GitContextProperties.ChunkingProperties chunking = props.chunking();
			assertThat(chunking).isNotNull();
			assertThat(chunking.maxChunkTokens()).isEqualTo(512);
			assertThat(chunking.minChunkTokens()).isEqualTo(40);
			assertThat(chunking.overlapLines()).isEqualTo(5);
			assertThat(chunking.fallbackWindowLines()).isEqualTo(60);

			// Embedding defaults
			GitContextProperties.EmbeddingProperties embedding = props.embedding();
			assertThat(embedding).isNotNull();
			assertThat(embedding.batchSize()).isEqualTo(32);
			assertThat(embedding.queryPrefix()).isEqualTo("Represent this sentence for searching relevant passages: ");

			// Qdrant defaults
			GitContextProperties.QdrantProperties qdrant = props.qdrant();
			assertThat(qdrant).isNotNull();
			assertThat(qdrant.url()).isEqualTo("http://localhost:6333");
			assertThat(qdrant.collection()).isEqualTo("gitcontext_chunks");
			assertThat(qdrant.upsertBatchSize()).isEqualTo(128);
			assertThat(qdrant.timeoutSeconds()).isEqualTo(30);

			// Keyword defaults
			GitContextProperties.KeywordProperties keyword = props.keyword();
			assertThat(keyword).isNotNull();
			assertThat(keyword.indexDir()).isAbsolute();
			assertThat(keyword.indexDir().toString().replace('\\', '/')).endsWith("data/lucene");
			assertThat(keyword.symbolBoost()).isEqualTo(3.0);
			assertThat(keyword.parentSymbolBoost()).isEqualTo(1.5);
			assertThat(keyword.pathBoost()).isEqualTo(1.0);

			// Hybrid defaults
			GitContextProperties.HybridProperties hybrid = props.hybrid();
			assertThat(hybrid).isNotNull();
			assertThat(hybrid.candidates()).isEqualTo(50);
			assertThat(hybrid.rrfK()).isEqualTo(60);
			assertThat(hybrid.defaultMode()).isEqualTo(io.github.tarunipriya.gitcontext.retrieval.SearchMode.HYBRID);
			assertThat(hybrid.vectorWeight()).isEqualTo(1.0);
			assertThat(hybrid.keywordWeight()).isEqualTo(1.0);

			// Rerank defaults
			GitContextProperties.RerankProperties rerank = props.rerank();
			assertThat(rerank).isNotNull();
			assertThat(rerank.enabled()).isFalse();
			assertThat(rerank.modelName()).isEqualTo("ms-marco-MiniLM-L-6-v2");
			assertThat(rerank.modelUrl()).isEqualTo("https://huggingface.co/Xenova/ms-marco-MiniLM-L-6-v2/resolve/main/onnx/model_quantized.onnx");
			assertThat(rerank.tokenizerUrl()).isEqualTo("https://huggingface.co/Xenova/ms-marco-MiniLM-L-6-v2/resolve/main/tokenizer.json");
			assertThat(rerank.candidates()).isEqualTo(30);
			assertThat(rerank.maxTextChars()).isEqualTo(1000);

			// LLM defaults
			GitContextProperties.LlmProperties llm = props.llm();
			assertThat(llm).isNotNull();
			assertThat(llm.provider()).isEqualTo("gemini");
			assertThat(llm.model()).isEqualTo("gemini-3.5-flash");
			assertThat(llm.fallbackModel()).isEqualTo("gemini-3.8-flash");
			assertThat(llm.baseUrl()).isEqualTo("https://generativelanguage.googleapis.com/v1beta");
			assertThat(llm.temperature()).isEqualTo(0.2);
			assertThat(llm.maxOutputTokens()).isEqualTo(2048);
			assertThat(llm.timeoutSeconds()).isEqualTo(60);
			assertThat(llm.maxRetries()).isEqualTo(2);
			assertThat(llm.thinkingLevel()).isEqualTo("low");

			// Demo defaults
			GitContextProperties.DemoProperties demo = props.demo();
			assertThat(demo).isNotNull();
			assertThat(demo.repo()).isEqualTo("tarunipriya/gitcontext");
			assertThat(demo.forceReingest()).isFalse();

			// Eval defaults
			GitContextProperties.EvalProperties eval = props.eval();
			assertThat(eval).isNotNull();
			assertThat(eval.dataset()).isEqualTo("eval/datasets/gitcontext.jsonl");
			assertThat(eval.repo()).isEqualTo("tarunipriya/gitcontext");
			assertThat(eval.ks()).containsExactly(1, 3, 5, 10);
			assertThat(eval.maxK()).isEqualTo(10);
			assertThat(eval.variants()).containsExactly("VECTOR", "KEYWORD", "HYBRID", "HYBRID_RERANK");
			assertThat(eval.excludeTests()).isFalse();
			assertThat(eval.reingest()).isTrue();
			assertThat(eval.warmupQueries()).isEqualTo(1);
			assertThat(eval.outputDir()).isEqualTo("eval/results");

			// AnswerEval defaults
			GitContextProperties.AnswerEvalProperties answerEval = props.answerEval();
			assertThat(answerEval).isNotNull();
			assertThat(answerEval.dataset()).isEqualTo("eval/datasets/gitcontext.jsonl");
			assertThat(answerEval.repo()).isEqualTo("tarunipriya/gitcontext");
			assertThat(answerEval.reingest()).isTrue();
			assertThat(answerEval.outputDir()).isEqualTo("eval/results");
			assertThat(answerEval.requestDelayMs()).isEqualTo(13000L);
			assertThat(answerEval.judgeEnabled()).isTrue();
			assertThat(answerEval.judgeMaxSourceChars()).isEqualTo(6000);
			assertThat(answerEval.maxCases()).isEqualTo(0);
			assertThat(answerEval.maxConsecutiveFailures()).isEqualTo(3);
			assertThat(answerEval.resume()).isTrue();
			assertThat(answerEval.judgeModel()).isEmpty();
			assertThat(answerEval.disableFallback()).isTrue();
		});
	}

	@Test
	@DisplayName("Custom property overrides bind successfully")
	void testCustomProperties() {
		runner.withPropertyValues(
				"gitcontext.github-token=ghp_testToken12345",
				"gitcontext.repos-dir=./custom/repos",
				"gitcontext.qdrant-url=http://qdrant.internal:6333",
				"gitcontext.llm-api-key=sk_testKey67890",
				"gitcontext.ingestion.max-file-size-kb=1024",
				"gitcontext.ingestion.max-commits=100",
				"gitcontext.ingestion.max-diff-chars=2000",
				"gitcontext.ingestion.exclude-path-prefixes=eval/,benchmarks/",
				"gitcontext.github.api-base-url=https://github-enterprise.internal/api/v3",
				"gitcontext.github.max-pull-requests=50",
				"gitcontext.github.max-issues=75",
				"gitcontext.github.max-comments-per-item=20",
				"gitcontext.github.incident-labels=sev-1,incident,critical",
				"gitcontext.github.rate-limit-warn-threshold=100",
				"gitcontext.chunking.max-chunk-tokens=256",
				"gitcontext.chunking.min-chunk-tokens=20",
				"gitcontext.chunking.overlap-lines=3",
				"gitcontext.chunking.fallback-window-lines=40",
				"gitcontext.embedding.batch-size=16",
				"gitcontext.embedding.query-prefix=Represent query:",
				"gitcontext.qdrant.collection=custom_chunks",
				"gitcontext.qdrant.upsert-batch-size=64",
				"gitcontext.qdrant.timeout-seconds=45",
				"gitcontext.keyword.index-dir=./custom/lucene",
				"gitcontext.keyword.symbol-boost=4.5",
				"gitcontext.keyword.parent-symbol-boost=2.5",
				"gitcontext.keyword.path-boost=1.5",
				"gitcontext.hybrid.candidates=30",
				"gitcontext.hybrid.rrf-k=40",
				"gitcontext.hybrid.default-mode=KEYWORD",
				"gitcontext.hybrid.vector-weight=0.8",
				"gitcontext.hybrid.keyword-weight=0.4",
				"gitcontext.rerank.enabled=true",
				"gitcontext.rerank.model-name=custom-reranker",
				"gitcontext.rerank.model-url=https://custom/model.onnx",
				"gitcontext.rerank.tokenizer-url=https://custom/tokenizer.json",
				"gitcontext.rerank.candidates=15",
				"gitcontext.rerank.max-text-chars=1500",
				"gitcontext.llm.provider=gemini-custom",
				"gitcontext.llm.model=gemini-1.5-pro",
				"gitcontext.llm.fallback-model=gemini-1.5-flash",
				"gitcontext.llm.base-url=https://custom.googleapis.com",
				"gitcontext.llm.temperature=0.7",
				"gitcontext.llm.max-output-tokens=2048",
				"gitcontext.llm.timeout-seconds=45",
				"gitcontext.llm.max-retries=3",
				"gitcontext.llm.thinking-level=medium",
				"gitcontext.demo.repo=custom/demo-repo",
				"gitcontext.demo.force-reingest=true",
				"gitcontext.eval.dataset=eval/datasets/smoke.jsonl",
				"gitcontext.eval.repo=custom/test-repo",
				"gitcontext.eval.ks=1,5",
				"gitcontext.eval.variants=HYBRID,HYBRID_RERANK",
				"gitcontext.eval.exclude-tests=true",
				"gitcontext.eval.reingest=false",
				"gitcontext.eval.warmup-queries=0",
				"gitcontext.eval.output-dir=eval/custom-output",
				"gitcontext.answer-eval.dataset=eval/datasets/custom.jsonl",
				"gitcontext.answer-eval.repo=custom/answer-repo",
				"gitcontext.answer-eval.reingest=false",
				"gitcontext.answer-eval.output-dir=eval/custom-answers",
				"gitcontext.answer-eval.request-delay-ms=2500",
				"gitcontext.answer-eval.judge-enabled=false",
				"gitcontext.answer-eval.judge-max-source-chars=4000",
				"gitcontext.answer-eval.max-cases=5",
				"gitcontext.answer-eval.max-consecutive-failures=5",
				"gitcontext.answer-eval.resume=false",
				"gitcontext.answer-eval.judge-model=gemini-3.8-flash",
				"gitcontext.answer-eval.disable-fallback=false"
		).run(context -> {
			assertThat(context).hasNotFailed();
			GitContextProperties props = context.getBean(GitContextProperties.class);

			assertThat(props.githubToken()).isEqualTo("ghp_testToken12345");
			assertThat(props.qdrantUrl()).isEqualTo("http://qdrant.internal:6333");
			assertThat(props.llmApiKey()).isEqualTo("sk_testKey67890");
			assertThat(props.reposDir().toString().replace('\\', '/')).endsWith("custom/repos");
			assertThat(props.hasGithubToken()).isTrue();
			assertThat(props.hasLlmApiKey()).isTrue();

			assertThat(props.ingestion().maxFileSizeKb()).isEqualTo(1024);
			assertThat(props.ingestion().maxCommits()).isEqualTo(100);
			assertThat(props.ingestion().maxDiffChars()).isEqualTo(2000);
			assertThat(props.ingestion().excludePathPrefixes()).containsExactly("eval/", "benchmarks/");

			assertThat(props.github().apiBaseUrl()).isEqualTo("https://github-enterprise.internal/api/v3");
			assertThat(props.github().maxPullRequests()).isEqualTo(50);
			assertThat(props.github().maxIssues()).isEqualTo(75);
			assertThat(props.github().maxCommentsPerItem()).isEqualTo(20);
			assertThat(props.github().incidentLabels()).containsExactly("sev-1", "incident", "critical");
			assertThat(props.github().rateLimitWarnThreshold()).isEqualTo(100);

			assertThat(props.chunking().maxChunkTokens()).isEqualTo(256);
			assertThat(props.chunking().minChunkTokens()).isEqualTo(20);
			assertThat(props.chunking().overlapLines()).isEqualTo(3);
			assertThat(props.chunking().fallbackWindowLines()).isEqualTo(40);

			assertThat(props.embedding().batchSize()).isEqualTo(16);
			assertThat(props.embedding().queryPrefix()).isEqualTo("Represent query:");

			assertThat(props.qdrant().url()).isEqualTo("http://qdrant.internal:6333");
			assertThat(props.qdrant().collection()).isEqualTo("custom_chunks");
			assertThat(props.qdrant().upsertBatchSize()).isEqualTo(64);
			assertThat(props.qdrant().timeoutSeconds()).isEqualTo(45);

			assertThat(props.keyword().indexDir().toString().replace('\\', '/')).endsWith("custom/lucene");
			assertThat(props.keyword().symbolBoost()).isEqualTo(4.5);
			assertThat(props.keyword().parentSymbolBoost()).isEqualTo(2.5);
			assertThat(props.keyword().pathBoost()).isEqualTo(1.5);

			assertThat(props.hybrid().candidates()).isEqualTo(30);
			assertThat(props.hybrid().rrfK()).isEqualTo(40);
			assertThat(props.hybrid().defaultMode()).isEqualTo(io.github.tarunipriya.gitcontext.retrieval.SearchMode.KEYWORD);
			assertThat(props.hybrid().vectorWeight()).isEqualTo(0.8);
			assertThat(props.hybrid().keywordWeight()).isEqualTo(0.4);

			assertThat(props.rerank().enabled()).isTrue();
			assertThat(props.rerank().modelName()).isEqualTo("custom-reranker");
			assertThat(props.rerank().modelUrl()).isEqualTo("https://custom/model.onnx");
			assertThat(props.rerank().tokenizerUrl()).isEqualTo("https://custom/tokenizer.json");
			assertThat(props.rerank().candidates()).isEqualTo(15);
			assertThat(props.rerank().maxTextChars()).isEqualTo(1500);

			assertThat(props.llm().provider()).isEqualTo("gemini-custom");
			assertThat(props.llm().model()).isEqualTo("gemini-1.5-pro");
			assertThat(props.llm().fallbackModel()).isEqualTo("gemini-1.5-flash");
			assertThat(props.llm().baseUrl()).isEqualTo("https://custom.googleapis.com");
			assertThat(props.llm().temperature()).isEqualTo(0.7);
			assertThat(props.llm().maxOutputTokens()).isEqualTo(2048);
			assertThat(props.llm().timeoutSeconds()).isEqualTo(45);
			assertThat(props.llm().maxRetries()).isEqualTo(3);
			assertThat(props.llm().thinkingLevel()).isEqualTo("medium");

			assertThat(props.demo().repo()).isEqualTo("custom/demo-repo");
			assertThat(props.demo().forceReingest()).isTrue();

			assertThat(props.eval().dataset()).isEqualTo("eval/datasets/smoke.jsonl");
			assertThat(props.eval().repo()).isEqualTo("custom/test-repo");
			assertThat(props.eval().ks()).containsExactly(1, 5);
			assertThat(props.eval().maxK()).isEqualTo(5);
			assertThat(props.eval().variants()).containsExactly("HYBRID", "HYBRID_RERANK");
			assertThat(props.eval().excludeTests()).isTrue();
			assertThat(props.eval().reingest()).isFalse();
			assertThat(props.eval().warmupQueries()).isEqualTo(0);
			assertThat(props.eval().outputDir()).isEqualTo("eval/custom-output");

			assertThat(props.answerEval().dataset()).isEqualTo("eval/datasets/custom.jsonl");
			assertThat(props.answerEval().repo()).isEqualTo("custom/answer-repo");
			assertThat(props.answerEval().reingest()).isFalse();
			assertThat(props.answerEval().outputDir()).isEqualTo("eval/custom-answers");
			assertThat(props.answerEval().requestDelayMs()).isEqualTo(2500L);
			assertThat(props.answerEval().judgeEnabled()).isFalse();
			assertThat(props.answerEval().judgeMaxSourceChars()).isEqualTo(4000);
			assertThat(props.answerEval().maxCases()).isEqualTo(5);
			assertThat(props.answerEval().maxConsecutiveFailures()).isEqualTo(5);
			assertThat(props.answerEval().resume()).isFalse();
			assertThat(props.answerEval().judgeModel()).isEqualTo("gemini-3.8-flash");
			assertThat(props.answerEval().disableFallback()).isFalse();
		});
	}

	@Test
	@DisplayName("Demo properties bind default and custom values")
	void testDemoPropertiesBinding() {
		runner.run(context -> {
			assertThat(context).hasNotFailed();
			GitContextProperties props = context.getBean(GitContextProperties.class);
			assertThat(props.demo()).isNotNull();
			assertThat(props.demo().repo()).isEqualTo("tarunipriya/gitcontext");
			assertThat(props.demo().forceReingest()).isFalse();
		});

		runner.withPropertyValues(
				"gitcontext.demo.repo=test/custom-demo-repo",
				"gitcontext.demo.force-reingest=true"
		).run(context -> {
			assertThat(context).hasNotFailed();
			GitContextProperties props = context.getBean(GitContextProperties.class);
			assertThat(props.demo()).isNotNull();
			assertThat(props.demo().repo()).isEqualTo("test/custom-demo-repo");
			assertThat(props.demo().forceReingest()).isTrue();
		});
	}

	@Test
	@DisplayName("Eval properties bind default and custom values")
	void testEvalPropertiesBinding() {
		runner.run(context -> {
			assertThat(context).hasNotFailed();
			GitContextProperties props = context.getBean(GitContextProperties.class);
			assertThat(props.eval()).isNotNull();
			assertThat(props.eval().dataset()).isEqualTo("eval/datasets/gitcontext.jsonl");
			assertThat(props.eval().repo()).isEqualTo("tarunipriya/gitcontext");
			assertThat(props.eval().ks()).containsExactly(1, 3, 5, 10);
			assertThat(props.eval().maxK()).isEqualTo(10);
			assertThat(props.eval().variants()).containsExactly("VECTOR", "KEYWORD", "HYBRID", "HYBRID_RERANK");
			assertThat(props.eval().excludeTests()).isFalse();
			assertThat(props.eval().reingest()).isTrue();
			assertThat(props.eval().warmupQueries()).isEqualTo(1);
			assertThat(props.eval().outputDir()).isEqualTo("eval/results");
		});

		runner.withPropertyValues(
				"gitcontext.eval.dataset=eval/datasets/smoke.jsonl",
				"gitcontext.eval.repo=test/custom-eval-repo",
				"gitcontext.eval.ks=3,7",
				"gitcontext.eval.variants=VECTOR,HYBRID",
				"gitcontext.eval.exclude-tests=true",
				"gitcontext.eval.reingest=false",
				"gitcontext.eval.warmup-queries=2",
				"gitcontext.eval.output-dir=eval/out"
		).run(context -> {
			assertThat(context).hasNotFailed();
			GitContextProperties props = context.getBean(GitContextProperties.class);
			assertThat(props.eval()).isNotNull();
			assertThat(props.eval().dataset()).isEqualTo("eval/datasets/smoke.jsonl");
			assertThat(props.eval().repo()).isEqualTo("test/custom-eval-repo");
			assertThat(props.eval().ks()).containsExactly(3, 7);
			assertThat(props.eval().maxK()).isEqualTo(7);
			assertThat(props.eval().variants()).containsExactly("VECTOR", "HYBRID");
			assertThat(props.eval().excludeTests()).isTrue();
			assertThat(props.eval().reingest()).isFalse();
			assertThat(props.eval().warmupQueries()).isEqualTo(2);
			assertThat(props.eval().outputDir()).isEqualTo("eval/out");
		});

		// Comma-separated list containing ':' and '=' binds cleanly to List<String>
		runner.withPropertyValues(
				"gitcontext.eval.variants=VECTOR,KEYWORD,HYBRID,HYBRID:kw=0.75,HYBRID:kw=0.5,HYBRID:kw=0.25,HYBRID_RERANK,HYBRID_RERANK:kw=0.5"
		).run(context -> {
			assertThat(context).hasNotFailed();
			GitContextProperties props = context.getBean(GitContextProperties.class);
			assertThat(props.eval().variants()).containsExactly(
					"VECTOR",
					"KEYWORD",
					"HYBRID",
					"HYBRID:kw=0.75",
					"HYBRID:kw=0.5",
					"HYBRID:kw=0.25",
					"HYBRID_RERANK",
					"HYBRID_RERANK:kw=0.5"
			);
		});
	}

	@Test
	@DisplayName("AnswerEval properties bind default and custom values, validating non-negative delay")
	void testAnswerEvalPropertiesBinding() {
		runner.run(context -> {
			assertThat(context).hasNotFailed();
			GitContextProperties props = context.getBean(GitContextProperties.class);
			assertThat(props.answerEval()).isNotNull();
			assertThat(props.answerEval().dataset()).isEqualTo("eval/datasets/gitcontext.jsonl");
			assertThat(props.answerEval().repo()).isEqualTo("tarunipriya/gitcontext");
			assertThat(props.answerEval().reingest()).isTrue();
			assertThat(props.answerEval().outputDir()).isEqualTo("eval/results");
			assertThat(props.answerEval().requestDelayMs()).isEqualTo(13000L);
			assertThat(props.answerEval().judgeEnabled()).isTrue();
			assertThat(props.answerEval().judgeMaxSourceChars()).isEqualTo(6000);
			assertThat(props.answerEval().maxCases()).isEqualTo(0);
		});

		runner.withPropertyValues(
				"gitcontext.answer-eval.dataset=eval/datasets/smoke.jsonl",
				"gitcontext.answer-eval.repo=test/custom-repo",
				"gitcontext.answer-eval.reingest=false",
				"gitcontext.answer-eval.output-dir=eval/custom-dir",
				"gitcontext.answer-eval.request-delay-ms=1500",
				"gitcontext.answer-eval.judge-enabled=false",
				"gitcontext.answer-eval.judge-max-source-chars=3000",
				"gitcontext.answer-eval.max-cases=3"
		).run(context -> {
			assertThat(context).hasNotFailed();
			GitContextProperties props = context.getBean(GitContextProperties.class);
			assertThat(props.answerEval()).isNotNull();
			assertThat(props.answerEval().dataset()).isEqualTo("eval/datasets/smoke.jsonl");
			assertThat(props.answerEval().repo()).isEqualTo("test/custom-repo");
			assertThat(props.answerEval().reingest()).isFalse();
			assertThat(props.answerEval().outputDir()).isEqualTo("eval/custom-dir");
			assertThat(props.answerEval().requestDelayMs()).isEqualTo(1500L);
			assertThat(props.answerEval().judgeEnabled()).isFalse();
			assertThat(props.answerEval().judgeMaxSourceChars()).isEqualTo(3000);
			assertThat(props.answerEval().maxCases()).isEqualTo(3);
		});

		// Negative requestDelayMs throws IllegalArgumentException
		assertThatThrownBy(() -> new GitContextProperties.AnswerEvalProperties(
				"eval/datasets/gitcontext.jsonl",
				"tarunipriya/gitcontext",
				true,
				"eval/results",
				-1L,
				true,
				6000,
				0,
				3,
				true,
				"",
				true
		))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("requestDelayMs must be non-negative");
	}

	@Test
	@DisplayName("Blank qdrantUrl fails validation during binding")
	void testBlankQdrantUrlFailsValidation() {
		runner.withPropertyValues("gitcontext.qdrant-url=")
				.run(context -> assertThat(context).hasFailed());

		runner.withPropertyValues("gitcontext.qdrant-url=   ")
				.run(context -> assertThat(context).hasFailed());
	}

	@Test
	@DisplayName("toString() masks secret tokens and handles unconfigured tokens")
	void testToStringMasksSecrets() {
		GitContextProperties withSecrets = new GitContextProperties(
				"ghp_superSecretToken",
				Path.of("./data/repos"),
				"http://localhost:6333",
				"sk_superSecretLlmKey",
				null,
				null,
				null
		);

		String stringOutput = withSecrets.toString();
		assertThat(stringOutput).doesNotContain("ghp_superSecretToken");
		assertThat(stringOutput).doesNotContain("sk_superSecretLlmKey");
		assertThat(stringOutput).contains("githubToken=****");
		assertThat(stringOutput).contains("llmApiKey=****");

		GitContextProperties withoutSecrets = new GitContextProperties(
				null,
				Path.of("./data/repos"),
				"http://localhost:6333",
				"",
				null,
				null,
				null
		);

		String unconfiguredOutput = withoutSecrets.toString();
		assertThat(unconfiguredOutput).contains("githubToken=<not set>");
		assertThat(unconfiguredOutput).contains("llmApiKey=<not set>");
	}

	@Test
	@DisplayName("resolveToRepoRoot('eval/results') stays inside repo root and ends with eval/results")
	void testResolveToRepoRootEvalResults() {
		Path repoRoot = GitContextProperties.resolveToRepoRoot(Path.of(""));
		Path resolved = GitContextProperties.resolveToRepoRoot(Path.of("eval/results"));

		assertThat(resolved).startsWith(repoRoot);
		assertThat(resolved.toString().replace('\\', '/')).endsWith("eval/results");
		assertThat(resolved.toString()).doesNotContain("..");
	}

	@Test
	@DisplayName("EvalProperties validates cutoff ranks ks: non-empty, every k > 0, duplicates removed")
	void testEvalPropertiesKsValidation() {
		// Empty ks throws
		assertThatThrownBy(() -> new GitContextProperties.EvalProperties(
				"eval/datasets/gitcontext.jsonl",
				"tarunipriya/gitcontext",
				List.of(),
				List.of("VECTOR"),
				false,
				true,
				1,
				"eval/results"
		))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Cutoff ranks ks must not be empty");

		// Non-positive k throws
		assertThatThrownBy(() -> new GitContextProperties.EvalProperties(
				"eval/datasets/gitcontext.jsonl",
				"tarunipriya/gitcontext",
				List.of(1, 0, 5),
				List.of("VECTOR"),
				false,
				true,
				1,
				"eval/results"
		))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Cutoff rank k must be greater than 0");

		assertThatThrownBy(() -> new GitContextProperties.EvalProperties(
				"eval/datasets/gitcontext.jsonl",
				"tarunipriya/gitcontext",
				List.of(-2),
				List.of("VECTOR"),
				false,
				true,
				1,
				"eval/results"
		))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Cutoff rank k must be greater than 0");

		// Duplicates are removed
		GitContextProperties.EvalProperties props = new GitContextProperties.EvalProperties(
				"eval/datasets/gitcontext.jsonl",
				"tarunipriya/gitcontext",
				List.of(5, 1, 5, 10, 1),
				List.of("VECTOR"),
				false,
				true,
				1,
				"eval/results"
		);
		assertThat(props.ks()).containsExactly(5, 1, 10);
		assertThat(props.maxK()).isEqualTo(10);
	}

	@Test
	@DisplayName("HybridProperties validates vector and keyword weights: finite, >= 0, not both 0")
	void testHybridPropertiesValidation() {
		assertThatThrownBy(() -> new GitContextProperties.HybridProperties(
				50, 60, io.github.tarunipriya.gitcontext.retrieval.SearchMode.HYBRID, -0.1, 1.0))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("vectorWeight must be finite and >= 0");

		assertThatThrownBy(() -> new GitContextProperties.HybridProperties(
				50, 60, io.github.tarunipriya.gitcontext.retrieval.SearchMode.HYBRID, 1.0, -0.5))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("keywordWeight must be finite and >= 0");

		assertThatThrownBy(() -> new GitContextProperties.HybridProperties(
				50, 60, io.github.tarunipriya.gitcontext.retrieval.SearchMode.HYBRID, Double.NaN, 1.0))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("vectorWeight must be finite and >= 0");

		assertThatThrownBy(() -> new GitContextProperties.HybridProperties(
				50, 60, io.github.tarunipriya.gitcontext.retrieval.SearchMode.HYBRID, 0.0, 0.0))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("vectorWeight and keywordWeight cannot both be 0");
	}
}
