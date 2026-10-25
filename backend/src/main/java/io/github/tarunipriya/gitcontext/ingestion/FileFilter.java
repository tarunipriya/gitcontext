package io.github.tarunipriya.gitcontext.ingestion;

import io.github.tarunipriya.gitcontext.config.GitContextProperties;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Evaluates candidate files to determine if they meet ingestion criteria.
 *
 * <p>Filtering rules include:
 * <ul>
 *   <li>Path prefix exclusion from repository root (e.g. {@code eval/})</li>
 *   <li>Directory exclusion (e.g. {@code node_modules}, {@code .git}, {@code target})</li>
 *   <li>Lock file exclusion (e.g. {@code package-lock.json}, {@code yarn.lock})</li>
 *   <li>Minified asset exclusion (e.g. {@code *.min.js}, {@code *.min.css})</li>
 *   <li>Extension whitelist matching (configured code and documentation extensions)</li>
 *   <li>File size bounds (below {@code maxFileSizeKb})</li>
 *   <li>Binary content detection (presence of NUL byte {@code 0x00} in the first 8 KB)</li>
 * </ul>
 */
@Component
public class FileFilter {

	private static final int BINARY_CHECK_BUFFER_SIZE = 8192;

	private final GitContextProperties.IngestionProperties properties;
	private final List<String> excludePathPrefixesLower;
	private final Set<String> excludeDirsLower;
	private final Set<String> excludeLockFilesLower;
	private final Set<String> allowedExtensionsLower;
	private final List<PathMatcher> minifiedPathMatchers;

	/**
	 * Creates a new {@link FileFilter} with configured ingestion rules.
	 *
	 * @param properties GitContext properties
	 */
	public FileFilter(GitContextProperties properties) {
		Objects.requireNonNull(properties, "properties must not be null");
		this.properties = properties.ingestion();

		List<String> prefixes = new ArrayList<>();
		if (this.properties.excludePathPrefixes() != null) {
			for (String prefix : this.properties.excludePathPrefixes()) {
				if (prefix != null && !prefix.isBlank()) {
					String normalized = prefix.replace('\\', '/').trim();
					while (normalized.startsWith("./") || normalized.startsWith("/")) {
						if (normalized.startsWith("./")) {
							normalized = normalized.substring(2);
						} else {
							normalized = normalized.substring(1);
						}
					}
					if (!normalized.isEmpty()) {
						prefixes.add(normalized.toLowerCase(Locale.ROOT));
					}
				}
			}
		}
		this.excludePathPrefixesLower = List.copyOf(prefixes);

		this.excludeDirsLower = new HashSet<>();
		for (String dir : this.properties.excludeDirs()) {
			this.excludeDirsLower.add(dir.toLowerCase(Locale.ROOT));
		}

		this.excludeLockFilesLower = new HashSet<>();
		for (String lockFile : this.properties.excludeLockFiles()) {
			this.excludeLockFilesLower.add(lockFile.toLowerCase(Locale.ROOT));
		}

		this.allowedExtensionsLower = new HashSet<>();
		for (String ext : this.properties.codeExtensions()) {
			this.allowedExtensionsLower.add(ext.toLowerCase(Locale.ROOT));
		}
		for (String ext : this.properties.docExtensions()) {
			this.allowedExtensionsLower.add(ext.toLowerCase(Locale.ROOT));
		}

		FileSystem fs = FileSystems.getDefault();
		List<PathMatcher> matchers = new ArrayList<>();
		if (this.properties.excludeMinifiedPatterns() != null) {
			for (String pattern : this.properties.excludeMinifiedPatterns()) {
				if (pattern != null && !pattern.isBlank()) {
					String trimmed = pattern.trim();
					matchers.add(fs.getPathMatcher("glob:" + trimmed));
					String lower = trimmed.toLowerCase(Locale.ROOT);
					if (!lower.equals(trimmed)) {
						matchers.add(fs.getPathMatcher("glob:" + lower));
					}
				}
			}
		}
		this.minifiedPathMatchers = List.copyOf(matchers);
	}

