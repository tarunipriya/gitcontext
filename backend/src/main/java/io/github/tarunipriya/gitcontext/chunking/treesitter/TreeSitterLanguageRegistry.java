package io.github.tarunipriya.gitcontext.chunking.treesitter;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.treesitter.TSLanguage;
import org.treesitter.TSParser;
import org.treesitter.TreeSitterJava;
import org.treesitter.TreeSitterJavascript;
import org.treesitter.TreeSitterPython;
import org.treesitter.TreeSitterTsx;
import org.treesitter.TreeSitterTypescript;

/**
 * Registry mapping programming language identifiers to lazy-loaded Tree-sitter {@link TSLanguage} instances.
 *
 * <p>Supports creation of configured {@link TSParser} instances. Because {@link TSParser} is not thread-safe,
 * a fresh parser instance should be created per chunking invocation and closed properly.
 */
@Component
public class TreeSitterLanguageRegistry {

	private final Map<String, Supplier<TSLanguage>> languageSuppliers = new ConcurrentHashMap<>();
	private final Map<String, TSLanguage> languageCache = new ConcurrentHashMap<>();

	/**
	 * Creates a new {@link TreeSitterLanguageRegistry} registering default supported language grammar suppliers.
	 */
	public TreeSitterLanguageRegistry() {
		registerSupplier("java", TreeSitterJava::new);
		registerSupplier("python", TreeSitterPython::new);
		registerSupplier("py", TreeSitterPython::new);
		registerSupplier("javascript", TreeSitterJavascript::new);
		registerSupplier("js", TreeSitterJavascript::new);
		registerSupplier("typescript", TreeSitterTypescript::new);
		registerSupplier("ts", TreeSitterTypescript::new);
		registerSupplier("tsx", TreeSitterTsx::new);
	}

	private void registerSupplier(String alias, Supplier<TSLanguage> supplier) {
		languageSuppliers.put(alias.toLowerCase(Locale.ROOT), supplier);
	}

	/**
	 * Returns {@code true} if the language is supported by this registry.
	 */
	public boolean supports(String language) {
		if (language == null || language.isBlank()) {
			return false;
		}
		return languageSuppliers.containsKey(language.trim().toLowerCase(Locale.ROOT));
	}

	/**
	 * Retrieves the lazy-loaded {@link TSLanguage} instance for the language, or null if unsupported.
	 */
	public TSLanguage getLanguage(String language) {
		if (language == null || language.isBlank()) {
			return null;
		}
		String norm = language.trim().toLowerCase(Locale.ROOT);
		Supplier<TSLanguage> supplier = languageSuppliers.get(norm);
		if (supplier == null) {
			return null;
		}
		return languageCache.computeIfAbsent(norm, k -> supplier.get());
	}

	/**
	 * Creates a newly allocated {@link TSParser} configured with the specified language's grammar.
	 *
	 * @param language programming language identifier
	 * @return configured {@link TSParser}, or null if the language is unsupported
	 */
	public TSParser createParser(String language) {
		TSLanguage tsLanguage = getLanguage(language);
		if (tsLanguage == null) {
			return null;
		}
		TSParser parser = new TSParser();
		parser.setLanguage(tsLanguage);
		return parser;
	}
}
