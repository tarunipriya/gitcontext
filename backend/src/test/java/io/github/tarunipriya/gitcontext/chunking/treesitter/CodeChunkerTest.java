package io.github.tarunipriya.gitcontext.chunking.treesitter;

import io.github.tarunipriya.gitcontext.chunking.ApproximateTokenCounter;
import io.github.tarunipriya.gitcontext.chunking.ChunkingService;
import io.github.tarunipriya.gitcontext.chunking.ContextHeaderBuilder;
import io.github.tarunipriya.gitcontext.chunking.LineWindowChunker;
import io.github.tarunipriya.gitcontext.chunking.MarkdownChunker;
import io.github.tarunipriya.gitcontext.chunking.RecordChunker;
import io.github.tarunipriya.gitcontext.config.GitContextProperties;
import io.github.tarunipriya.gitcontext.model.Chunk;
import io.github.tarunipriya.gitcontext.model.ChunkType;
import io.github.tarunipriya.gitcontext.model.Document;
import io.github.tarunipriya.gitcontext.model.DocumentMetadata;
import io.github.tarunipriya.gitcontext.model.SourceType;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CodeChunkerTest {

	private TreeSitterLanguageRegistry registry;
	private ApproximateTokenCounter tokenCounter;
	private ContextHeaderBuilder headerBuilder;
	private GitContextProperties properties;
	private LineWindowChunker lineWindowChunker;
	private CodeChunker codeChunker;

	@BeforeEach
	void setUp() {
		registry = new TreeSitterLanguageRegistry();
		tokenCounter = new ApproximateTokenCounter();
		headerBuilder = new ContextHeaderBuilder();
		properties = new GitContextProperties(
				null, null, "http://localhost:6333", null, null, null,
				new GitContextProperties.ChunkingProperties(512, 40, 5, 60)
		);
		lineWindowChunker = new LineWindowChunker(properties, tokenCounter, headerBuilder);
		codeChunker = new CodeChunker(registry, properties, tokenCounter, headerBuilder, lineWindowChunker);
	}

	private String loadFixture(String name) throws IOException {
		try (var is = getClass().getResourceAsStream("/chunking/" + name)) {
			if (is == null) {
				throw new IllegalArgumentException("Resource not found: /chunking/" + name);
			}
			return new String(is.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	private Document createDoc(String path, String lang, String content) {
		return Document.create(
				content,
				new DocumentMetadata("owner/repo", SourceType.CODE, path, null, null, null, null, null, null, lang, null, null),
				path
		);
	}

	@Test
	@DisplayName("Java: package + imports -> MODULE; class skeleton and methods with exact names, line numbers, and Javadoc")
	void testJavaSampleChunks() throws IOException {
		String source = loadFixture("Sample.java");
		Document doc = createDoc("src/main/java/Sample.java", "java", source);

		List<Chunk> chunks = codeChunker.chunk(doc);
		assertThat(chunks).isNotEmpty();

		// Sequential chunk indexes
		for (int i = 0; i < chunks.size(); i++) {
			assertThat(chunks.get(i).chunkIndex()).isEqualTo(i);
		}

		// 1. MODULE chunk for package + imports
		Chunk moduleChunk = chunks.stream()
				.filter(c -> c.metadata().chunkType() == ChunkType.MODULE)
				.findFirst()
				.orElseThrow();
		assertThat(moduleChunk.content()).contains("package com.example;");
		assertThat(moduleChunk.content()).contains("import java.util.List;");
		assertThat(moduleChunk.metadata().startLine()).isEqualTo(1);
		assertThat(moduleChunk.metadata().endLine()).isEqualTo(4);

		// 2. CLASS skeleton for SampleService
		Chunk classChunk = chunks.stream()
				.filter(c -> c.metadata().chunkType() == ChunkType.CLASS && "SampleService".equals(c.metadata().symbolName()))
				.findFirst()
				.orElseThrow();
		assertThat(classChunk.metadata().parentSymbol()).isNull();
		assertThat(classChunk.metadata().startLine()).isEqualTo(6); // includes javadoc
		assertThat(classChunk.content()).contains("/**\n * Service for sample operations.\n */");
		assertThat(classChunk.content()).contains("@Deprecated");
		assertThat(classChunk.content()).contains("private final String name;");
		assertThat(classChunk.content()).contains("public SampleService(String name) { ... }");
		assertThat(classChunk.content()).contains("public String greet(String target) { ... }");

		// 3. Constructor chunk
		Chunk constructorChunk = chunks.stream()
				.filter(c -> c.metadata().chunkType() == ChunkType.METHOD && "SampleService".equals(c.metadata().symbolName()))
				.findFirst()
				.orElseThrow();
		assertThat(constructorChunk.metadata().parentSymbol()).isEqualTo("SampleService");
		assertThat(constructorChunk.content()).contains("Constructs a new SampleService");

		// 4. greet method chunk
		Chunk greetChunk = chunks.stream()
				.filter(c -> c.metadata().chunkType() == ChunkType.METHOD && "greet".equals(c.metadata().symbolName()))
				.findFirst()
				.orElseThrow();
		assertThat(greetChunk.metadata().parentSymbol()).isEqualTo("SampleService");
		assertThat(greetChunk.content()).contains("Greets the user.");
		assertThat(greetChunk.content()).contains("@Override");
		assertThat(greetChunk.content()).contains("return \"Hello \" + target;");

		// 5. Nested class InnerHelper
		Chunk innerClassChunk = chunks.stream()
				.filter(c -> c.metadata().chunkType() == ChunkType.CLASS && "InnerHelper".equals(c.metadata().symbolName()))
				.findFirst()
				.orElseThrow();
		assertThat(innerClassChunk.metadata().parentSymbol()).isEqualTo("SampleService");

		// 6. Nested class method assist with dotted parentSymbol
		Chunk assistChunk = chunks.stream()
				.filter(c -> c.metadata().chunkType() == ChunkType.METHOD && "assist".equals(c.metadata().symbolName()))
				.findFirst()
				.orElseThrow();
		assertThat(assistChunk.metadata().parentSymbol()).isEqualTo("SampleService.InnerHelper");

		// 7. Interface, enum, and record recognized as CLASS chunks
		assertThat(chunks).anyMatch(c -> c.metadata().chunkType() == ChunkType.CLASS && "Worker".equals(c.metadata().symbolName()));
		assertThat(chunks).anyMatch(c -> c.metadata().chunkType() == ChunkType.CLASS && "Status".equals(c.metadata().symbolName()));
		assertThat(chunks).anyMatch(c -> c.metadata().chunkType() == ChunkType.CLASS && "UserRecord".equals(c.metadata().symbolName()));

		// 8. Small getters merged into one chunk with symbolName joined by ", "
		Chunk gettersChunk = chunks.stream()
				.filter(c -> c.metadata().chunkType() == ChunkType.METHOD && "getX, getY".equals(c.metadata().symbolName()))
				.findFirst()
				.orElseThrow();
		assertThat(gettersChunk.content()).contains("public int getX()");
		assertThat(gettersChunk.content()).contains("public int getY()");
		assertThat(gettersChunk.metadata().parentSymbol()).isEqualTo("SampleService");
	}

	@Test
	@DisplayName("Java: huge method split into overlapping windows keeping symbolName, parentSymbol, and chunkType")
	void testHugeMethodSplitWithOverlap() throws IOException {
		String source = loadFixture("HugeMethod.java");
		Document doc = createDoc("HugeClass.java", "java", source);

		// Configure tight maxChunkTokens (45 tokens) so hugeOperation with ~75 tokens must split
		GitContextProperties tightProps = new GitContextProperties(
				null, null, "http://localhost:6333", null, null, null,
				new GitContextProperties.ChunkingProperties(45, 5, 3, 60)
		);
		LineWindowChunker tightLineChunker = new LineWindowChunker(tightProps, tokenCounter, headerBuilder);
		CodeChunker tightCodeChunker = new CodeChunker(registry, tightProps, tokenCounter, headerBuilder, tightLineChunker);

		List<Chunk> chunks = tightCodeChunker.chunk(doc);

		List<Chunk> methodChunks = chunks.stream()
				.filter(c -> "hugeOperation".equals(c.metadata().symbolName()))
				.toList();

		assertThat(methodChunks.size()).isGreaterThanOrEqualTo(2);
		for (Chunk mc : methodChunks) {
			assertThat(mc.metadata().chunkType()).isEqualTo(ChunkType.METHOD);
			assertThat(mc.metadata().symbolName()).isEqualTo("hugeOperation");
			assertThat(mc.metadata().parentSymbol()).isEqualTo("HugeClass");
			assertThat(mc.metadata().tokenCount()).isLessThanOrEqualTo(45);
		}
	}

	@Test
	@DisplayName("Python: class with methods, decorated function keeps decorator, top-level function -> FUNCTION, module-level -> MODULE")
	void testPythonSampleChunks() throws IOException {
		String source = loadFixture("sample.py");
		Document doc = createDoc("sample.py", "python", source);

		List<Chunk> chunks = codeChunker.chunk(doc);
		assertThat(chunks).isNotEmpty();

		// Module chunk for imports and constants
		Chunk moduleChunk = chunks.stream()
				.filter(c -> c.metadata().chunkType() == ChunkType.MODULE)
				.findFirst()
				.orElseThrow();
		assertThat(moduleChunk.content()).contains("import os");
		assertThat(moduleChunk.content()).contains("DEFAULT_TIMEOUT = 30");

		// Class Calculator skeleton
		Chunk calcClass = chunks.stream()
				.filter(c -> c.metadata().chunkType() == ChunkType.CLASS && "Calculator".equals(c.metadata().symbolName()))
				.findFirst()
				.orElseThrow();
		assertThat(calcClass.content()).contains("@decor_class");
		assertThat(calcClass.content()).contains("class Calculator:");
		assertThat(calcClass.content()).contains("...");

		// Small methods add and subtract merged
		Chunk mergedMethods = chunks.stream()
				.filter(c -> c.metadata().chunkType() == ChunkType.METHOD && "add, subtract".equals(c.metadata().symbolName()))
				.findFirst()
				.orElseThrow();
		assertThat(mergedMethods.metadata().parentSymbol()).isEqualTo("Calculator");
		assertThat(mergedMethods.content()).contains("@audit");
		assertThat(mergedMethods.content()).contains("def add(self, a, b):");
		assertThat(mergedMethods.content()).contains("def subtract(self, a, b):");

		// Standalone method multiply in Calculator
		Chunk multiplyMethod = chunks.stream()
				.filter(c -> c.metadata().chunkType() == ChunkType.METHOD && "multiply".equals(c.metadata().symbolName()))
				.findFirst()
				.orElseThrow();
		assertThat(multiplyMethod.metadata().parentSymbol()).isEqualTo("Calculator");
		assertThat(multiplyMethod.content()).contains("def multiply(self, factor, items):");

		// Top-level decorated function compute_metrics -> FUNCTION
		Chunk metricsFunc = chunks.stream()
				.filter(c -> c.metadata().chunkType() == ChunkType.FUNCTION && "compute_metrics".equals(c.metadata().symbolName()))
				.findFirst()
				.orElseThrow();
		assertThat(metricsFunc.metadata().parentSymbol()).isNull();
		assertThat(metricsFunc.content()).contains("@timing");
		assertThat(metricsFunc.content()).contains("def compute_metrics(data):");

		// Top-level main function -> FUNCTION
		Chunk mainFunc = chunks.stream()
				.filter(c -> c.metadata().chunkType() == ChunkType.FUNCTION && "main".equals(c.metadata().symbolName()))
				.findFirst()
				.orElseThrow();
		assertThat(mainFunc.metadata().parentSymbol()).isNull();
		assertThat(mainFunc.content()).contains("def main():");
	}

	@Test
	@DisplayName("JavaScript/TypeScript: class with methods, function declaration, exported arrow function assigned to const")
	void testTypeScriptSampleChunks() throws IOException {
		String source = loadFixture("sample.ts");
		Document doc = createDoc("sample.ts", "typescript", source);

		List<Chunk> chunks = codeChunker.chunk(doc);
		assertThat(chunks).isNotEmpty();

		// Class UserService skeleton
		Chunk userClass = chunks.stream()
				.filter(c -> c.metadata().chunkType() == ChunkType.CLASS && "UserService".equals(c.metadata().symbolName()))
				.findFirst()
				.orElseThrow();
		assertThat(userClass.content()).contains("export class UserService");

		// Method getUser
		Chunk getUserMethod = chunks.stream()
				.filter(c -> c.metadata().chunkType() == ChunkType.METHOD && "getUser".equals(c.metadata().symbolName()))
				.findFirst()
				.orElseThrow();
		assertThat(getUserMethod.metadata().parentSymbol()).isEqualTo("UserService");
		assertThat(getUserMethod.content()).contains("getUser(id: string): string");

		// Exported arrow function formatName assigned to const -> FUNCTION
		Chunk arrowFunc = chunks.stream()
				.filter(c -> c.metadata().chunkType() == ChunkType.FUNCTION && "formatName".equals(c.metadata().symbolName()))
				.findFirst()
				.orElseThrow();
		assertThat(arrowFunc.metadata().parentSymbol()).isNull();
		assertThat(arrowFunc.content()).contains("export const formatName =");

		// Regular function calculateTotal -> FUNCTION
		Chunk totalFunc = chunks.stream()
				.filter(c -> c.metadata().chunkType() == ChunkType.FUNCTION && "calculateTotal".equals(c.metadata().symbolName()))
				.findFirst()
				.orElseThrow();
		assertThat(totalFunc.metadata().parentSymbol()).isNull();
		assertThat(totalFunc.content()).contains("function calculateTotal");
	}

	@Test
	@DisplayName("JavaScript: class with methods, function declaration, and arrow function assigned to const")
	void testJavaScriptChunking() throws IOException {
		String source = loadFixture("sample.js");
		Document doc = createDoc("sample.js", "javascript", source);

		List<Chunk> chunks = codeChunker.chunk(doc);
		assertThat(chunks).isNotEmpty();

		// Class Calculator
		Chunk calcClass = chunks.stream()
				.filter(c -> c.metadata().chunkType() == ChunkType.CLASS && "Calculator".equals(c.metadata().symbolName()))
				.findFirst()
				.orElseThrow();
		assertThat(calcClass.content()).contains("class Calculator");

		// Methods add and subtract within Calculator
		Chunk methodChunk = chunks.stream()
				.filter(c -> c.metadata().chunkType() == ChunkType.METHOD && c.metadata().symbolName().contains("add"))
				.findFirst()
				.orElseThrow();
		assertThat(methodChunk.metadata().parentSymbol()).isEqualTo("Calculator");
		assertThat(methodChunk.content()).contains("add(n)");
		assertThat(methodChunk.content()).contains("subtract(n)");

		// Arrow function assigned to const -> FUNCTION
		Chunk arrowFunc = chunks.stream()
				.filter(c -> c.metadata().chunkType() == ChunkType.FUNCTION && "multiply".equals(c.metadata().symbolName()))
				.findFirst()
				.orElseThrow();
		assertThat(arrowFunc.metadata().parentSymbol()).isNull();
		assertThat(arrowFunc.content()).contains("const multiply =");

		// Function declaration divide -> FUNCTION
		Chunk divideFunc = chunks.stream()
				.filter(c -> c.metadata().chunkType() == ChunkType.FUNCTION && "divide".equals(c.metadata().symbolName()))
				.findFirst()
				.orElseThrow();
		assertThat(divideFunc.metadata().parentSymbol()).isNull();
		assertThat(divideFunc.content()).contains("function divide");
	}

	@Test
	@DisplayName("Telugu text and emoji in comments: chunk content and line numbers are exact (byte/char safety)")
	void testTeluguUnicodeSafety() throws IOException {
		String source = loadFixture("unicode_telugu.java");
		Document doc = createDoc("UnicodeService.java", "java", source);

		List<Chunk> chunks = codeChunker.chunk(doc);
		assertThat(chunks).isNotEmpty();

		// Class chunk
		Chunk classChunk = chunks.stream()
				.filter(c -> c.metadata().chunkType() == ChunkType.CLASS && "UnicodeService".equals(c.metadata().symbolName()))
				.findFirst()
				.orElseThrow();
		assertThat(classChunk.content()).contains("తెలుగు డాక్యుమెంటేషన్ కామెంట్ 🚀 Sparkle");
		assertThat(classChunk.metadata().startLine()).isEqualTo(3);

		// Method chunk
		Chunk greetChunk = chunks.stream()
				.filter(c -> c.metadata().chunkType() == ChunkType.METHOD && "greetTelugu".equals(c.metadata().symbolName()))
				.findFirst()
				.orElseThrow();
		assertThat(greetChunk.content()).contains("నమస్కారం పద్ధతి 🎯 Target");
		assertThat(greetChunk.content()).contains("return \"నమస్కారం \" + name;");
		assertThat(greetChunk.metadata().startLine()).isEqualTo(8);
		assertThat(greetChunk.metadata().endLine()).isEqualTo(13);
	}

	@Test
	@DisplayName("CRLF line endings produce identical line numbers to LF")
	void testCrlfLineEndings() throws IOException {
		String lfSource = loadFixture("Sample.java").replace("\r\n", "\n");
		String crlfSource = lfSource.replace("\n", "\r\n");

		Document lfDoc = createDoc("Sample.java", "java", lfSource);
		Document crlfDoc = createDoc("Sample.java", "java", crlfSource);

		List<Chunk> lfChunks = codeChunker.chunk(lfDoc);
		List<Chunk> crlfChunks = codeChunker.chunk(crlfDoc);

		assertThat(crlfChunks).hasSameSizeAs(lfChunks);
		for (int i = 0; i < lfChunks.size(); i++) {
			Chunk lf = lfChunks.get(i);
			Chunk crlf = crlfChunks.get(i);
			assertThat(crlf.metadata().startLine()).isEqualTo(lf.metadata().startLine());
			assertThat(crlf.metadata().endLine()).isEqualTo(lf.metadata().endLine());
			assertThat(crlf.metadata().chunkType()).isEqualTo(lf.metadata().chunkType());
			assertThat(crlf.metadata().symbolName()).isEqualTo(lf.metadata().symbolName());
			assertThat(crlf.metadata().parentSymbol()).isEqualTo(lf.metadata().parentSymbol());
			assertThat(crlf.content()).isEqualTo(lf.content());
		}
	}

	@Test
	@DisplayName("A file with syntax errors still produces chunks or falls back without throwing")
	void testSyntaxErrorsProduceChunksOrFallbackWithoutThrowing() throws IOException {
		String brokenSource = loadFixture("syntax_error.java");
		Document doc = createDoc("BrokenClass.java", "java", brokenSource);

		// Must not throw
		List<Chunk> chunks = codeChunker.chunk(doc);
		assertThat(chunks).isNotEmpty();
		for (Chunk chunk : chunks) {
			assertThat(chunk.content()).isNotBlank();
			assertThat(chunk.metadata().startLine()).isNotNull();
			assertThat(chunk.metadata().endLine()).isNotNull();
		}
	}

	@Test
	@DisplayName("An unsupported language (e.g. dart) is rejected by CodeChunker and handled by LineWindowChunker")
	void testUnsupportedLanguageRoutesToLineWindowChunker() {
		String dartCode = """
				void main() {
				  print('Hello Dart');
				}
				""";
		Document dartDoc = createDoc("main.dart", "dart", dartCode);

		assertThat(codeChunker.supports(dartDoc)).isFalse();

		MarkdownChunker markdownChunker = new MarkdownChunker(properties, tokenCounter, headerBuilder);
		RecordChunker recordChunker = new RecordChunker(properties, tokenCounter, headerBuilder);
		ChunkingService chunkingService = new ChunkingService(List.of(codeChunker, markdownChunker, recordChunker, lineWindowChunker));

		List<Chunk> chunks = chunkingService.chunkAll(List.of(dartDoc));
		assertThat(chunks).isNotEmpty();
		assertThat(chunks.get(0).metadata().chunkType()).isEqualTo(ChunkType.LINES);
	}
}
