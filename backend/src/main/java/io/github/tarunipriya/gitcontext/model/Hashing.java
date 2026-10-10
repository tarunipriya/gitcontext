package io.github.tarunipriya.gitcontext.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Cryptographic hashing utility for generating stable identifiers and content checksums.
 */
public final class Hashing {

	private static final String SHA_256 = "SHA-256";

	private Hashing() {
		// Utility class
	}

	/**
	 * Computes the SHA-256 hex digest of the given UTF-8 string.
	 *
	 * @param input the string to hash (must not be null)
	 * @return a 64-character lowercase hexadecimal hash string
	 */
	public static String sha256(String input) {
		Objects.requireNonNull(input, "input to hash must not be null");
		try {
			MessageDigest digest = MessageDigest.getInstance(SHA_256);
			byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(hash);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 algorithm not available in current JVM", e);
		}
	}
}
