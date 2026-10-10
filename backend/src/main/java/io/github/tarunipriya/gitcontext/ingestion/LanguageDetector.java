package io.github.tarunipriya.gitcontext.ingestion;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Maps file extensions to canonical programming and document language names.
 */
public final class LanguageDetector {

	private static final Map<String, String> EXTENSION_TO_LANGUAGE = new HashMap<>();

	static {
		// Programming languages
		register("java", "java");
		register("kt", "kotlin");
		register("kts", "kotlin");
		register("py", "python");
		register("js", "javascript");
		register("mjs", "javascript");
		register("cjs", "javascript");
		register("jsx", "javascript");
		register("ts", "typescript");
		register("mts", "typescript");
		register("cts", "typescript");
		register("tsx", "typescript");
		register("go", "go");
		register("rs", "rust");
		register("c", "c");
		register("h", "c");
		register("cpp", "cpp");
		register("hpp", "cpp");
		register("cc", "cpp");
		register("cxx", "cpp");
		register("hxx", "cpp");
		register("cs", "csharp");
		register("rb", "ruby");
		register("php", "php");
		register("dart", "dart");
		register("swift", "swift");
		register("scala", "scala");
		register("sql", "sql");
		register("sh", "shell");
		register("bash", "shell");
		register("zsh", "shell");
		register("yml", "yaml");
		register("yaml", "yaml");
		register("toml", "toml");
		register("json", "json");
		register("xml", "xml");
		register("gradle", "gradle");

		// Document formats
		register("md", "markdown");
		register("markdown", "markdown");
		register("rst", "restructuredtext");
		register("txt", "text");
		register("adoc", "asciidoc");
		register("asciidoc", "asciidoc");
	}

	private LanguageDetector() {
		// Utility class
	}

	private static void register(String ext, String lang) {
		EXTENSION_TO_LANGUAGE.put(ext.toLowerCase(Locale.ROOT), lang);
	}

	/**
	 * Detects the language name for a given file name or extension.
	 *
	 * @param filenameOrExtension the filename (e.g. "App.java") or extension (e.g. "java")
	 * @return optional containing the canonical language name if detected
	 */
	public static Optional<String> detectLanguage(String filenameOrExtension) {
		if (filenameOrExtension == null || filenameOrExtension.isBlank()) {
			return Optional.empty();
		}
		String ext = extractExtension(filenameOrExtension).toLowerCase(Locale.ROOT);
		return Optional.ofNullable(EXTENSION_TO_LANGUAGE.get(ext));
	}

	/**
	 * Extracts the file extension (without the leading dot) from a filename.
	 *
	 * @param filename the filename or path
	 * @return extension in lowercase, or the input string if no dot exists
	 */
	public static String extractExtension(String filename) {
		if (filename == null) {
			return "";
		}
		int lastDot = filename.lastIndexOf('.');
		if (lastDot >= 0 && lastDot < filename.length() - 1) {
			return filename.substring(lastDot + 1);
		}
		return filename;
	}
}
