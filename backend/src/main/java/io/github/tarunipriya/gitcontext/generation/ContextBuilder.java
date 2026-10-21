package io.github.tarunipriya.gitcontext.generation;

import io.github.tarunipriya.gitcontext.chunking.TokenCounter;
import io.github.tarunipriya.gitcontext.indexing.SearchHit;
import io.github.tarunipriya.gitcontext.model.ChunkType;
import io.github.tarunipriya.gitcontext.model.SourceType;
import io.github.tarunipriya.gitcontext.retrieval.HybridSearchHit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * Builds prompt context from search results enforcing token budgets, per-file caps,
 * line overlap suppression, and markdown code block formatting.
 */
@Component
public class ContextBuilder {

	public static final int MAX_CONTEXT_TOKENS = 6000;
	public static final int MAX_CHUNKS_PER_FILE = 3;

	public record ContextBuildResult(
			List<Source> sources,
			String formattedContext,
			int totalTokens
	) {
	}

	private record LineRange(int start, int end) {
		boolean overlaps(int otherStart, int otherEnd) {
			return Math.max(start, otherStart) <= Math.min(end, otherEnd);
		}
	}

	private final TokenCounter tokenCounter;

	public ContextBuilder(TokenCounter tokenCounter) {
		this.tokenCounter = Objects.requireNonNull(tokenCounter, "tokenCounter must not be null");
	}

	/**
	 * Selects and formats search hits into numbered sources within context constraints.
	 *
	 * @param hits candidate search hits
	 * @param maxSources maximum number of sources to select (1-12)
	 * @return formatted context and selected source objects
	 */
	public ContextBuildResult buildContext(List<HybridSearchHit> hits, int maxSources) {
		if (hits == null || hits.isEmpty() || maxSources <= 0) {
			return new ContextBuildResult(List.of(), "", 0);
		}

		int effectiveMaxSources = Math.min(maxSources, 12);
		List<Source> selectedSources = new ArrayList<>();
		StringBuilder contextBuilder = new StringBuilder();

		Map<String, Integer> fileCounts = new HashMap<>();
		Map<String, List<LineRange>> fileRanges = new HashMap<>();

		int currentTokens = 0;

		for (HybridSearchHit hitWrapper : hits) {
			if (selectedSources.size() >= effectiveMaxSources) {
				break;
			}

			SearchHit hit = hitWrapper.hit();
			if (hit == null) {
				continue;
			}

			String path = hit.path();
			String fileKey = (path != null && !path.isBlank()) ? path.toLowerCase() : null;

			// Per-file cap check
			if (fileKey != null) {
				int count = fileCounts.getOrDefault(fileKey, 0);
				if (count >= MAX_CHUNKS_PER_FILE) {
					continue;
				}

				// Line overlap check
				Integer startLine = hit.startLine();
				Integer endLine = hit.endLine();
				if (startLine != null && endLine != null) {
					List<LineRange> ranges = fileRanges.computeIfAbsent(fileKey, k -> new ArrayList<>());
					boolean overlaps = ranges.stream().anyMatch(r -> r.overlaps(startLine, endLine));
					if (overlaps) {
						continue;
					}
				}
			}

			int sourceNumber = selectedSources.size() + 1;
			String formattedBlock = formatSourceBlock(sourceNumber, hit);
			int blockTokens = tokenCounter.countTokens(formattedBlock);

			if (currentTokens + blockTokens > MAX_CONTEXT_TOKENS) {
				// Stop if budget is exceeded
				if (selectedSources.isEmpty()) {
					// If the very first source exceeds budget, include truncated or stop
					break;
				}
				break;
			}

			selectedSources.add(new Source(sourceNumber, hit, false));
			if (!contextBuilder.isEmpty()) {
				contextBuilder.append("\n\n");
			}
			contextBuilder.append(formattedBlock);
			currentTokens += blockTokens;

			if (fileKey != null) {
				fileCounts.put(fileKey, fileCounts.getOrDefault(fileKey, 0) + 1);
				if (hit.startLine() != null && hit.endLine() != null) {
					fileRanges.computeIfAbsent(fileKey, k -> new ArrayList<>())
							.add(new LineRange(hit.startLine(), hit.endLine()));
				}
			}
		}

		return new ContextBuildResult(selectedSources, contextBuilder.toString(), currentTokens);
	}

	private String formatSourceBlock(int number, SearchHit hit) {
		StringBuilder sb = new StringBuilder();
		String path = hit.path();
		Integer start = hit.startLine();
		Integer end = hit.endLine();
		ChunkType type = hit.chunkType();
		String symbol = hit.symbolName();
		String url = hit.url();
		SourceType sourceType = hit.sourceType();

		sb.append("[").append(number).append("] ");

		if (path != null && !path.isBlank()) {
			sb.append(path);
			if (start != null && end != null) {
				sb.append(" (lines ").append(start).append("-").append(end).append(")");
			} else if (start != null) {
				sb.append(" (line ").append(start).append(")");
			}
			if (type != null) {
				sb.append(", ").append(type);
				if (symbol != null && !symbol.isBlank()) {
					sb.append(" ").append(symbol);
				}
			}
		} else {
			String title = (symbol != null && !symbol.isBlank()) ? symbol : (hit.headingPath() != null ? hit.headingPath() : "Document");
			if (sourceType != null) {
				sb.append(sourceType).append(": ");
			}
			sb.append(title);
			if (url != null && !url.isBlank()) {
				sb.append(" - ").append(url);
			}
		}

		sb.append("\n");

		String lang = hit.language() != null ? hit.language().toLowerCase() : "";
		if (lang.isBlank() && path != null) {
			lang = inferLanguage(path);
		}

		String content = hit.content() != null ? hit.content() : "";
		sb.append("```").append(lang).append("\n");
		sb.append(content);
		if (!content.endsWith("\n")) {
			sb.append("\n");
		}
		sb.append("```");

		return sb.toString();
	}

	private String inferLanguage(String path) {
		int dot = path.lastIndexOf('.');
		if (dot == -1) {
			return "";
		}
		String ext = path.substring(dot + 1).toLowerCase();
		return switch (ext) {
			case "java" -> "java";
			case "py" -> "python";
			case "js" -> "javascript";
			case "ts" -> "typescript";
			case "tsx" -> "tsx";
			case "jsx" -> "jsx";
			case "md" -> "markdown";
			case "yml", "yaml" -> "yaml";
			case "json" -> "json";
			case "xml" -> "xml";
			case "html" -> "html";
			case "css" -> "css";
			case "sql" -> "sql";
			case "sh" -> "bash";
			default -> ext;
		};
	}
}
