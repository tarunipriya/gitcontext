package io.github.tarunipriya.gitcontext.ingestion;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LanguageDetectorTest {

	@Test
	@DisplayName("Detects programming and documentation languages correctly")
	void testLanguageDetection() {
		assertThat(LanguageDetector.detectLanguage("Main.java")).contains("java");
		assertThat(LanguageDetector.detectLanguage("script.py")).contains("python");
		assertThat(LanguageDetector.detectLanguage("index.ts")).contains("typescript");
		assertThat(LanguageDetector.detectLanguage("component.jsx")).contains("javascript");
		assertThat(LanguageDetector.detectLanguage("app.dart")).contains("dart");
		assertThat(LanguageDetector.detectLanguage("README.md")).contains("markdown");
		assertThat(LanguageDetector.detectLanguage("doc.adoc")).contains("asciidoc");
		assertThat(LanguageDetector.detectLanguage("server.go")).contains("go");
		assertThat(LanguageDetector.detectLanguage("lib.rs")).contains("rust");
	}

	@Test
	@DisplayName("Returns empty optional for unknown or missing extensions")
	void testUnknownExtensions() {
		assertThat(LanguageDetector.detectLanguage("archive.zip")).isEmpty();
		assertThat(LanguageDetector.detectLanguage("")).isEmpty();
		assertThat(LanguageDetector.detectLanguage(null)).isEmpty();
	}

	@Test
	@DisplayName("Extracts file extensions properly")
	void testExtractExtension() {
		assertThat(LanguageDetector.extractExtension("foo.bar.baz")).isEqualTo("baz");
		assertThat(LanguageDetector.extractExtension("Dockerfile")).isEqualTo("Dockerfile");
		assertThat(LanguageDetector.extractExtension(null)).isEqualTo("");
	}
}
