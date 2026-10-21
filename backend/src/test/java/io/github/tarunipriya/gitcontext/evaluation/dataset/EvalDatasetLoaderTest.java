package io.github.tarunipriya.gitcontext.evaluation.dataset;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EvalDatasetLoaderTest {

	@TempDir
	Path tempDir;

	@Nested
	@DisplayName("Valid Datasets")
	class ValidDatasetTests {

		@Test
		@DisplayName("Loads valid dataset containing answerable and unanswerable cases with comments and blank lines")
		void validDatasetWithMixedCases() throws IOException {
			Path file = tempDir.resolve("smoke-bench.jsonl");
			String content = """
					// Benchmark header comment
					// Dataset created for evaluation smoke tests
					
					{"id": "c-001", "question": "How is chunking implemented?", "expectedPaths": ["src/chunking/Chunker.java"], "expectedFacts": ["Uses tree-sitter", "Splits by AST node"], "category": "architecture", "answerable": true}
					
					// Another comment
					{"id": "c-002", "question": "Where is the React frontend located?", "expectedPaths": [], "expectedFacts": ["Frontend does not exist in repo"], "category": "", "answerable": false}
					
					{"id": "c-003", "question": "What is the Lucene BM25 index path?", "expectedPaths": ["src/config/GitContextProperties.java"], "expectedFacts": ["Defaults to ./data/lucene"], "answerable": true}
					""";
			Files.writeString(file, content);

			EvalDataset dataset = EvalDatasetLoader.load(file);

			assertThat(dataset.name()).isEqualTo("smoke-bench");
			assertThat(dataset.cases()).hasSize(3);

			List<EvalCase> answerable = dataset.answerableCases();
			assertThat(answerable).hasSize(2);
			assertThat(answerable).extracting(EvalCase::id).containsExactly("c-001", "c-003");

			List<EvalCase> unanswerable = dataset.unanswerableCases();
			assertThat(unanswerable).hasSize(1);
			assertThat(unanswerable.get(0).id()).isEqualTo("c-002");
			// Blank category defaulted to "general"
			assertThat(unanswerable.get(0).category()).isEqualTo("general");

			EvalCase case1 = answerable.get(0);
			assertThat(case1.expectedPaths()).containsExactly("src/chunking/Chunker.java");
			assertThat(case1.expectedFacts()).containsExactly("Uses tree-sitter", "Splits by AST node");
			assertThat(case1.category()).isEqualTo("architecture");
		}

		@Test
		@DisplayName("Normalizes Windows backslashes and leading ./ or / in expectedPaths")
		void pathNormalizationInExpectedPaths() throws IOException {
			Path file = tempDir.resolve("paths-test.jsonl");
			String content = """
					{"id": "p-01", "question": "Where is App configured?", "expectedPaths": [".\\\\src\\\\main\\\\App.java", "./src/util/Helper.java", "/src/config/Config.java"], "answerable": true}
					""";
			Files.writeString(file, content);

			EvalDataset dataset = EvalDatasetLoader.load(file);
			assertThat(dataset.cases()).hasSize(1);

			EvalCase evalCase = dataset.cases().get(0);
			assertThat(evalCase.expectedPaths()).containsExactly(
					"src/main/App.java",
					"src/util/Helper.java",
					"src/config/Config.java"
			);
		}

		@Test
		@DisplayName("A line without answerable field and with expectedPaths loads as answerable=true")
		void lineWithoutAnswerableDefaultsToTrue() throws IOException {
			Path file = tempDir.resolve("default-answerable.jsonl");
			String content = """
					{"id": "c-def", "question": "Where is App configured?", "expectedPaths": ["src/main/App.java"], "expectedFacts": ["In App.java"]}
					""";
			Files.writeString(file, content);

			EvalDataset dataset = EvalDatasetLoader.load(file);
			assertThat(dataset.cases()).hasSize(1);

			EvalCase evalCase = dataset.cases().get(0);
			assertThat(evalCase.id()).isEqualTo("c-def");
			assertThat(evalCase.answerable()).isTrue();
			assertThat(evalCase.expectedPaths()).containsExactly("src/main/App.java");
		}

		@Test
		@DisplayName("A line with answerable:false and no expectedPaths loads as unanswerable")
		void lineWithAnswerableFalseAndNoExpectedPathsLoadsAsUnanswerable() throws IOException {
			Path file = tempDir.resolve("unanswerable.jsonl");
			String content = """
					{"id": "c-unans", "question": "Where is Vue code?", "expectedPaths": [], "expectedFacts": ["Vue is not in repo"], "answerable": false}
					""";
			Files.writeString(file, content);

			EvalDataset dataset = EvalDatasetLoader.load(file);
			assertThat(dataset.cases()).hasSize(1);

			EvalCase evalCase = dataset.cases().get(0);
			assertThat(evalCase.id()).isEqualTo("c-unans");
			assertThat(evalCase.answerable()).isFalse();
			assertThat(evalCase.expectedPaths()).isEmpty();
			assertThat(dataset.unanswerableCases()).containsExactly(evalCase);
		}
	}

	@Nested
	@DisplayName("Validation Error Cases")
	class ValidationErrorTests {

		@Test
		@DisplayName("Throws when jsonlFile is null")
		void rejectsNullFile() {
			assertThatNullPointerException()
					.isThrownBy(() -> EvalDatasetLoader.load(null))
					.withMessageContaining("jsonlFile must not be null");
		}

		@Test
		@DisplayName("Throws when file does not exist")
		void rejectsNonExistentFile() {
			Path missing = tempDir.resolve("missing.jsonl");
			assertThatThrownBy(() -> EvalDatasetLoader.load(missing))
					.isInstanceOf(EvalDatasetException.class)
					.hasMessageContaining("does not exist");
		}

		@Test
		@DisplayName("Throws when dataset is empty (only comments or blank lines)")
		void rejectsEmptyDataset() throws IOException {
			Path file = tempDir.resolve("empty.jsonl");
			Files.writeString(file, "// Just a comment\n\n   \n// Another comment\n");

			assertThatThrownBy(() -> EvalDatasetLoader.load(file))
					.isInstanceOf(EvalDatasetException.class)
					.hasMessageContaining("Empty dataset in empty.jsonl");
		}

		@Test
		@DisplayName("Invalid JSON throws EvalDatasetException containing exact line number")
		void rejectsInvalidJsonWithLineNumber() throws IOException {
			Path file = tempDir.resolve("bad-json.jsonl");
			String content = """
					// Line 1: comment
					{"id": "valid-1", "question": "Q1", "expectedPaths": ["A.java"], "answerable": true}
					// Line 3: comment
					{ this is not valid json }
					""";
			Files.writeString(file, content);

			assertThatThrownBy(() -> EvalDatasetLoader.load(file))
					.isInstanceOf(EvalDatasetException.class)
					.hasMessageContaining("line 4")
					.hasMessageContaining("Invalid JSON in bad-json.jsonl");
		}

		@Test
		@DisplayName("Blank ID throws EvalDatasetException containing exact line number")
		void rejectsBlankIdWithLineNumber() throws IOException {
			Path file = tempDir.resolve("blank-id.jsonl");
			String content = """
					{"id": "ok-1", "question": "Q1", "expectedPaths": ["A.java"], "answerable": true}
					{"id": "   ", "question": "Q2", "expectedPaths": ["B.java"], "answerable": true}
					""";
			Files.writeString(file, content);

			assertThatThrownBy(() -> EvalDatasetLoader.load(file))
					.isInstanceOf(EvalDatasetException.class)
					.hasMessageContaining("line 2")
					.hasMessageContaining("Blank id in blank-id.jsonl");
		}

		@Test
		@DisplayName("Blank question throws EvalDatasetException containing exact line number")
		void rejectsBlankQuestionWithLineNumber() throws IOException {
			Path file = tempDir.resolve("blank-q.jsonl");
			String content = """
					{"id": "q-1", "question": "", "expectedPaths": ["A.java"], "answerable": true}
					""";
			Files.writeString(file, content);

			assertThatThrownBy(() -> EvalDatasetLoader.load(file))
					.isInstanceOf(EvalDatasetException.class)
					.hasMessageContaining("line 1")
					.hasMessageContaining("Blank question in blank-q.jsonl");
		}

		@Test
		@DisplayName("Duplicate ID throws EvalDatasetException containing exact line number")
		void rejectsDuplicateIdWithLineNumber() throws IOException {
			Path file = tempDir.resolve("duplicate-id.jsonl");
			String content = """
					{"id": "case-01", "question": "Q1", "expectedPaths": ["A.java"], "answerable": true}
					// Some comment
					
					{"id": "case-01", "question": "Q2", "expectedPaths": ["B.java"], "answerable": true}
					""";
			Files.writeString(file, content);

			assertThatThrownBy(() -> EvalDatasetLoader.load(file))
					.isInstanceOf(EvalDatasetException.class)
					.hasMessageContaining("line 4")
					.hasMessageContaining("Duplicate id 'case-01' in duplicate-id.jsonl");
		}

		@Test
		@DisplayName("Answerable case with empty expectedPaths throws with line number")
		void rejectsAnswerableWithEmptyExpectedPaths() throws IOException {
			Path file = tempDir.resolve("empty-paths.jsonl");
			String content = """
					{"id": "c-01", "question": "Q1", "expectedPaths": [], "answerable": true}
					""";
			Files.writeString(file, content);

			assertThatThrownBy(() -> EvalDatasetLoader.load(file))
					.isInstanceOf(EvalDatasetException.class)
					.hasMessageContaining("line 1")
					.hasMessageContaining("Answerable case has empty expectedPaths");
		}

		@Test
		@DisplayName("Unanswerable case with non-empty expectedPaths throws with line number")
		void rejectsUnanswerableWithNonEmptyExpectedPaths() throws IOException {
			Path file = tempDir.resolve("unanswerable-with-paths.jsonl");
			String content = """
					// Header
					{"id": "c-01", "question": "Q1", "expectedPaths": ["A.java"], "answerable": false}
					""";
			Files.writeString(file, content);

			assertThatThrownBy(() -> EvalDatasetLoader.load(file))
					.isInstanceOf(EvalDatasetException.class)
					.hasMessageContaining("line 2")
					.hasMessageContaining("Unanswerable case has non-empty expectedPaths");
		}

		@Test
		@DisplayName("Unknown JSON property throws strict error with line number")
		void rejectsUnknownJsonPropertyWithLineNumber() throws IOException {
			Path file = tempDir.resolve("unknown-field.jsonl");
			String content = """
					{"id": "c-01", "question": "Q1", "expectedPaths": ["A.java"], "answerable": true, "extraBonusProperty": 123}
					""";
			Files.writeString(file, content);

			assertThatThrownBy(() -> EvalDatasetLoader.load(file))
					.isInstanceOf(EvalDatasetException.class)
					.hasMessageContaining("line 1")
					.hasMessageContaining("Invalid JSON in unknown-field.jsonl");
		}
	}
}