	/**
	 * Determines whether the given repository-relative path matches any exclusion criteria.
	 *
	 * <p>Exclusion criteria checked by path include:
	 * <ul>
	 *   <li>Path prefix exclusion from repository root (e.g. {@code eval/})</li>
	 *   <li>Directory exclusion (e.g. {@code node_modules}, {@code .git}, {@code target})</li>
	 *   <li>Lock file exclusion (e.g. {@code package-lock.json}, {@code yarn.lock})</li>
	 *   <li>Minified asset patterns (e.g. {@code *.min.js}, {@code *.min.css})</li>
	 * </ul>
	 *
	 * @param repoRelativePath relative path to the candidate file from repository root
	 * @return {@code true} if the path is excluded by prefix, directory, lock file, or minified pattern
	 */
	public boolean isExcludedPath(String repoRelativePath) {
		if (repoRelativePath == null || repoRelativePath.isBlank()) {
			return true;
		}

		String normalizedPosix = repoRelativePath.replace('\\', '/').trim();
		while (normalizedPosix.startsWith("./") || normalizedPosix.startsWith("/")) {
			if (normalizedPosix.startsWith("./")) {
				normalizedPosix = normalizedPosix.substring(2);
			} else {
				normalizedPosix = normalizedPosix.substring(1);
			}
		}
		if (normalizedPosix.isEmpty()) {
			return true;
		}
		String normalizedPosixLower = normalizedPosix.toLowerCase(Locale.ROOT);

		// 0. Check path prefix exclusion from repository root (e.g. eval/)
		for (String prefix : excludePathPrefixesLower) {
			if (normalizedPosixLower.startsWith(prefix)) {
				return true;
			}
		}

		String[] segments = normalizedPosix.split("/");

		// 1. Check excluded directory segments (all parent segments)
		for (int i = 0; i < segments.length - 1; i++) {
			if (excludeDirsLower.contains(segments[i].toLowerCase(Locale.ROOT))) {
				return true;
			}
		}

		String fileName = segments[segments.length - 1];
		String fileNameLower = fileName.toLowerCase(Locale.ROOT);

		// 2. Check lock files
		if (excludeLockFilesLower.contains(fileNameLower)) {
			return true;
		}

		// 3. Check minified patterns
		Path fileNamePath = Path.of(fileName);
		Path fileNameLowerPath = Path.of(fileNameLower);
		Path relPath = Path.of(normalizedPosix);
		for (PathMatcher matcher : minifiedPathMatchers) {
			if (matcher.matches(fileNamePath)
					|| matcher.matches(fileNameLowerPath)
					|| matcher.matches(relPath)) {
				return true;
			}
		}

		return false;
	}

	/**
	 * Determines whether the given file should be ingested.
	 *
	 * @param repoRoot root directory of the repository
	 * @param filePath absolute or relative path to the candidate file
	 * @return {@code true} if the file passes all inclusion filters
	 */
	public boolean shouldIngest(Path repoRoot, Path filePath) {
		if (filePath == null || !Files.isRegularFile(filePath)) {
			return false;
		}

		Path relativePath = repoRoot != null && filePath.isAbsolute()
				? repoRoot.relativize(filePath)
				: filePath;

		String posixPath = relativePath.toString().replace('\\', '/');
		if (isExcludedPath(posixPath)) {
			return false;
		}

		String fileName = filePath.getFileName() != null ? filePath.getFileName().toString() : "";

		// 4. Check allowed file extensions
		String extension = LanguageDetector.extractExtension(fileName).toLowerCase(Locale.ROOT);
		if (!allowedExtensionsLower.contains(extension)) {
			return false;
		}

		// 5. Check file size
		try {
			long sizeInBytes = Files.size(filePath);
			long maxBytes = (long) properties.maxFileSizeKb() * 1024L;
			if (sizeInBytes > maxBytes) {
				return false;
			}
		} catch (IOException e) {
			return false;
		}

		// 6. Check binary detection (first 8 KB)
		return !isBinary(filePath);
	}

	private static boolean isBinary(Path file) {
		byte[] buffer = new byte[BINARY_CHECK_BUFFER_SIZE];
		try (InputStream in = Files.newInputStream(file)) {
			int read = in.read(buffer);
			for (int i = 0; i < read; i++) {
				if (buffer[i] == 0) {
					return true;
				}
			}
		} catch (IOException e) {
			return true;
		}
		return false;
	}
}
