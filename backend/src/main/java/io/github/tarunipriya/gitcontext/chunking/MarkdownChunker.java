package io.github.tarunipriya.gitcontext.chunking;

import io.github.tarunipriya.gitcontext.config.GitContextProperties;
import io.github.tarunipriya.gitcontext.model.Chunk;
import io.github.tarunipriya.gitcontext.model.ChunkMetadata;
import io.github.tarunipriya.gitcontext.model.ChunkType;
import io.github.tarunipriya.gitcontext.model.Document;
import io.github.tarunipriya.gitcontext.model.DocumentMetadata;
import io.github.tarunipriya.gitcontext.model.SourceType;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Splits Markdown documentation files into semantic section chunks delineated by ATX headings (#..######).
 *
 * <p>Maintains heading hierarchy paths (e.g. "Getting Started > Windows"), ignores '#' lines inside fenced
 * code blocks (``` or ~~~), merges small sections below {@code minChunkTokens}, and slices large sections
 * into overlapping line windows with {@code overlapLines} overlap.
 */
@Component
public class MarkdownChunker implements Chunker {

	private static final Pattern CODE_FENCE_PATTERN = Pattern.compile("^\\s*(`{3,}|~{3,}).*$");
	private static final Pattern ATX_HEADING_PATTERN = Pattern.compile("^\\s*(#{1,6})(?:\\s+(.*))?$");

	private final GitContextProperties properties;
	private final TokenCounter tokenCounter;
	private final ContextHeaderBuilder headerBuilder;

	public MarkdownChunker(
			GitContextProperties properties,
			TokenCounter tokenCounter,
			ContextHeaderBuilder headerBuilder
	) {
		this.properties = Objects.requireNonNull(properties, "properties must not be null");
		this.tokenCounter = Objects.requireNonNull(tokenCounter, "tokenCounter must not be null");
		this.headerBuilder = Objects.requireNonNull(headerBuilder, "headerBuilder must not be null");
	}

	@Override
	public boolean supports(Document document) {
		if (document == null || document.metadata() == null) {
			return false;
		}
		DocumentMetadata meta = document.metadata();
		if (meta.sourceType() != SourceType.DOC) {
			return false;
		}
		String lang = meta.language();
		if (lang != null && (lang.equalsIgnoreCase("markdown") || lang.equalsIgnoreCase("md"))) {
			return true;
		}
		String path = meta.path();
		return path != null && (path.endsWith(".md") || path.endsWith(".markdown"));
	}

	@Override
	public List<Chunk> chunk(Document document) {
		Objects.requireNonNull(document, "document must not be null");
		if (document.content() == null || document.content().isBlank()) {
			return List.of();
		}

		List<String> allLines = document.content().lines().toList();
		if (allLines.isEmpty()) {
			return List.of();
		}

		List<RawSection> rawSections = parseSections(allLines, document.metadata());
		if (rawSections.isEmpty()) {
			return List.of();
		}

		int minTokens = properties.chunking().minChunkTokens();
		int maxTokens = properties.chunking().maxChunkTokens();
		int overlapLines = properties.chunking().overlapLines();

		List<RawSection> mergedSections = mergeSmallSections(rawSections, minTokens);

		List<Chunk> chunks = new ArrayList<>();
		int chunkIndex = 0;
		DocumentMetadata docMeta = document.metadata();

		for (RawSection sec : mergedSections) {
			int tokens = tokenCounter.countTokens(sec.content());
			if (tokens <= maxTokens) {
				String chunkContent = sec.content();
				String header = headerBuilder.buildFileHeader(
						docMeta.path(),
						docMeta.language(),
						null,
						null,
						sec.headingPath(),
						sec.startLine(),
						sec.endLine()
				);
				String text = header.isEmpty() ? chunkContent : header + "\n\n" + chunkContent;
				ChunkMetadata meta = ChunkMetadata.from(
						docMeta,
						ChunkType.SECTION,
						tokenCounter.countTokens(text),
						null,
						null,
						sec.headingPath(),
						sec.startLine(),
						sec.endLine()
				);
				chunks.add(Chunk.of(document.id(), chunkIndex++, text, chunkContent, meta));
			} else {
				// Split into overlapping line windows keeping headingPath
				List<String> secLines = sec.lines();
				int start = 0;
				while (start < secLines.size()) {
					int end = start;
					StringBuilder sb = new StringBuilder();
					while (end < secLines.size()) {
						String line = secLines.get(end);
						String candidate = (sb.length() == 0) ? line : sb + "\n" + line;
						if (sb.length() > 0 && tokenCounter.countTokens(candidate) > maxTokens) {
							break;
						}
						sb.append(sb.length() == 0 ? "" : "\n").append(line);
						end++;
					}
					if (end == start) {
						sb.append(secLines.get(start));
						end = start + 1;
					}

					int windowStartLine = sec.startLine() + start;
					int windowEndLine = sec.startLine() + end - 1;
					String chunkContent = sb.toString();
					String header = headerBuilder.buildFileHeader(
							docMeta.path(),
							docMeta.language(),
							null,
							null,
							sec.headingPath(),
							windowStartLine,
							windowEndLine
					);
					String text = header.isEmpty() ? chunkContent : header + "\n\n" + chunkContent;
					ChunkMetadata meta = ChunkMetadata.from(
							docMeta,
							ChunkType.SECTION,
							tokenCounter.countTokens(text),
							null,
							null,
							sec.headingPath(),
							windowStartLine,
							windowEndLine
					);
					chunks.add(Chunk.of(document.id(), chunkIndex++, text, chunkContent, meta));

					if (end >= secLines.size()) {
						break;
					}
					int nextStart = end - overlapLines;
					if (nextStart <= start) {
						nextStart = start + 1;
					}
					start = nextStart;
				}
			}
		}

		return chunks;
	}

	private List<RawSection> parseSections(List<String> allLines, DocumentMetadata metadata) {
		List<RawSection> sections = new ArrayList<>();
		String fileName = resolveFileName(metadata.path());

		String currentFence = null;
		String[] hierarchy = new String[7];

		int currentSectionStartLine = 1;
		String currentHeadingPath = fileName;
		List<String> currentSectionLines = new ArrayList<>();
		boolean hasSeenHeading = false;

		for (int i = 0; i < allLines.size(); i++) {
			String line = allLines.get(i);
			int lineNum = i + 1;

			// Check code fence boundary
			Matcher fenceMatcher = CODE_FENCE_PATTERN.matcher(line);
			if (fenceMatcher.matches()) {
				String fenceStr = fenceMatcher.group(1);
				if (currentFence == null) {
					currentFence = fenceStr;
				} else if (line.trim().startsWith(currentFence.substring(0, 3))) {
					currentFence = null;
				}
				currentSectionLines.add(line);
				continue;
			}

			// If inside code fence, '#' is not an ATX heading
			if (currentFence != null) {
				currentSectionLines.add(line);
				continue;
			}

			// Check ATX heading
			Matcher headingMatcher = ATX_HEADING_PATTERN.matcher(line);
			if (headingMatcher.matches()) {
				int level = headingMatcher.group(1).length();
				String rawTitle = headingMatcher.group(2);
				String title = (rawTitle != null) ? rawTitle.replaceAll("#+\\s*$", "").trim() : "";

				// Save existing section if it has lines
				if (!currentSectionLines.isEmpty()) {
					sections.add(new RawSection(
							currentHeadingPath,
							currentSectionStartLine,
							lineNum - 1,
							List.copyOf(currentSectionLines)
					));
					currentSectionLines.clear();
				}

				// Update heading hierarchy
				hierarchy[level] = title;
				for (int k = level + 1; k <= 6; k++) {
					hierarchy[k] = null;
				}
				currentHeadingPath = buildHeadingPath(hierarchy);
				currentSectionStartLine = lineNum;
				hasSeenHeading = true;
			}

			currentSectionLines.add(line);
		}

		if (!currentSectionLines.isEmpty()) {
			sections.add(new RawSection(
					currentHeadingPath,
					currentSectionStartLine,
					allLines.size(),
					List.copyOf(currentSectionLines)
			));
		}

		return sections;
	}

	private String buildHeadingPath(String[] hierarchy) {
		List<String> parts = new ArrayList<>();
		for (int i = 1; i <= 6; i++) {
			if (hierarchy[i] != null && !hierarchy[i].isBlank()) {
				parts.add(hierarchy[i]);
			}
		}
		return parts.isEmpty() ? "Section" : String.join(" > ", parts);
	}

	private List<RawSection> mergeSmallSections(List<RawSection> rawSections, int minTokens) {
		List<RawSection> merged = new ArrayList<>();
		RawSection pending = null;

		for (int i = 0; i < rawSections.size(); i++) {
			RawSection s = rawSections.get(i);
			if (pending != null) {
				s = merge(pending, s, s.headingPath());
				pending = null;
			}
			int tokens = tokenCounter.countTokens(s.content());
			if (tokens < minTokens) {
				if (i + 1 < rawSections.size()) {
					pending = s;
				} else {
					if (!merged.isEmpty()) {
						RawSection prev = merged.remove(merged.size() - 1);
						merged.add(merge(prev, s, prev.headingPath()));
					} else {
						merged.add(s);
					}
				}
			} else {
				merged.add(s);
			}
		}

		if (pending != null) {
			if (!merged.isEmpty()) {
				RawSection prev = merged.remove(merged.size() - 1);
				merged.add(merge(prev, pending, prev.headingPath()));
			} else {
				merged.add(pending);
			}
		}

		return merged;
	}

	private RawSection merge(RawSection first, RawSection second, String headingPath) {
		List<String> combinedLines = new ArrayList<>(first.lines());
		combinedLines.addAll(second.lines());
		return new RawSection(
				headingPath,
				first.startLine(),
				second.endLine(),
				List.copyOf(combinedLines)
		);
	}

	private String resolveFileName(String path) {
		if (path == null || path.isBlank()) {
			return "README.md";
		}
		String clean = path.replace('\\', '/');
		int idx = clean.lastIndexOf('/');
		return idx >= 0 ? clean.substring(idx + 1) : clean;
	}

	private record RawSection(
			String headingPath,
			int startLine,
			int endLine,
			List<String> lines
	) {
		String content() {
			return String.join("\n", lines);
		}
	}
}
