package io.github.tarunipriya.gitcontext.generation;

import io.github.tarunipriya.gitcontext.indexing.SearchHit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Parses and verifies citation markers in LLM-generated answers.
 *
 * <p>Identifies citation markers such as {@code [1]}, {@code [1][2]}, {@code [1, 3]}, and {@code [1-3]},
 * checks each reference against available source indexes, strips invalid citation references from the text,
 * marks sources as cited, and compiles an ordered list of citations by first appearance.
 */
@Component
public class CitationParser {

	// Matches bracketed numbers, comma-lists, or dash-ranges, e.g. [1], [1, 3], [1-3], [1 - 2]
	// Negative lookahead prevents matching Markdown links like [1](http://...)
	private static final Pattern CITATION_PATTERN = Pattern.compile("\\[\\s*(\\d+(?:\\s*[,-]\\s*\\d+)*)\\s*\\](?!\\s*\\()");

	public record CitationParseResult(
			String cleanedAnswer,
			List<Citation> citations,
			List<Source> updatedSources,
			int invalidCitationsCount
	) {
	}

	/**
	 * Parses citations from the answer text, validates them against the provided sources,
	 * removes invalid markers, and returns the cleaned text, citation list, and updated sources.
	 *
	 * @param rawAnswer generated text from LLM
	 * @param sources numbered sources provided in context
	 * @return parse result with cleaned text and verified citations
	 */
	public CitationParseResult parse(String rawAnswer, List<Source> sources) {
		if (rawAnswer == null || rawAnswer.isBlank()) {
			return new CitationParseResult("", List.of(), sources != null ? List.copyOf(sources) : List.of(), 0);
		}

		List<Source> sourceList = (sources != null) ? new ArrayList<>(sources) : new ArrayList<>();
		int sourceCount = sourceList.size();

		Set<Integer> citedSourceNumbers = new LinkedHashSet<>();
		int[] invalidCount = new int[1];

		Matcher matcher = CITATION_PATTERN.matcher(rawAnswer);
		StringBuilder cleanedSb = new StringBuilder();

		while (matcher.find()) {
			String group = matcher.group(1);
			List<Integer> numbers = parseNumbers(group);

			List<Integer> validNumbers = new ArrayList<>();
			for (int num : numbers) {
				if (num >= 1 && num <= sourceCount) {
					validNumbers.add(num);
					citedSourceNumbers.add(num);
				} else {
					invalidCount[0]++;
				}
			}

			if (validNumbers.isEmpty()) {
				// Entire marker was invalid -> replace with empty string
				matcher.appendReplacement(cleanedSb, "");
			} else if (validNumbers.size() == numbers.size()) {
				// All were valid -> keep original matched string
				matcher.appendReplacement(cleanedSb, Matcher.quoteReplacement(matcher.group(0)));
			} else {
				// Subset valid -> format only the valid ones
				String reconstructed = formatNumbers(validNumbers);
				matcher.appendReplacement(cleanedSb, Matcher.quoteReplacement(reconstructed));
			}
		}
		matcher.appendTail(cleanedSb);

		String cleanedText = cleanedSb.toString();
		// Clean up trailing whitespace before punctuation caused by removed citations
		cleanedText = cleanedText.replaceAll("\\s+([.,;:!?])", "$1");
		cleanedText = cleanedText.replaceAll("[ \\t]{2,}", " ").trim();

		// Build citations list in order of first use
		List<Citation> citations = new ArrayList<>();
		for (int num : citedSourceNumbers) {
			Source src = sourceList.get(num - 1);
			SearchHit hit = src.hit();
			citations.add(new Citation(
					num,
					hit.path(),
					hit.startLine(),
					hit.endLine(),
					hit.symbolName(),
					hit.url(),
					hit.chunkId()
			));
		}

		// Update cited flags on sources
		List<Source> updatedSources = new ArrayList<>(sourceList.size());
		for (Source src : sourceList) {
			boolean isCited = citedSourceNumbers.contains(src.number());
			updatedSources.add(src.withCited(isCited));
		}

		return new CitationParseResult(cleanedText, citations, updatedSources, invalidCount[0]);
	}

	private List<Integer> parseNumbers(String raw) {
		List<Integer> result = new ArrayList<>();
		String[] tokens = raw.split(",");
		for (String token : tokens) {
			String trimmed = token.trim();
			if (trimmed.isEmpty()) {
				continue;
			}
			if (trimmed.contains("-")) {
				String[] rangeParts = trimmed.split("-");
				if (rangeParts.length == 2) {
					try {
						int start = Integer.parseInt(rangeParts[0].trim());
						int end = Integer.parseInt(rangeParts[1].trim());
						if (start <= end && (end - start) < 50) {
							for (int i = start; i <= end; i++) {
								result.add(i);
							}
							continue;
						}
					} catch (NumberFormatException ignored) {
					}
				}
			}
			try {
				result.add(Integer.parseInt(trimmed));
			} catch (NumberFormatException ignored) {
			}
		}
		return result;
	}

	private String formatNumbers(List<Integer> numbers) {
		StringBuilder sb = new StringBuilder("[");
		for (int i = 0; i < numbers.size(); i++) {
			if (i > 0) {
				sb.append(", ");
			}
			sb.append(numbers.get(i));
		}
		sb.append("]");
		return sb.toString();
	}
}
