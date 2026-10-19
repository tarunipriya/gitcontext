package io.github.tarunipriya.gitcontext.retrieval;

/**
 * Utility for detecting test-related source and test resource files based on standard naming conventions and paths.
 */
public final class TestFileFilter {

	private TestFileFilter() {
	}

	/**
	 * Returns {@code true} if the given file path matches test directory or test file suffix conventions.
	 *
	 * <p>Specifically, a path is considered a test file if:
	 * <ul>
	 *   <li>The normalised path contains {@code /test/} or {@code /tests/}</li>
	 *   <li>The normalised path ends with {@code Test.java}, {@code Tests.java}, {@code _test.py},
	 *       {@code .test.js}, {@code .test.ts}, {@code .spec.js}, or {@code .spec.ts}</li>
	 * </ul>
	 *
	 * @param path relative or absolute file path (may use either forward or backward slashes)
	 * @return true if the path represents a test file
	 */
	public static boolean isTestPath(String path) {
		if (path == null || path.isBlank()) {
			return false;
		}
		String normalized = path.replace('\\', '/');
		String checkPath = normalized.startsWith("/") ? normalized : "/" + normalized;
		if (checkPath.contains("/test/") || checkPath.contains("/tests/")) {
			return true;
		}
		return normalized.endsWith("Test.java")
				|| normalized.endsWith("Tests.java")
				|| normalized.endsWith("_test.py")
				|| normalized.endsWith(".test.js")
				|| normalized.endsWith(".test.ts")
				|| normalized.endsWith(".spec.js")
				|| normalized.endsWith(".spec.ts");
	}
}
