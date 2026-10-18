package io.github.tarunipriya.gitcontext.model;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Strongly typed representation of a GitHub repository identifier ("owner/name").
 *
 * @param owner repository owner or organization
 * @param name repository name
 */
public record RepoRef(String owner, String name) {

	private static final Pattern REPO_PATTERN = Pattern.compile(
			"^(?:(?:https?://|git@)?github\\.com[/:]|/)?([a-zA-Z0-9_.-]+)/([a-zA-Z0-9_.-]+?)(?:\\.git)?/?$"
	);

	/**
	 * Compact constructor validating owner and name.
	 */
	public RepoRef {
		Objects.requireNonNull(owner, "owner must not be null");
		if (owner.isBlank()) {
			throw new IllegalArgumentException("owner must not be blank");
		}
		Objects.requireNonNull(name, "name must not be null");
		if (name.isBlank()) {
			throw new IllegalArgumentException("name must not be blank");
		}
	}

	/**
	 * Parses a string into a {@link RepoRef}.
	 *
	 * <p>Supported formats include:
	 * <ul>
	 *   <li>{@code "owner/name"}</li>
	 *   <li>{@code "https://github.com/owner/name"}</li>
	 *   <li>{@code "https://github.com/owner/name.git"}</li>
	 *   <li>{@code "http://github.com/owner/name"}</li>
	 *   <li>{@code "git@github.com:owner/name.git"}</li>
	 * </ul>
	 *
	 * @param input repository identifier or URL string
	 * @return a parsed {@link RepoRef}
	 * @throws IllegalArgumentException if the input is null, blank, or invalid
	 */
	public static RepoRef parse(String input) {
		if (input == null || input.isBlank()) {
			throw new IllegalArgumentException("Repository reference must not be null or blank");
		}
		String trimmed = input.trim();
		Matcher matcher = REPO_PATTERN.matcher(trimmed);
		if (!matcher.matches()) {
			throw new IllegalArgumentException("Invalid repository reference: '" + input +
					"'. Expected format 'owner/name' or 'https://github.com/owner/name'");
		}
		String owner = matcher.group(1);
		String name = matcher.group(2);
		return new RepoRef(owner, name);
	}

	/**
	 * Returns the canonical "owner/name" identifier.
	 *
	 * @return repository full name
	 */
	public String fullName() {
		return owner + "/" + name;
	}

	/**
	 * Returns the normalized lower-case repository identifier ("owner/name") used for case-insensitive lookups.
	 *
	 * @return normalized lower-case key
	 */
	public String normalizedKey() {
		return owner.toLowerCase(java.util.Locale.ROOT) + "/" + name.toLowerCase(java.util.Locale.ROOT);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof RepoRef other)) return false;
		return owner.equalsIgnoreCase(other.owner) && name.equalsIgnoreCase(other.name);
	}

	@Override
	public int hashCode() {
		return Objects.hash(owner.toLowerCase(java.util.Locale.ROOT), name.toLowerCase(java.util.Locale.ROOT));
	}

	/**
	 * Returns the HTTPS clone URL for GitHub.
	 *
	 * @return clone URL string ending with .git
	 */
	public String httpsCloneUrl() {
		return "https://github.com/" + fullName() + ".git";
	}

	/**
	 * Returns the canonical web URL for browsing the repository on GitHub.
	 *
	 * @return GitHub web URL
	 */
	public String webUrl() {
		return "https://github.com/" + fullName();
	}

	@Override
	public String toString() {
		return fullName();
	}
}
