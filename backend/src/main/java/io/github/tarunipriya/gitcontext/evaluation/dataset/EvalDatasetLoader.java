package io.github.tarunipriya.gitcontext.evaluation.dataset;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Plain, Spring-free loader for reading and strictly validating evaluation datasets in JSON Lines (.jsonl) format.
 * <p>
 * File format specifications:
 * <ul>
 *   <li>One JSON object per line conforming to the schema of {@link EvalCase}</li>
 *   <li>Blank lines and comment lines (starting with {@code //}) are ignored</li>
 *   <li>UTF-8 text encoding</li>
 *   <li>Strict validation: unknown JSON fields, blank questions/IDs, duplicate IDs, or mismatched
 *       answerability constraints result in an {@link EvalDatasetException} referencing the exact line number</li>
 *   <li>Dataset name is derived from the file name without extension</li>
 * </ul>
 */
public final class EvalDatasetLoader {

	private static final JsonMapper JSON_MAPPER = JsonMapper.builder()
			.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
			.build();

	private EvalDatasetLoader() {
		// Utility class; prevent instantiation
	}

	/**
	 * Loads and validates an evaluation dataset from a JSON Lines (.jsonl) file.
	 *
	 * @param jsonlFile path to the .jsonl dataset file
	 * @return validated, immutable {@link EvalDataset}
	 * @throws NullPointerException if {@code jsonlFile} is null
	 * @throws EvalDatasetException if the file is missing, empty, contains invalid JSON,
	 *                              or violates dataset schema constraints
	 */
	public static EvalDataset load(Path jsonlFile) {
		Objects.requireNonNull(jsonlFile, "jsonlFile must not be null");

		if (!Files.exists(jsonlFile)) {
			throw new EvalDatasetException("Eval dataset file does not exist: " + jsonlFile);
		}
		if (!Files.isRegularFile(jsonlFile)) {
			throw new EvalDatasetException("Eval dataset path is not a regular file: " + jsonlFile);
		}

		String fileName = jsonlFile.getFileName() != null ? jsonlFile.getFileName().toString() : jsonlFile.toString();
		int dotIndex = fileName.lastIndexOf('.');
		String datasetName = (dotIndex > 0) ? fileName.substring(0, dotIndex) : fileName;

		List<EvalCase> cases = new ArrayList<>();
		Set<String> seenIds = new HashSet<>();

		try (BufferedReader reader = Files.newBufferedReader(jsonlFile, StandardCharsets.UTF_8)) {
			String line;
			int lineNumber = 0;
			while ((line = reader.readLine()) != null) {
				lineNumber++;
				String trimmed = line.trim();
				if (trimmed.isEmpty() || trimmed.startsWith("//")) {
					continue;
				}

				EvalCase evalCase;
				try {
					evalCase = JSON_MAPPER.readValue(line, EvalCase.class);
				} catch (Exception e) {
					IllegalArgumentException iae = findIllegalArgumentException(e);
					if (iae != null) {
						throw new EvalDatasetException(
								"Invalid source key in " + fileName + " at line " + lineNumber + ": " + iae.getMessage(), iae);
					}
					throw new EvalDatasetException(
							"Invalid JSON in " + fileName + " at line " + lineNumber + ": " + e.getMessage(), e);
				}

				if (evalCase.id() == null || evalCase.id().isBlank()) {
					throw new EvalDatasetException("Blank id in " + fileName + " at line " + lineNumber);
				}
				if (evalCase.question() == null || evalCase.question().isBlank()) {
					throw new EvalDatasetException("Blank question in " + fileName + " at line " + lineNumber);
				}
				if (!seenIds.add(evalCase.id())) {
					throw new EvalDatasetException(
							"Duplicate id '" + evalCase.id() + "' in " + fileName + " at line " + lineNumber);
				}
				if (evalCase.answerable() && evalCase.expectedPaths().isEmpty()) {
					throw new EvalDatasetException(
							"Answerable case has empty expectedPaths in " + fileName + " at line " + lineNumber);
				}
				if (!evalCase.answerable() && !evalCase.expectedPaths().isEmpty()) {
					throw new EvalDatasetException(
							"Unanswerable case has non-empty expectedPaths in " + fileName + " at line " + lineNumber);
				}

				cases.add(evalCase);
			}
		} catch (IOException e) {
			throw new EvalDatasetException("Failed to read eval dataset " + fileName + ": " + e.getMessage(), e);
		}

		if (cases.isEmpty()) {
			throw new EvalDatasetException("Empty dataset in " + fileName + ": no evaluation cases found");
		}

		return new EvalDataset(datasetName, cases);
	}

	private static IllegalArgumentException findIllegalArgumentException(Throwable t) {
		Throwable current = t;
		while (current != null) {
			if (current instanceof IllegalArgumentException iae) {
				return iae;
			}
			current = current.getCause();
		}
		return null;
	}
}
