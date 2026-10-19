package io.github.tarunipriya.gitcontext.generation;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Loads system instruction prompts from classpath resources and formats user question context prompts.
 */
@Component
public class PromptTemplates {

	private static final String SYSTEM_PROMPT_PATH = "prompts/answer-system.txt";
	private final String systemInstruction;

	public PromptTemplates() {
		this.systemInstruction = loadSystemInstruction();
	}

	/**
	 * Returns the static system instruction guiding model persona and citation rules.
	 *
	 * @return system instruction string
	 */
	public String systemInstruction() {
		return systemInstruction;
	}

	/**
	 * Formats the user prompt combining indexed source contexts with the query.
	 *
	 * @param question user inquiry
	 * @param context formatted numbered sources
	 * @return composed user prompt
	 */
	public String buildUserPrompt(String question, String context) {
		Objects.requireNonNull(question, "question must not be null");
		String cleanContext = context != null ? context.trim() : "";

		StringBuilder sb = new StringBuilder();
		if (!cleanContext.isEmpty()) {
			sb.append("Sources:\n")
					.append(cleanContext)
					.append("\n\n");
		}
		sb.append("Question: ").append(question.trim());
		return sb.toString();
	}

	private String loadSystemInstruction() {
		ClassPathResource resource = new ClassPathResource(SYSTEM_PROMPT_PATH);
		try (InputStream is = resource.getInputStream()) {
			return new String(is.readAllBytes(), StandardCharsets.UTF_8).trim();
		} catch (IOException e) {
			throw new IllegalStateException("Failed to load prompt template from classpath: " + SYSTEM_PROMPT_PATH, e);
		}
	}
}
