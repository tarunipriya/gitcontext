package io.github.tarunipriya.gitcontext.generation;

import io.github.tarunipriya.gitcontext.chunking.ApproximateTokenCounter;
import io.github.tarunipriya.gitcontext.chunking.TokenCounter;
import io.github.tarunipriya.gitcontext.indexing.SearchHit;
import io.github.tarunipriya.gitcontext.model.ChunkType;
import io.github.tarunipriya.gitcontext.model.SourceType;
import io.github.tarunipriya.gitcontext.retrieval.HybridSearchHit;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ContextBuilderTest {

	private ContextBuilder contextBuilder;
	private TokenCounter tokenCounter;

	@BeforeEach
	void setUp() {
		tokenCounter = new ApproximateTokenCounter();
		contextBuilder = new ContextBuilder(tokenCounter);
	}

	private SearchHit createHit(String chunkId, String path, Integer startLine, Integer endLine, String content) {
		return new SearchHit(
				"id-" + chunkId,
				0.8,
				"owner/repo",
				"run1",
				chunkId,
				"doc-" + chunkId,
				0,
				SourceType.CODE,
				ChunkType.METHOD,
				path,
				"java",
				"myMethod",
				"MyClass",
				null,
				startLine,
				endLine,
				10,
				"https://github.com/owner/repo/blob/main/" + path,
				"sha123",
				content,
				content
		);
	}

	@Test
	@DisplayName("buildContext limits selected sources to maxSources")
	void buildContext_maxSourcesCapEnforced() {
		List<HybridSearchHit> hits = new ArrayList<>();
		for (int i = 1; i <= 10; i++) {
			hits.add(new HybridSearchHit(createHit("c" + i, "File" + i + ".java", 1, 10, "code " + i), 0.9, null, null, null, null));
		}

		ContextBuilder.ContextBuildResult result = contextBuilder.buildContext(hits, 4);

		assertThat(result.sources()).hasSize(4);
		assertThat(result.sources().get(0).number()).isEqualTo(1);
		assertThat(result.sources().get(3).number()).isEqualTo(4);
	}

	@Test
	@DisplayName("buildContext enforces at most 3 chunks per file")
	void buildContext_perFileCapEnforced() {
		List<HybridSearchHit> hits = List.of(
				new HybridSearchHit(createHit("c1", "File.java", 1, 10, "code 1"), 0.9, null, null, null, null),
				new HybridSearchHit(createHit("c2", "File.java", 20, 30, "code 2"), 0.8, null, null, null, null),
				new HybridSearchHit(createHit("c3", "File.java", 40, 50, "code 3"), 0.7, null, null, null, null),
				new HybridSearchHit(createHit("c4", "File.java", 60, 70, "code 4"), 0.6, null, null, null, null),
				new HybridSearchHit(createHit("c5", "Other.java", 1, 10, "code 5"), 0.5, null, null, null, null)
		);

		ContextBuilder.ContextBuildResult result = contextBuilder.buildContext(hits, 8);

		// Exactly 3 from File.java + 1 from Other.java = 4
		assertThat(result.sources()).hasSize(4);
		List<String> chunkIds = result.sources().stream().map(s -> s.hit().chunkId()).toList();
		assertThat(chunkIds).containsExactly("c1", "c2", "c3", "c5");
	}

	@Test
	@DisplayName("buildContext skips chunks whose line ranges overlap an already selected chunk of the same file")
	void buildContext_lineOverlapSkipped() {
		List<HybridSearchHit> hits = List.of(
				new HybridSearchHit(createHit("c1", "Service.java", 10, 25, "code 1"), 0.9, null, null, null, null),
				new HybridSearchHit(createHit("c2", "Service.java", 20, 35, "overlapping"), 0.8, null, null, null, null),
				new HybridSearchHit(createHit("c3", "Service.java", 40, 55, "non overlapping"), 0.7, null, null, null, null)
		);

		ContextBuilder.ContextBuildResult result = contextBuilder.buildContext(hits, 8);

		assertThat(result.sources()).hasSize(2);
		assertThat(result.sources().get(0).hit().chunkId()).isEqualTo("c1");
		assertThat(result.sources().get(1).hit().chunkId()).isEqualTo("c3");
	}

	@Test
	@DisplayName("buildContext respects context token budget")
	void buildContext_tokenBudgetEnforced() {
		// Mock TokenCounter with high token values
		TokenCounter heavyCounter = text -> 2500;
		ContextBuilder heavyBuilder = new ContextBuilder(heavyCounter);

		List<HybridSearchHit> hits = List.of(
				new HybridSearchHit(createHit("c1", "F1.java", 1, 10, "first"), 0.9, null, null, null, null),
				new HybridSearchHit(createHit("c2", "F2.java", 1, 10, "second"), 0.8, null, null, null, null),
				new HybridSearchHit(createHit("c3", "F3.java", 1, 10, "third"), 0.7, null, null, null, null)
		);

		// Budget is 6000: 2500 + 2500 = 5000 <= 6000; 3rd chunk would make 7500 > 6000
		ContextBuilder.ContextBuildResult result = heavyBuilder.buildContext(hits, 8);

		assertThat(result.sources()).hasSize(2);
		assertThat(result.totalTokens()).isEqualTo(5000);
	}

	@Test
	@DisplayName("buildContext numbers and formats code fences tagged with language")
	void buildContext_numberingAndFormatting() {
		SearchHit hit = createHit("c1", "src/main/java/App.java", 15, 30, "System.out.println(\"hello\");");
		List<HybridSearchHit> hits = List.of(new HybridSearchHit(hit, 0.9, null, null, null, null));

		ContextBuilder.ContextBuildResult result = contextBuilder.buildContext(hits, 5);

		assertThat(result.formattedContext()).contains("[1] src/main/java/App.java (lines 15-30), METHOD myMethod");
		assertThat(result.formattedContext()).contains("```java\nSystem.out.println(\"hello\");\n```");
	}
}
