package io.github.tarunipriya.gitcontext.evaluation.runner;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Objects;

/**
 * Evaluation variant pairing a descriptive label and execution {@link EvalMode}
 * with an optional keyword score weight multiplier.
 *
 * @param label formatted, normalized variant label (e.g. "VECTOR", "HYBRID:kw=0.5")
 * @param mode underlying evaluation search mode (retrieval strategy and reranking behavior)
 * @param keywordWeight optional reciprocal rank keyword weight multiplier (null indicates config default)
 */
public record EvalVariant(
		@JsonValue String label,
		EvalMode mode,
		Double keywordWeight
) {

	private static final String VALID_FORMAT_DESCRIPTION =
			"VECTOR, KEYWORD, HYBRID[:kw=<number>], HYBRID_RERANK[:kw=<number>]";

	public static final EvalVariant VECTOR = new EvalVariant("VECTOR", EvalMode.VECTOR, null);
	public static final EvalVariant KEYWORD = new EvalVariant("KEYWORD", EvalMode.KEYWORD, null);
	public static final EvalVariant HYBRID = new EvalVariant("HYBRID", EvalMode.HYBRID, null);
	public static final EvalVariant HYBRID_RERANK = new EvalVariant("HYBRID_RERANK", EvalMode.HYBRID_RERANK, null);

	public EvalVariant {
		Objects.requireNonNull(label, "label must not be null");
		Objects.requireNonNull(mode, "mode must not be null");
		if (keywordWeight != null) {
			if (!Double.isFinite(keywordWeight) || keywordWeight < 0) {
				throw new IllegalArgumentException("keywordWeight must be finite and >= 0, got " + keywordWeight);
			}
		}
	}

	/**
	 * Parses an evaluation variant descriptor case-insensitively.
	 *
	 * <p>Accepts base variants {@code "VECTOR"}, {@code "KEYWORD"}, {@code "HYBRID"}, and {@code "HYBRID_RERANK"}.
	 * For {@code HYBRID} and {@code HYBRID_RERANK} only, an optional suffix {@code ":kw=<number>"} may be provided,
	 * specifying a custom keyword reciprocal rank weight (e.g. {@code "HYBRID:kw=0.5"}).
	 *
	 * @param value raw variant descriptor string
	 * @return parsed {@link EvalVariant}
	 * @throws IllegalArgumentException if the descriptor is null, blank, has an invalid suffix, or is unknown
	 */
	@JsonCreator
	public static EvalVariant parse(String value) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(
					"Evaluation variant must not be null or blank. Valid formats: " + VALID_FORMAT_DESCRIPTION);
		}
		String trimmed = value.trim();
		int colonIdx = trimmed.indexOf(':');

		if (colonIdx < 0) {
			String upper = trimmed.toUpperCase();
			return switch (upper) {
				case "VECTOR" -> VECTOR;
				case "KEYWORD" -> KEYWORD;
				case "HYBRID" -> HYBRID;
				case "HYBRID_RERANK" -> HYBRID_RERANK;
				default -> throw new IllegalArgumentException(
						"Unknown evaluation variant: '" + value + "'. Valid formats: " + VALID_FORMAT_DESCRIPTION);
			};
		}

		String base = trimmed.substring(0, colonIdx).trim().toUpperCase();
		String suffix = trimmed.substring(colonIdx + 1).trim();

		if (!base.equals("HYBRID") && !base.equals("HYBRID_RERANK")) {
			throw new IllegalArgumentException(
					"Suffix ':kw=' is only supported on HYBRID and HYBRID_RERANK, got: '" + value
							+ "'. Valid formats: " + VALID_FORMAT_DESCRIPTION);
		}

		if (!suffix.toLowerCase().startsWith("kw=")) {
			throw new IllegalArgumentException(
					"Invalid variant suffix in: '" + value + "'. Expected ':kw=<number>'. Valid formats: "
							+ VALID_FORMAT_DESCRIPTION);
		}

		String numStr = suffix.substring(3).trim();
		if (numStr.isEmpty()) {
			throw new IllegalArgumentException(
					"Keyword weight number is missing in: '" + value + "'. Valid formats: " + VALID_FORMAT_DESCRIPTION);
		}

		double weight;
		try {
			weight = Double.parseDouble(numStr);
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException(
					"Invalid keyword weight number in: '" + value + "': '" + numStr + "'. Expected a valid number >= 0", e);
		}

		if (!Double.isFinite(weight) || weight < 0) {
			throw new IllegalArgumentException(
					"Keyword weight must be finite and >= 0, got: " + weight + " in '" + value + "'");
		}

		EvalMode mode = EvalMode.valueOf(base);
		String normalizedLabel = base + ":kw=" + numStr;
		return new EvalVariant(normalizedLabel, mode, weight);
	}

	/**
	 * Alias for {@link #parse(String)} providing standard Java naming convention.
	 *
	 * @param value raw variant descriptor string
	 * @return parsed {@link EvalVariant}
	 * @throws IllegalArgumentException if the descriptor is invalid
	 */
	public static EvalVariant fromString(String value) {
		return parse(value);
	}

	@Override
	public String toString() {
		return label;
	}
}
