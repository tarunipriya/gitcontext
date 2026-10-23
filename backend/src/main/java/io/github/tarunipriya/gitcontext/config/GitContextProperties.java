package io.github.tarunipriya.gitcontext.config;

import io.github.tarunipriya.gitcontext.retrieval.SearchMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Parameter;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Strongly typed configuration properties for the GitContext application under the {@code gitcontext} namespace.
 *
 * <p>Repository root resolution for {@code reposDir}:
 * When {@code reposDir} is specified as a relative path (e.g. the default {@code ./data/repos}), resolving it against
 * the JVM's current working directory directly would produce different locations depending on whether the process is launched
 * from the repository root ({@code ./data/repos}) or from the {@code backend/} subdirectory ({@code backend/data/repos}).
 * To ensure consistent, cross-platform behavior, the compact constructor dynamically traverses parent directories starting
 * from the working directory until it locates the repository root marker (e.g. {@code .git} directory or {@code AGENTS.md}
 * alongside {@code backend/}), and normalises the path relative to that root. If {@code reposDir} is already absolute, it
 * is normalised as-is.
 *
 * @param githubToken personal access token for GitHub REST API authentication (optional)
 * @param reposDir local directory path where ingested repositories are cloned (defaults to {@code ./data/repos} relative to repo root)
 * @param qdrantUrl base URL for the Qdrant vector database instance (required, defaults to {@code http://localhost:6333})
 * @param llmApiKey API key for LLM provider access (optional)
 * @param ingestion ingestion configuration for cloning, filtering, and parsing repository content
 * @param github GitHub REST API configuration for fetching pull requests and issues
 * @param chunking chunking configuration for AST and fallback window chunkers
 * @param embedding embedding configuration for local vector model inference
 * @param qdrant Qdrant vector database configuration
 * @param keyword Lucene BM25 keyword index configuration
 * @param hybrid hybrid search and rank fusion configuration
 */
@Validated
@ConfigurationProperties(prefix = "gitcontext")
public record GitContextProperties(
		String githubToken,
		@DefaultValue("./data/repos") Path reposDir,
		@NotBlank @DefaultValue("http://localhost:6333") String qdrantUrl,
		String llmApiKey,
		@Valid @DefaultValue IngestionProperties ingestion,
		@Valid @DefaultValue GitHubProperties github,
		@Valid @DefaultValue ChunkingProperties chunking,
		@Valid @DefaultValue EmbeddingProperties embedding,
		@Valid @DefaultValue QdrantProperties qdrant,
		@Valid @DefaultValue KeywordProperties keyword,
		@Valid @DefaultValue HybridProperties hybrid,
		@Valid @DefaultValue RerankProperties rerank,
		@Valid @DefaultValue LlmProperties llm,
		@Valid @DefaultValue DemoProperties demo,
		@Valid @DefaultValue EvalProperties eval,
		@Valid @DefaultValue AnswerEvalProperties answerEval
) {

	/**
	 * Compact constructor to normalise paths and apply defaults.
	 */
	@ConstructorBinding
	public GitContextProperties {
		reposDir = resolveToRepoRoot(reposDir != null ? reposDir : Path.of("./data/repos"));
		if (qdrantUrl == null) {
			qdrantUrl = "http://localhost:6333";
		}
		if (ingestion == null) {
			ingestion = new IngestionProperties(0, 0, 0, null, null, null, null, null, null);
		}
		if (github == null) {
			github = new GitHubProperties(null, 0, 0, 0, null, -1);
		}
		if (chunking == null) {
			chunking = new ChunkingProperties(512, 40, 5, 60);
		}
		if (embedding == null) {
			embedding = new EmbeddingProperties(32, "Represent this sentence for searching relevant passages: ");
		}
		if (qdrant == null) {
			qdrant = new QdrantProperties(qdrantUrl, "gitcontext_chunks", 128, 30);
		} else {
			String effectiveUrl = (qdrant.url() != null && !qdrant.url().isBlank()) ? qdrant.url() : qdrantUrl;
			String effectiveCollection = (qdrant.collection() != null && !qdrant.collection().isBlank()) ? qdrant.collection() : "gitcontext_chunks";
			int effectiveBatch = qdrant.upsertBatchSize() > 0 ? qdrant.upsertBatchSize() : 128;
			int effectiveTimeout = qdrant.timeoutSeconds() > 0 ? qdrant.timeoutSeconds() : 30;
			qdrant = new QdrantProperties(effectiveUrl, effectiveCollection, effectiveBatch, effectiveTimeout);
		}
		if (keyword == null) {
			keyword = new KeywordProperties(null, 3.0, 1.5, 1.0);
		} else {
			Path effectiveDir = resolveToRepoRoot(keyword.indexDir() != null ? keyword.indexDir() : Path.of("./data/lucene"));
			double sb = keyword.symbolBoost() >= 0 ? keyword.symbolBoost() : 3.0;
			double psb = keyword.parentSymbolBoost() >= 0 ? keyword.parentSymbolBoost() : 1.5;
			double pb = keyword.pathBoost() >= 0 ? keyword.pathBoost() : 1.0;
			keyword = new KeywordProperties(effectiveDir, sb, psb, pb);
		}
		if (hybrid == null) {
			hybrid = new HybridProperties(50, 60, SearchMode.HYBRID, 1.0, 1.0);
		} else {
			int c = hybrid.candidates() > 0 ? hybrid.candidates() : 50;
			int k = hybrid.rrfK() > 0 ? hybrid.rrfK() : 60;
			SearchMode m = hybrid.defaultMode() != null ? hybrid.defaultMode() : SearchMode.HYBRID;
			double vw = hybrid.vectorWeight();
			double kw = hybrid.keywordWeight();
			hybrid = new HybridProperties(c, k, m, vw, kw);
		}
		if (rerank == null) {
			rerank = new RerankProperties(
					false,
					"ms-marco-MiniLM-L-6-v2",
					"https://huggingface.co/Xenova/ms-marco-MiniLM-L-6-v2/resolve/main/onnx/model_quantized.onnx",
					"https://huggingface.co/Xenova/ms-marco-MiniLM-L-6-v2/resolve/main/tokenizer.json",
					30,
					1000
			);
		}
		if (llm == null) {
			llm = new LlmProperties(
					"gemini",
					"gemini-3.5-flash",
					"gemini-3.8-flash",
					"https://generativelanguage.googleapis.com/v1beta",
					0.2,
					2048,
					60,
					2,
					"low"
			);
		} else {
			String p = (llm.provider() != null && !llm.provider().isBlank()) ? llm.provider() : "gemini";
			String m = (llm.model() != null && !llm.model().isBlank()) ? llm.model() : "gemini-3.5-flash";
			String fb = (llm.fallbackModel() != null && !llm.fallbackModel().isBlank()) ? llm.fallbackModel() : "gemini-3.8-flash";
			String u = (llm.baseUrl() != null && !llm.baseUrl().isBlank()) ? llm.baseUrl() : "https://generativelanguage.googleapis.com/v1beta";
			double t = llm.temperature() >= 0.0 ? llm.temperature() : 0.2;
			int mot = llm.maxOutputTokens() > 0 ? llm.maxOutputTokens() : 2048;
			int ts = llm.timeoutSeconds() > 0 ? llm.timeoutSeconds() : 60;
			int mr = llm.maxRetries() >= 0 ? llm.maxRetries() : 2;
			String tl = (llm.thinkingLevel() != null && !llm.thinkingLevel().isBlank()) ? llm.thinkingLevel() : "low";
			llm = new LlmProperties(p, m, fb, u, t, mot, ts, mr, tl);
		}
		if (demo == null) {
			demo = new DemoProperties("tarunipriya/gitcontext", false);
		} else {
			String r = (demo.repo() != null && !demo.repo().isBlank()) ? demo.repo() : "tarunipriya/gitcontext";
			demo = new DemoProperties(r, demo.forceReingest());
		}
		if (eval == null) {
			eval = new EvalProperties(
					"eval/datasets/gitcontext.jsonl",
					"tarunipriya/gitcontext",
					List.of(1, 3, 5, 10),
					List.of("VECTOR", "KEYWORD", "HYBRID", "HYBRID_RERANK"),
					false,
					true,
					1,
					"eval/results"
			);
		} else {
			String d = (eval.dataset() != null && !eval.dataset().isBlank()) ? eval.dataset() : "eval/datasets/gitcontext.jsonl";
			String r = (eval.repo() != null && !eval.repo().isBlank()) ? eval.repo() : "tarunipriya/gitcontext";
			List<Integer> ks = (eval.ks() != null && !eval.ks().isEmpty()) ? eval.ks() : List.of(1, 3, 5, 10);
			List<String> vars = (eval.variants() != null && !eval.variants().isEmpty()) ? List.copyOf(eval.variants()) : List.of("VECTOR", "KEYWORD", "HYBRID", "HYBRID_RERANK");
			boolean ext = eval.excludeTests();
			boolean rei = eval.reingest();
			int w = eval.warmupQueries() >= 0 ? eval.warmupQueries() : 1;
			String out = (eval.outputDir() != null && !eval.outputDir().isBlank()) ? eval.outputDir() : "eval/results";
			eval = new EvalProperties(d, r, ks, vars, ext, rei, w, out);
		}
		if (answerEval == null) {
			answerEval = new AnswerEvalProperties(
					"eval/datasets/gitcontext.jsonl",
					"tarunipriya/gitcontext",
					true,
					"eval/results",
					13000L,
					true,
					6000,
					0,
					3,
					true,
					"",
					true
			);
		} else {
			String d = (answerEval.dataset() != null && !answerEval.dataset().isBlank()) ? answerEval.dataset() : "eval/datasets/gitcontext.jsonl";
			String r = (answerEval.repo() != null && !answerEval.repo().isBlank()) ? answerEval.repo() : "tarunipriya/gitcontext";
			boolean rei = answerEval.reingest();
			String out = (answerEval.outputDir() != null && !answerEval.outputDir().isBlank()) ? answerEval.outputDir() : "eval/results";
			long delay = answerEval.requestDelayMs() >= 0 ? answerEval.requestDelayMs() : 13000L;
			boolean judge = answerEval.judgeEnabled();
			int maxSrc = answerEval.judgeMaxSourceChars() > 0 ? answerEval.judgeMaxSourceChars() : 6000;
			int maxC = answerEval.maxCases() >= 0 ? answerEval.maxCases() : 0;
			int maxFail = answerEval.maxConsecutiveFailures() > 0 ? answerEval.maxConsecutiveFailures() : 3;
			boolean res = answerEval.resume();
			String jm = answerEval.judgeModel() != null ? answerEval.judgeModel() : "";
			boolean df = answerEval.disableFallback();
			answerEval = new AnswerEvalProperties(d, r, rei, out, delay, judge, maxSrc, maxC, maxFail, res, jm, df);
		}
	}

	/**
	 * Overloaded constructor preserving compatibility for callers with all properties except answerEval.
	 */
	public GitContextProperties(
			String githubToken,
			Path reposDir,
			String qdrantUrl,
			String llmApiKey,
			IngestionProperties ingestion,
			GitHubProperties github,
			ChunkingProperties chunking,
			EmbeddingProperties embedding,
			QdrantProperties qdrant,
			KeywordProperties keyword,
			HybridProperties hybrid,
			RerankProperties rerank,
			LlmProperties llm,
			DemoProperties demo,
			EvalProperties eval
	) {
		this(githubToken, reposDir, qdrantUrl, llmApiKey, ingestion, github, chunking, embedding, qdrant, keyword, hybrid, rerank, llm, demo, eval, null);
	}

	/**
	 * Overloaded constructor preserving compatibility for callers with all properties except eval and answerEval.
	 */
	public GitContextProperties(
			String githubToken,
			Path reposDir,
			String qdrantUrl,
			String llmApiKey,
			IngestionProperties ingestion,
			GitHubProperties github,
			ChunkingProperties chunking,
			EmbeddingProperties embedding,
			QdrantProperties qdrant,
			KeywordProperties keyword,
			HybridProperties hybrid,
			RerankProperties rerank,
			LlmProperties llm,
			DemoProperties demo
	) {
		this(githubToken, reposDir, qdrantUrl, llmApiKey, ingestion, github, chunking, embedding, qdrant, keyword, hybrid, rerank, llm, demo, null, null);
	}

	/**
	 * Overloaded constructor preserving compatibility for callers with all properties except demo, eval, and answerEval.
	 */
	public GitContextProperties(
			String githubToken,
			Path reposDir,
			String qdrantUrl,
			String llmApiKey,
			IngestionProperties ingestion,
			GitHubProperties github,
			ChunkingProperties chunking,
			EmbeddingProperties embedding,
			QdrantProperties qdrant,
			KeywordProperties keyword,
			HybridProperties hybrid,
			RerankProperties rerank,
			LlmProperties llm
	) {
		this(githubToken, reposDir, qdrantUrl, llmApiKey, ingestion, github, chunking, embedding, qdrant, keyword, hybrid, rerank, llm, null, null, null);
	}

	/**
	 * Overloaded constructor preserving compatibility for callers with all search properties except llm.
	 */
	public GitContextProperties(
			String githubToken,
			Path reposDir,
			String qdrantUrl,
			String llmApiKey,
			IngestionProperties ingestion,
			GitHubProperties github,
			ChunkingProperties chunking,
			EmbeddingProperties embedding,
			QdrantProperties qdrant,
			KeywordProperties keyword,
			HybridProperties hybrid,
			RerankProperties rerank
	) {
		this(githubToken, reposDir, qdrantUrl, llmApiKey, ingestion, github, chunking, embedding, qdrant, keyword, hybrid, rerank, null, null);
	}

	/**
	 * Overloaded constructor preserving compatibility for callers with all search properties except rerank.
	 */
	public GitContextProperties(
			String githubToken,
			Path reposDir,
			String qdrantUrl,
			String llmApiKey,
			IngestionProperties ingestion,
			GitHubProperties github,
			ChunkingProperties chunking,
			EmbeddingProperties embedding,
			QdrantProperties qdrant,
			KeywordProperties keyword,
			HybridProperties hybrid
	) {
		this(githubToken, reposDir, qdrantUrl, llmApiKey, ingestion, github, chunking, embedding, qdrant, keyword, hybrid, null, null);
	}

	/**
	 * Overloaded constructor preserving compatibility for callers with chunking properties.
	 */
	public GitContextProperties(
			String githubToken,
			Path reposDir,
			String qdrantUrl,
			String llmApiKey,
			IngestionProperties ingestion,
			GitHubProperties github,
			ChunkingProperties chunking
	) {
		this(githubToken, reposDir, qdrantUrl, llmApiKey, ingestion, github, chunking, null, null, null, null, null);
	}

	/**
	 * Overloaded constructor preserving compatibility for callers without chunking properties.
	 */
	public GitContextProperties(
			String githubToken,
			Path reposDir,
			String qdrantUrl,
			String llmApiKey,
			IngestionProperties ingestion,
			GitHubProperties github
	) {
		this(githubToken, reposDir, qdrantUrl, llmApiKey, ingestion, github, null, null, null, null, null, null);
	}

	/**
	 * Configuration properties for repository ingestion.
	 *
	 * @param maxFileSizeKb maximum file size in kilobytes eligible for ingestion (default: 512)
	 * @param maxCommits maximum number of commits to walk and ingest (default: 500)
	 * @param maxDiffChars maximum character length for git diff output before truncation (default: 4000)
	 * @param excludeDirs directory names to skip during working tree traversal
	 * @param excludePathPrefixes path prefixes relative to repository root to skip during ingestion (default: eval/)
	 * @param docExtensions file extensions classified as documentation documents
	 * @param codeExtensions file extensions classified as source code documents
	 * @param excludeLockFiles filenames of lock files to skip during ingestion
	 * @param excludeMinifiedPatterns filename glob/suffix patterns for minified assets to skip
	 */
	public record IngestionProperties(
			@Min(1) @DefaultValue("512") int maxFileSizeKb,
			@Min(1) @DefaultValue("500") int maxCommits,
			@Min(1) @DefaultValue("4000") int maxDiffChars,
			@NotNull @DefaultValue({".git", "node_modules", "target", "build", "dist", "out", ".idea", ".vscode", ".venv", "venv", "__pycache__", ".gradle", ".mvn", "vendor", "coverage"}) List<String> excludeDirs,
			@NotNull @DefaultValue({"eval/"}) List<String> excludePathPrefixes,
			@NotNull @DefaultValue({"md", "rst", "txt", "adoc"}) List<String> docExtensions,
			@NotNull @DefaultValue({"java", "kt", "py", "js", "jsx", "ts", "tsx", "go", "rs", "c", "h", "cpp", "hpp", "cs", "rb", "php", "dart", "swift", "scala", "sql", "sh", "yml", "yaml", "toml", "json", "xml", "gradle"}) List<String> codeExtensions,
			@NotNull @DefaultValue({"package-lock.json", "yarn.lock", "pnpm-lock.yaml", "poetry.lock", "Cargo.lock"}) List<String> excludeLockFiles,
			@NotNull @DefaultValue({"*.min.js", "*.min.css"}) List<String> excludeMinifiedPatterns
	) {
		private static final Map<String, List<String>> DEFAULT_LISTS = loadDefaultLists();
		private static final int DEFAULT_MAX_FILE_SIZE_KB = loadDefaultInt("maxFileSizeKb", 512);
		private static final int DEFAULT_MAX_COMMITS = loadDefaultInt("maxCommits", 500);
		private static final int DEFAULT_MAX_DIFF_CHARS = loadDefaultInt("maxDiffChars", 4000);

		public IngestionProperties {
			if (maxFileSizeKb <= 0) {
				maxFileSizeKb = DEFAULT_MAX_FILE_SIZE_KB;
			}
			if (maxCommits <= 0) {
				maxCommits = DEFAULT_MAX_COMMITS;
			}
			if (maxDiffChars <= 0) {
				maxDiffChars = DEFAULT_MAX_DIFF_CHARS;
			}
			excludeDirs = excludeDirs != null ? List.copyOf(excludeDirs) : DEFAULT_LISTS.getOrDefault("excludeDirs", List.of());
			excludePathPrefixes = excludePathPrefixes != null ? List.copyOf(excludePathPrefixes) : DEFAULT_LISTS.getOrDefault("excludePathPrefixes", List.of("eval/"));
			docExtensions = docExtensions != null ? List.copyOf(docExtensions) : DEFAULT_LISTS.getOrDefault("docExtensions", List.of());
			codeExtensions = codeExtensions != null ? List.copyOf(codeExtensions) : DEFAULT_LISTS.getOrDefault("codeExtensions", List.of());
			excludeLockFiles = excludeLockFiles != null ? List.copyOf(excludeLockFiles) : DEFAULT_LISTS.getOrDefault("excludeLockFiles", List.of());
			excludeMinifiedPatterns = excludeMinifiedPatterns != null ? List.copyOf(excludeMinifiedPatterns) : DEFAULT_LISTS.getOrDefault("excludeMinifiedPatterns", List.of());
		}

		private static Map<String, List<String>> loadDefaultLists() {
			Map<String, List<String>> map = new HashMap<>();
			for (RecordComponent rc : IngestionProperties.class.getRecordComponents()) {
				if (List.class.isAssignableFrom(rc.getType())) {
					DefaultValue dv = findDefaultValue(rc.getName());
					if (dv != null) {
						map.put(rc.getName(), List.of(dv.value()));
					}
				}
			}
			return Map.copyOf(map);
		}

		private static int loadDefaultInt(String propertyName, int fallback) {
			DefaultValue dv = findDefaultValue(propertyName);
			if (dv != null && dv.value().length > 0) {
				try {
					return Integer.parseInt(dv.value()[0]);
				} catch (NumberFormatException ignored) {
				}
			}
			return fallback;
		}

		private static DefaultValue findDefaultValue(String propertyName) {
			try {
				Field field = IngestionProperties.class.getDeclaredField(propertyName);
				DefaultValue dv = field.getAnnotation(DefaultValue.class);
				if (dv != null) {
					return dv;
				}
			} catch (NoSuchFieldException ignored) {
			}
			for (Constructor<?> ctor : IngestionProperties.class.getDeclaredConstructors()) {
				for (Parameter param : ctor.getParameters()) {
					if (propertyName.equals(param.getName())) {
						DefaultValue dv = param.getAnnotation(DefaultValue.class);
						if (dv != null) {
							return dv;
						}
					}
				}
			}
			return null;
		}
	}

	/**
	 * Configuration properties for GitHub REST API client and ingestion under {@code gitcontext.github}.
	 *
	 * @param apiBaseUrl base URL for the GitHub REST API (default: https://api.github.com)
	 * @param maxPullRequests maximum number of pull requests to ingest (default: 200)
	 * @param maxIssues maximum number of issues to ingest (default: 200)
	 * @param maxCommentsPerItem maximum number of comments to fetch per issue or pull request (default: 50)
	 * @param incidentLabels label names that identify an incident or outage issue (default: incident, bug, postmortem, outage)
	 * @param rateLimitWarnThreshold remaining rate limit threshold below which warning logs are emitted (default: 50)
	 */
	public record GitHubProperties(
			@NotBlank @DefaultValue("https://api.github.com") String apiBaseUrl,
			@Min(1) @DefaultValue("200") int maxPullRequests,
			@Min(1) @DefaultValue("200") int maxIssues,
			@Min(1) @DefaultValue("50") int maxCommentsPerItem,
			@NotNull @DefaultValue({"incident", "bug", "postmortem", "outage"}) List<String> incidentLabels,
			@Min(0) @DefaultValue("50") int rateLimitWarnThreshold
	) {
		private static final Map<String, List<String>> DEFAULT_LISTS = loadDefaultLists();
		private static final String DEFAULT_API_BASE_URL = loadDefaultString("apiBaseUrl", "https://api.github.com");
		private static final int DEFAULT_MAX_PULL_REQUESTS = loadDefaultInt("maxPullRequests", 200);
		private static final int DEFAULT_MAX_ISSUES = loadDefaultInt("maxIssues", 200);
		private static final int DEFAULT_MAX_COMMENTS_PER_ITEM = loadDefaultInt("maxCommentsPerItem", 50);
		private static final int DEFAULT_RATE_LIMIT_WARN_THRESHOLD = loadDefaultInt("rateLimitWarnThreshold", 50);

		public GitHubProperties {
			if (apiBaseUrl == null || apiBaseUrl.isBlank()) {
				apiBaseUrl = DEFAULT_API_BASE_URL;
			}
			if (maxPullRequests <= 0) {
				maxPullRequests = DEFAULT_MAX_PULL_REQUESTS;
			}
			if (maxIssues <= 0) {
				maxIssues = DEFAULT_MAX_ISSUES;
			}
			if (maxCommentsPerItem <= 0) {
				maxCommentsPerItem = DEFAULT_MAX_COMMENTS_PER_ITEM;
			}
			if (rateLimitWarnThreshold < 0) {
				rateLimitWarnThreshold = DEFAULT_RATE_LIMIT_WARN_THRESHOLD;
			}
			incidentLabels = incidentLabels != null ? List.copyOf(incidentLabels) : DEFAULT_LISTS.getOrDefault("incidentLabels", List.of());
		}

		private static Map<String, List<String>> loadDefaultLists() {
			Map<String, List<String>> map = new HashMap<>();
			for (RecordComponent rc : GitHubProperties.class.getRecordComponents()) {
				if (List.class.isAssignableFrom(rc.getType())) {
					DefaultValue dv = findDefaultValue(rc.getName());
					if (dv != null) {
						map.put(rc.getName(), List.of(dv.value()));
					}
				}
			}
			return Map.copyOf(map);
		}

		private static int loadDefaultInt(String propertyName, int fallback) {
			DefaultValue dv = findDefaultValue(propertyName);
			if (dv != null && dv.value().length > 0) {
				try {
					return Integer.parseInt(dv.value()[0]);
				} catch (NumberFormatException ignored) {
				}
			}
			return fallback;
		}

		private static String loadDefaultString(String propertyName, String fallback) {
			DefaultValue dv = findDefaultValue(propertyName);
			if (dv != null && dv.value().length > 0) {
				return dv.value()[0];
			}
			return fallback;
		}

		private static DefaultValue findDefaultValue(String propertyName) {
			try {
				Field field = GitHubProperties.class.getDeclaredField(propertyName);
				DefaultValue dv = field.getAnnotation(DefaultValue.class);
				if (dv != null) {
					return dv;
				}
			} catch (NoSuchFieldException ignored) {
			}
			for (Constructor<?> ctor : GitHubProperties.class.getDeclaredConstructors()) {
				for (Parameter param : ctor.getParameters()) {
					if (propertyName.equals(param.getName())) {
						DefaultValue dv = param.getAnnotation(DefaultValue.class);
						if (dv != null) {
							return dv;
						}
					}
				}
			}
			return null;
		}
	}

	/**
	 * Configuration properties for chunking strategies under {@code gitcontext.chunking}.
	 *
	 * @param maxChunkTokens maximum token count per chunk before splitting (default: 512)
	 * @param minChunkTokens minimum token threshold below which sections are merged (default: 40)
	 * @param overlapLines number of overlapping lines between consecutive chunk windows (default: 5)
	 * @param fallbackWindowLines line window size for the fallback line chunker (default: 60)
	 */
	public record ChunkingProperties(
			@Min(1) @DefaultValue("512") int maxChunkTokens,
			@Min(1) @DefaultValue("40") int minChunkTokens,
			@Min(0) @DefaultValue("5") int overlapLines,
			@Min(1) @DefaultValue("60") int fallbackWindowLines
	) {
	}

	/**
	 * Configuration properties for text embeddings under {@code gitcontext.embedding}.
	 *
	 * @param batchSize maximum number of text segments embedded in a single forward pass (default: 32)
	 * @param queryPrefix instruction prefix prepended to search queries prior to embedding (default: "Represent this sentence for searching relevant passages: ")
	 */
	public record EmbeddingProperties(
			@Min(1) @DefaultValue("32") int batchSize,
			@NotBlank @DefaultValue("Represent this sentence for searching relevant passages: ") String queryPrefix
	) {
	}

	/**
	 * Configuration properties for Qdrant vector database under {@code gitcontext.qdrant}.
	 *
	 * @param url base URL of the Qdrant service (derived from {@code gitcontext.qdrant-url})
	 * @param collection collection name for chunk vector points (default: "gitcontext_chunks")
	 * @param upsertBatchSize maximum number of points upserted per batch request (default: 128)
	 * @param timeoutSeconds connection and request timeout in seconds (default: 30)
	 */
	public record QdrantProperties(
			String url,
			@NotBlank @DefaultValue("gitcontext_chunks") String collection,
			@Min(1) @DefaultValue("128") int upsertBatchSize,
			@Min(1) @DefaultValue("30") int timeoutSeconds
	) {
	}

	/**
	 * Configuration properties for Lucene BM25 keyword index under {@code gitcontext.keyword}.
	 *
	 * @param indexDir directory path where Lucene indexes are stored (defaults to {@code ./data/lucene} relative to repo root)
	 * @param symbolBoost BM25 query boost for AST symbol names (default: 3.0)
	 * @param parentSymbolBoost BM25 query boost for parent symbol names (default: 1.5)
	 * @param pathBoost BM25 query boost for file paths (default: 1.0)
	 */
	public record KeywordProperties(
			@DefaultValue("./data/lucene") Path indexDir,
			@Min(0) @DefaultValue("3.0") double symbolBoost,
			@Min(0) @DefaultValue("1.5") double parentSymbolBoost,
			@Min(0) @DefaultValue("1.0") double pathBoost
	) {
		public KeywordProperties {
			indexDir = resolveToRepoRoot(indexDir != null ? indexDir : Path.of("./data/lucene"));
			if (symbolBoost < 0) {
				symbolBoost = 3.0;
			}
			if (parentSymbolBoost < 0) {
				parentSymbolBoost = 1.5;
			}
			if (pathBoost < 0) {
				pathBoost = 1.0;
			}
		}
	}

	/**
	 * Configuration properties for hybrid retrieval and rank fusion under {@code gitcontext.hybrid}.
	 *
	 * @param candidates number of candidate results fetched from each retriever before rank fusion (default: 50)
	 * @param rrfK smoothing constant k for Reciprocal Rank Fusion scoring (default: 60)
	 * @param defaultMode default search mode when not explicitly requested (default: HYBRID)
	 * @param vectorWeight weight multiplier applied to vector reciprocal rank scores (default: 1.0)
	 * @param keywordWeight weight multiplier applied to keyword reciprocal rank scores (default: 1.0)
	 */
	public record HybridProperties(
			@Min(1) @DefaultValue("50") int candidates,
			@Min(1) @DefaultValue("60") int rrfK,
			@NotNull @DefaultValue("HYBRID") SearchMode defaultMode,
			@Min(0) @DefaultValue("1.0") double vectorWeight,
			@Min(0) @DefaultValue("1.0") double keywordWeight
	) {
		public HybridProperties {
			if (candidates <= 0) {
				candidates = 50;
			}
			if (rrfK <= 0) {
				rrfK = 60;
			}
			if (defaultMode == null) {
				defaultMode = SearchMode.HYBRID;
			}
			if (!Double.isFinite(vectorWeight) || vectorWeight < 0) {
				throw new IllegalArgumentException("vectorWeight must be finite and >= 0, got " + vectorWeight);
			}
			if (!Double.isFinite(keywordWeight) || keywordWeight < 0) {
				throw new IllegalArgumentException("keywordWeight must be finite and >= 0, got " + keywordWeight);
			}
			if (vectorWeight == 0.0 && keywordWeight == 0.0) {
				throw new IllegalArgumentException("vectorWeight and keywordWeight cannot both be 0");
			}
		}
	}

	/**
	 * Configuration properties for cross-encoder reranking under {@code gitcontext.rerank}.
	 *
	 * @param enabled whether cross-encoder reranking is enabled (default: false)
	 * @param modelName local model directory name under data/models/ (default: ms-marco-MiniLM-L-6-v2)
	 * @param modelUrl download URL for the ONNX model weights (default: Hugging Face Xenova quantized ONNX)
	 * @param tokenizerUrl download URL for the tokenizer JSON (default: Hugging Face Xenova tokenizer.json)
	 * @param candidates number of candidate fused results to evaluate with the reranker (default: 30)
	 * @param maxTextChars maximum character length of chunk text passed to the cross-encoder (default: 1000)
	 */
	public record RerankProperties(
			@DefaultValue("false") boolean enabled,
			@NotBlank @DefaultValue("ms-marco-MiniLM-L-6-v2") String modelName,
			@NotBlank @DefaultValue("https://huggingface.co/Xenova/ms-marco-MiniLM-L-6-v2/resolve/main/onnx/model_quantized.onnx") String modelUrl,
			@NotBlank @DefaultValue("https://huggingface.co/Xenova/ms-marco-MiniLM-L-6-v2/resolve/main/tokenizer.json") String tokenizerUrl,
			@Min(1) @DefaultValue("30") int candidates,
			@Min(1) @DefaultValue("1000") int maxTextChars
	) {
	}

	/**
	 * Configuration properties for LLM answer generation under {@code gitcontext.llm}.
	 *
	 * @param provider LLM provider name (default: "gemini")
	 * @param model primary LLM model identifier (default: "gemini-3.5-flash")
	 * @param fallbackModel fallback model identifier when primary model fails (default: "gemini-3.8-flash")
	 * @param baseUrl base URL for provider REST endpoint (default: "https://generativelanguage.googleapis.com/v1beta")
	 * @param temperature generation temperature (default: 0.2)
	 * @param maxOutputTokens maximum generation token length (default: 2048)
	 * @param timeoutSeconds request timeout in seconds (default: 60)
	 * @param maxRetries maximum retry attempts for 429 and 5xx errors (default: 2)
	 * @param thinkingLevel thinking effort preset for reasoning models (default: "low")
	 */
	public record LlmProperties(
			@NotBlank @DefaultValue("gemini") String provider,
			@NotBlank @DefaultValue("gemini-3.5-flash") String model,
			@NotBlank @DefaultValue("gemini-3.8-flash") String fallbackModel,
			@NotBlank @DefaultValue("https://generativelanguage.googleapis.com/v1beta") String baseUrl,
			@DefaultValue("0.2") double temperature,
			@Min(1) @DefaultValue("2048") int maxOutputTokens,
			@Min(1) @DefaultValue("60") int timeoutSeconds,
			@Min(0) @DefaultValue("2") int maxRetries,
			@NotBlank @DefaultValue("low") String thinkingLevel
	) {
	}

	/**
	 * Configuration properties for running demo profiles under {@code gitcontext.demo}.
	 *
	 * @param repo repository to run the demo against (default: "tarunipriya/gitcontext")
	 * @param forceReingest whether to force full re-ingestion before demo queries (default: false)
	 */
	public record DemoProperties(
			@NotBlank @DefaultValue("tarunipriya/gitcontext") String repo,
			@DefaultValue("false") boolean forceReingest
	) {
	}

	/**
	 * Configuration properties for running retrieval evaluations under {@code gitcontext.eval}.
	 *
	 * @param dataset path to the evaluation dataset .jsonl file (default: "eval/datasets/gitcontext.jsonl")
	 * @param repo target repository reference string (default: "tarunipriya/gitcontext")
	 * @param ks list of cutoff rank positions k (default: [1, 3, 5, 10])
	 * @param variants search variant names to evaluate (default: ["VECTOR", "KEYWORD", "HYBRID", "HYBRID_RERANK"])
	 * @param excludeTests whether to exclude test files during retrieval (default: false)
	 * @param reingest whether to reingest repository before evaluation (default: true)
	 * @param warmupQueries number of unrecorded warmup searches per variant (default: 1)
	 * @param outputDir directory path where evaluation reports are saved (default: "eval/results")
	 */
	public record EvalProperties(
			@NotBlank @DefaultValue("eval/datasets/gitcontext.jsonl") String dataset,
			@NotBlank @DefaultValue("tarunipriya/gitcontext") String repo,
			@DefaultValue({"1", "3", "5", "10"}) List<Integer> ks,
			@DefaultValue({"VECTOR", "KEYWORD", "HYBRID", "HYBRID_RERANK"}) List<String> variants,
			@DefaultValue("false") boolean excludeTests,
			@DefaultValue("true") boolean reingest,
			@Min(0) @DefaultValue("1") int warmupQueries,
			@NotBlank @DefaultValue("eval/results") String outputDir
	) {
		public EvalProperties {
			if (dataset == null || dataset.isBlank()) {
				dataset = "eval/datasets/gitcontext.jsonl";
			}
			if (repo == null || repo.isBlank()) {
				repo = "tarunipriya/gitcontext";
			}
			if (ks == null) {
				ks = List.of(1, 3, 5, 10);
			} else {
				if (ks.isEmpty()) {
					throw new IllegalArgumentException("Cutoff ranks ks must not be empty");
				}
				for (Integer k : ks) {
					if (k == null || k <= 0) {
						throw new IllegalArgumentException("Cutoff rank k must be greater than 0, got: " + k);
					}
				}
				ks = ks.stream().distinct().toList();
			}
			if (variants == null || variants.isEmpty()) {
				variants = List.of("VECTOR", "KEYWORD", "HYBRID", "HYBRID_RERANK");
			} else {
				variants = List.copyOf(variants);
			}
			if (warmupQueries < 0) {
				warmupQueries = 1;
			}
			if (outputDir == null || outputDir.isBlank()) {
				outputDir = "eval/results";
			}
		}

		/**
		 * Returns the maximum cutoff rank k in {@code ks}.
		 *
		 * @return maximum rank cutoff
		 */
		public int maxK() {
			return ks.stream().mapToInt(Integer::intValue).max().orElse(10);
		}
	}

	/**
	 * Configuration properties for running answer quality evaluations under {@code gitcontext.answer-eval}.
	 *
	 * @param dataset path to the evaluation dataset .jsonl file (default: "eval/datasets/gitcontext.jsonl")
	 * @param repo target repository reference string (default: "tarunipriya/gitcontext")
	 * @param reingest whether to reingest repository before evaluation (default: true)
	 * @param outputDir directory path where evaluation reports are saved (default: "eval/results")
	 * @param requestDelayMs delay in milliseconds between consecutive LLM calls to respect rate limits (default: 13000)
	 * @param judgeEnabled whether the LLM judge is enabled for fact coverage and groundedness (default: true)
	 * @param judgeMaxSourceChars maximum characters of cited sources passed to the judge (default: 6000)
	 * @param maxCases maximum number of cases to evaluate, or 0 for all (default: 0)
	 * @param maxConsecutiveFailures consecutive generation failures threshold to fail fast (default: 3)
	 * @param resume whether to resume from existing checkpoint if present and valid (default: true)
	 * @param judgeModel model identifier for LLM judge (default: "" = gitcontext.llm.model)
	 * @param disableFallback whether fallback model is disabled during answer evaluation (default: true)
	 */
	public record AnswerEvalProperties(
			@NotBlank @DefaultValue("eval/datasets/gitcontext.jsonl") String dataset,
			@NotBlank @DefaultValue("tarunipriya/gitcontext") String repo,
			@DefaultValue("true") boolean reingest,
			@NotBlank @DefaultValue("eval/results") String outputDir,
			@Min(0) @DefaultValue("13000") long requestDelayMs,
			@DefaultValue("true") boolean judgeEnabled,
			@Min(1) @DefaultValue("6000") int judgeMaxSourceChars,
			@Min(0) @DefaultValue("0") int maxCases,
			@Min(1) @DefaultValue("3") int maxConsecutiveFailures,
			@DefaultValue("true") boolean resume,
			@DefaultValue("") String judgeModel,
			@DefaultValue("true") boolean disableFallback
	) {
		public AnswerEvalProperties {
			if (dataset == null || dataset.isBlank()) {
				dataset = "eval/datasets/gitcontext.jsonl";
			}
			if (repo == null || repo.isBlank()) {
				repo = "tarunipriya/gitcontext";
			}
			if (outputDir == null || outputDir.isBlank()) {
				outputDir = "eval/results";
			}
			if (requestDelayMs < 0) {
				throw new IllegalArgumentException("requestDelayMs must be non-negative, got: " + requestDelayMs);
			}
			if (judgeMaxSourceChars <= 0) {
				judgeMaxSourceChars = 6000;
			}
			if (maxCases < 0) {
				maxCases = 0;
			}
			if (maxConsecutiveFailures <= 0) {
				maxConsecutiveFailures = 3;
			}
			if (judgeModel == null) {
				judgeModel = "";
			}
		}
	}

	/**
	 * Returns {@code true} if a non-blank GitHub personal access token is configured.
	 *
	 * @return true if GitHub token is present and non-blank
	 */
	public boolean hasGithubToken() {
		return githubToken != null && !githubToken.isBlank();
	}

	/**
	 * Returns {@code true} if a non-blank LLM API key is configured.
	 *
	 * @return true if LLM API key is present and non-blank
	 */
	public boolean hasLlmApiKey() {
		return llmApiKey != null && !llmApiKey.isBlank();
	}

	@Override
	public String toString() {
		return "GitContextProperties[" +
				"githubToken=" + maskSecret(githubToken) + ", " +
				"reposDir=" + reposDir + ", " +
				"qdrantUrl=" + qdrantUrl + ", " +
				"llmApiKey=" + maskSecret(llmApiKey) + ", " +
				"ingestion=" + ingestion + ", " +
				"github=" + github + ", " +
				"chunking=" + chunking + ", " +
				"embedding=" + embedding + ", " +
				"qdrant=" + qdrant + ", " +
				"keyword=" + keyword + ", " +
				"hybrid=" + hybrid + ", " +
				"rerank=" + rerank + ", " +
				"llm=" + llm + ", " +
				"demo=" + demo + ", " +
				"eval=" + eval + ", " +
				"answerEval=" + answerEval +
				"]";
	}

	private static String maskSecret(String secret) {
		if (secret == null || secret.isBlank()) {
			return "<not set>";
		}
		return "****";
	}

	public static Path resolveToRepoRoot(Path path) {
		if (path.isAbsolute()) {
			return path.normalize();
		}
		Path repoRoot = findRepoRoot();
		return repoRoot.resolve(path).normalize().toAbsolutePath();
	}

	private static Path findRepoRoot() {
		Path current = Path.of("").toAbsolutePath().normalize();
		Path candidate = current;
		while (candidate != null) {
			if (Files.isDirectory(candidate.resolve(".git")) ||
					(Files.exists(candidate.resolve("backend")) && Files.exists(candidate.resolve("AGENTS.md")))) {
				return candidate;
			}
			candidate = candidate.getParent();
		}
		return current;
	}
}
