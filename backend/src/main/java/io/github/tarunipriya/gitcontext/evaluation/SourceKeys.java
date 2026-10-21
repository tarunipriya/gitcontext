package io.github.tarunipriya.gitcontext.evaluation;

import io.github.tarunipriya.gitcontext.indexing.SearchHit;
import io.github.tarunipriya.gitcontext.model.DocumentMetadata;
import io.github.tarunipriya.gitcontext.model.SourceType;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Derives and normalizes canonical evaluation source keys across repository files and Git history entities.
 * <p>
 * Ground-truth evaluation cases and retrieved search hits identify documents by source keys:
 * <ul>
 *   <li>File paths (e.g. {@code "backend/src/main/App.java"} normalized via {@link RetrievalMetrics#normalizePath})</li>
 *   <li>Commits (e.g. {@code "commit:abc1234"}, normalized to the first 7 lowercase hex characters)</li>
 *   <li>Pull requests (e.g. {@code "pr:16"})</li>
 *   <li>Issues (e.g. {@code "issue:3"})</li>
 * </ul>
 */
public final class SourceKeys {

	private static final Pattern PR_URL_PATTERN = Pattern.compile("/pull/(\\d+)");
	private static final Pattern ISSUE_URL_PATTERN = Pattern.compile("/issues/(\\d+)");
	private static final Pattern SHA_PATTERN = Pattern.compile("^[0-9a-fA-F]{7,}$");

	private SourceKeys() {
		// Utility class; prevent instantiation
	}

	/**
	 * Derives the canonical source key from ingested {@link DocumentMetadata}.
	 *
	 * @param m document metadata (nullable)
	 * @return canonical source key string, or null if no key can be determined
	 */
	public static String of(DocumentMetadata m) {
		if (m == null) {
			return null;
		}
		if (m.path() != null && !m.path().isBlank()) {
			return RetrievalMetrics.normalizePath(m.path());
		}
		if (m.sourceType() == SourceType.COMMIT) {
			String sha = m.commitSha();
			if (sha != null) {
				String cleanSha = sha.trim();
				if (cleanSha.length() >= 7) {
					return "commit:" + cleanSha.substring(0, 7).toLowerCase(Locale.ROOT);
				}
			}
			return null;
		}
		if (m.sourceType() == SourceType.PULL_REQUEST) {
			if (m.prNumber() != null && m.prNumber() > 0) {
				return "pr:" + m.prNumber();
			}
			if (m.url() != null) {
				Matcher matcher = PR_URL_PATTERN.matcher(m.url());
				if (matcher.find()) {
					return "pr:" + matcher.group(1);
				}
			}
			return null;
		}
		if (m.sourceType() == SourceType.ISSUE) {
			if (m.issueNumber() != null && m.issueNumber() > 0) {
				return "issue:" + m.issueNumber();
			}
			if (m.url() != null) {
				Matcher matcher = ISSUE_URL_PATTERN.matcher(m.url());
				if (matcher.find()) {
					return "issue:" + matcher.group(1);
				}
			}
			return null;
		}
		return null;
	}

	/**
	 * Derives the canonical source key from a retrieval {@link SearchHit}.
	 *
	 * @param h search hit (nullable)
	 * @return canonical source key string, or null if no key can be determined
	 */
	public static String of(SearchHit h) {
		if (h == null) {
			return null;
		}
		if (h.path() != null && !h.path().isBlank()) {
			return RetrievalMetrics.normalizePath(h.path());
		}
		if (h.sourceType() == SourceType.COMMIT) {
			String sha = h.commitSha();
			if (sha != null) {
				String cleanSha = sha.trim();
				if (cleanSha.length() >= 7) {
					return "commit:" + cleanSha.substring(0, 7).toLowerCase(Locale.ROOT);
				}
			}
			return null;
		}
		if (h.sourceType() == SourceType.PULL_REQUEST) {
			if (h.url() != null) {
				Matcher matcher = PR_URL_PATTERN.matcher(h.url());
				if (matcher.find()) {
					return "pr:" + matcher.group(1);
				}
			}
			return null;
		}
		if (h.sourceType() == SourceType.ISSUE) {
			if (h.url() != null) {
				Matcher matcher = ISSUE_URL_PATTERN.matcher(h.url());
				if (matcher.find()) {
					return "issue:" + matcher.group(1);
				}
			}
			return null;
		}
		return null;
	}

	/**
	 * Normalizes an expected source key from an evaluation dataset case.
	 * <ul>
	 *   <li>File paths are normalized via {@link RetrievalMetrics#normalizePath(String)}.</li>
	 *   <li>{@code "commit:<sha>"} prefixes are normalized to lowercase {@code "commit:"} followed by the first 7 lowercase hex characters.</li>
	 *   <li>{@code "pr:<n>"} and {@code "issue:<n>"} are trimmed and normalized to lowercase prefixes with integer numbers (e.g. {@code "PR:16"} &rarr; {@code "pr:16"}).</li>
	 * </ul>
	 *
	 * @param key expected source key or file path string
	 * @return normalized canonical source key
	 * @throws NullPointerException if {@code key} is null
	 * @throws IllegalArgumentException if {@code key} is blank or an invalid form (e.g. {@code "pr:abc"}, {@code "commit:short"})
	 */
	public static String normalizeExpected(String key) {
		Objects.requireNonNull(key, "key must not be null");
		String trimmed = key.trim();
		if (trimmed.isEmpty()) {
			throw new IllegalArgumentException("Expected source key must not be blank");
		}
		String lower = trimmed.toLowerCase(Locale.ROOT);
		if (lower.startsWith("pr:")) {
			String rest = trimmed.substring(trimmed.indexOf(':') + 1).trim();
			if (!rest.matches("\\d+")) {
				throw new IllegalArgumentException("Invalid PR source key: '" + key + "'. Expected format 'pr:<number>'");
			}
			try {
				int number = Integer.parseInt(rest);
				if (number <= 0) {
					throw new IllegalArgumentException("Invalid PR source key: '" + key + "'. PR number must be positive");
				}
				return "pr:" + number;
			} catch (NumberFormatException e) {
				throw new IllegalArgumentException("Invalid PR source key: '" + key + "'", e);
			}
		}
		if (lower.startsWith("issue:")) {
			String rest = trimmed.substring(trimmed.indexOf(':') + 1).trim();
			if (!rest.matches("\\d+")) {
				throw new IllegalArgumentException("Invalid issue source key: '" + key + "'. Expected format 'issue:<number>'");
			}
			try {
				int number = Integer.parseInt(rest);
				if (number <= 0) {
					throw new IllegalArgumentException("Invalid issue source key: '" + key + "'. Issue number must be positive");
				}
				return "issue:" + number;
			} catch (NumberFormatException e) {
				throw new IllegalArgumentException("Invalid issue source key: '" + key + "'", e);
			}
		}
		if (lower.startsWith("commit:")) {
			String rest = trimmed.substring(trimmed.indexOf(':') + 1).trim();
			if (!SHA_PATTERN.matcher(rest).matches()) {
				throw new IllegalArgumentException(
						"Invalid commit source key: '" + key + "'. SHA must have at least 7 hexadecimal characters");
			}
			return "commit:" + rest.substring(0, 7).toLowerCase(Locale.ROOT);
		}
		return RetrievalMetrics.normalizePath(trimmed);
	}
}
