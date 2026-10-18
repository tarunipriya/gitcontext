package io.github.tarunipriya.gitcontext.chunking.treesitter;

import io.github.tarunipriya.gitcontext.chunking.Chunker;
import io.github.tarunipriya.gitcontext.chunking.ContextHeaderBuilder;
import io.github.tarunipriya.gitcontext.chunking.LineWindowChunker;
import io.github.tarunipriya.gitcontext.chunking.TokenCounter;
import io.github.tarunipriya.gitcontext.config.GitContextProperties;
import io.github.tarunipriya.gitcontext.model.Chunk;
import io.github.tarunipriya.gitcontext.model.ChunkMetadata;
import io.github.tarunipriya.gitcontext.model.ChunkType;
import io.github.tarunipriya.gitcontext.model.Document;
import io.github.tarunipriya.gitcontext.model.DocumentMetadata;
import io.github.tarunipriya.gitcontext.model.SourceType;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.treesitter.TSNode;
import org.treesitter.TSParser;
import org.treesitter.TSTree;

/**
 * AST-aware code chunker leveraging Tree-sitter grammar parse trees.
 *
 * <p>Produces:
 * <ul>
 *   <li><b>MODULE chunks:</b> Uncovered top-level ranges (package declaration, imports, module-level statements).</li>
 *   <li><b>CLASS skeleton chunks:</b> One skeleton per container retaining fields, signatures, annotations, and nested
 *       type declarations while replacing callable bodies with {@code { ... }} (Python: {@code ...}).</li>
 *   <li><b>METHOD / FUNCTION chunks:</b> Semantic callable units with immediately preceding comments/Javadoc and
 *       annotations/decorators. Splits large callables exceeding {@code maxChunkTokens} into overlapping line windows,
 *       and merges consecutive small callables in the same container below {@code minChunkTokens}.</li>
 * </ul>
 *
 * <p>Operates strictly by line numbers (rows) to preserve multi-byte UTF-8 character and emoji safety.
 */
@Component
@Order(10)
public class CodeChunker implements Chunker {

	private static final Logger log = LoggerFactory.getLogger(CodeChunker.class);

	private final TreeSitterLanguageRegistry registry;
	private final GitContextProperties properties;
	private final TokenCounter tokenCounter;
	private final ContextHeaderBuilder headerBuilder;
	private final LineWindowChunker lineWindowChunker;

	/**
	 * Creates a new {@link CodeChunker}.
	 *
	 * @param registry grammar registry for Tree-sitter languages
	 * @param properties configuration properties for chunking limits
	 * @param tokenCounter token counter for sizing chunks
	 * @param headerBuilder context header builder for chunk headers
	 * @param lineWindowChunker fallback line-window chunker
	 */
	public CodeChunker(
			TreeSitterLanguageRegistry registry,
			GitContextProperties properties,
			TokenCounter tokenCounter,
			ContextHeaderBuilder headerBuilder,
			LineWindowChunker lineWindowChunker
	) {
		this.registry = Objects.requireNonNull(registry, "registry must not be null");
		this.properties = Objects.requireNonNull(properties, "properties must not be null");
		this.tokenCounter = Objects.requireNonNull(tokenCounter, "tokenCounter must not be null");
		this.headerBuilder = Objects.requireNonNull(headerBuilder, "headerBuilder must not be null");
		this.lineWindowChunker = Objects.requireNonNull(lineWindowChunker, "lineWindowChunker must not be null");
	}

	@Override
	public boolean supports(Document document) {
		if (document == null || document.metadata() == null) {
			return false;
		}
		DocumentMetadata meta = document.metadata();
		if (meta.sourceType() != SourceType.CODE) {
			return false;
		}
		String language = meta.language();
		return language != null && registry.supports(language);
	}

	@Override
	public List<Chunk> chunk(Document document) {
		Objects.requireNonNull(document, "document must not be null");
		if (document.content() == null || document.content().isBlank()) {
			return List.of();
		}

		List<String> lines = document.content().lines().toList();
		if (lines.isEmpty()) {
			return List.of();
		}

		String language = document.metadata().language();
		LanguageSpec spec = LanguageSpec.forLanguage(language);
		if (spec == null) {
			return lineWindowChunker.chunk(document);
		}

		try (TSParser parser = registry.createParser(language)) {
			if (parser == null) {
				log.debug("No parser available for language {}, delegating to LineWindowChunker", language);
				return lineWindowChunker.chunk(document);
			}
			try (TSTree tree = parser.parseString(null, document.content())) {
				if (tree == null || tree.getRootNode() == null || tree.getRootNode().isNull()) {
					log.debug("Parser returned null tree for {}, delegating to LineWindowChunker", document.metadata().path());
					return lineWindowChunker.chunk(document);
				}

				byte[] utf8Bytes = document.content().getBytes(StandardCharsets.UTF_8);
				return extractChunks(document, lines, tree.getRootNode(), spec, utf8Bytes);
			}
		} catch (Exception e) {
			log.debug("Tree-sitter parsing failed for {}, delegating to LineWindowChunker: {}",
					document.metadata().path(), e.getMessage(), e);
			return lineWindowChunker.chunk(document);
		}
	}

	private List<Chunk> extractChunks(
			Document document,
			List<String> lines,
			TSNode rootNode,
			LanguageSpec spec,
			byte[] utf8Bytes
	) {
		List<ContainerDef> topLevelContainers = new ArrayList<>();
		List<CallableDef> topLevelCallables = new ArrayList<>();

		int namedChildCount = rootNode.getNamedChildCount();
		int prevEndRow = -1;

		for (int i = 0; i < namedChildCount; i++) {
			TSNode child = rootNode.getNamedChild(i);
			if (child == null || child.isNull()) {
				continue;
			}

			if (spec.isContainer(child)) {
				ContainerDef c = processContainer(child, null, null, prevEndRow, lines, spec, utf8Bytes);
				topLevelContainers.add(c);
				prevEndRow = child.getEndPoint().getRow();
			} else if (spec.isCallable(child)) {
				CallableDef f = processCallable(child, null, true, prevEndRow, lines, spec, utf8Bytes);
				topLevelCallables.add(f);
				prevEndRow = child.getEndPoint().getRow();
			} else if (!isCommentNode(child)) {
				prevEndRow = child.getEndPoint().getRow();
			}
		}

		// If no containers or callables found for non-trivial file, fallback to LineWindowChunker
		if (topLevelContainers.isEmpty() && topLevelCallables.isEmpty()) {
			boolean nonTrivial = lines.stream().anyMatch(l -> !l.isBlank());
			if (nonTrivial) {
				log.debug("No containers or callables found for non-trivial file {}, delegating to LineWindowChunker",
						document.metadata().path());
				return lineWindowChunker.chunk(document);
			} else {
				return List.of();
			}
		}

		List<RawChunk> rawChunks = new ArrayList<>();
		int totalLines = lines.size();
		DocumentMetadata docMeta = document.metadata();
		int maxTokens = properties.chunking().maxChunkTokens();
		int minTokens = properties.chunking().minChunkTokens();
		int overlap = properties.chunking().overlapLines();

		// 1. MODULE chunks for uncovered top-level lines
		boolean[] covered = new boolean[totalLines + 1];
		for (ContainerDef c : topLevelContainers) {
			int from = Math.max(1, c.startLine());
			int to = Math.min(totalLines, c.endLine());
			for (int l = from; l <= to; l++) {
				covered[l] = true;
			}
		}
		for (CallableDef f : topLevelCallables) {
			int from = Math.max(1, f.startLine());
			int to = Math.min(totalLines, f.endLine());
			for (int l = from; l <= to; l++) {
				covered[l] = true;
			}
		}

		int curStart = -1;
		for (int l = 1; l <= totalLines; l++) {
			if (!covered[l]) {
				if (curStart == -1) {
					curStart = l;
				}
			} else {
				if (curStart != -1) {
					addModuleChunks(rawChunks, curStart, l - 1, lines, docMeta, maxTokens, overlap);
					curStart = -1;
				}
			}
		}
		if (curStart != -1) {
			addModuleChunks(rawChunks, curStart, totalLines, lines, docMeta, maxTokens, overlap);
		}

		// 2. Top-level FUNCTION chunks
		for (CallableDef f : topLevelCallables) {
			emitCallableChunk(rawChunks, f, lines, docMeta, ChunkType.FUNCTION, null, maxTokens, overlap);
		}

		// 3. Containers: CLASS skeleton chunks + METHOD chunks
		List<ContainerDef> allContainers = new ArrayList<>();
		collectAllContainers(topLevelContainers, allContainers);

		for (ContainerDef c : allContainers) {
			// One CLASS skeleton chunk per container
			buildContainerSkeletonChunk(rawChunks, c, lines, docMeta, spec, maxTokens);

			// Callables directly in container c
			List<CallableDef> cCallables = c.callables();
			int i = 0;
			while (i < cCallables.size()) {
				CallableDef cur = cCallables.get(i);
				int curTokens = countCallableTokens(cur, lines, docMeta, c.fullPath());

				if (curTokens < minTokens) {
					// Consecutive callables smaller than minChunkTokens: merge into one chunk
					List<CallableDef> smallGroup = new ArrayList<>();
					smallGroup.add(cur);
					int j = i + 1;
					while (j < cCallables.size()) {
						CallableDef next = cCallables.get(j);
						int nextTokens = countCallableTokens(next, lines, docMeta, c.fullPath());
						if (nextTokens < minTokens) {
							int groupStart = smallGroup.get(0).startLine();
							int groupEnd = next.endLine();
							String candidateContent = String.join("\n", lines.subList(groupStart - 1, groupEnd));
							String candidateSymbols = smallGroup.stream().map(CallableDef::name).collect(Collectors.joining(", ")) + ", " + next.name();
							String candidateHeader = headerBuilder.buildFileHeader(
									docMeta.path(), docMeta.language(), c.fullPath(), candidateSymbols, null, groupStart, groupEnd
							);
							String candidateText = candidateHeader.isEmpty() ? candidateContent : candidateHeader + "\n\n" + candidateContent;
							if (tokenCounter.countTokens(candidateText) <= maxTokens) {
								smallGroup.add(next);
								j++;
							} else {
								break;
							}
						} else {
							break;
						}
					}

					if (smallGroup.size() > 1) {
						int mStart = smallGroup.get(0).startLine();
						int mEnd = smallGroup.get(smallGroup.size() - 1).endLine();
						String mSymbols = smallGroup.stream().map(CallableDef::name).collect(Collectors.joining(", "));
						String mContent = String.join("\n", lines.subList(mStart - 1, mEnd));
						String mHeader = headerBuilder.buildFileHeader(
								docMeta.path(), docMeta.language(), c.fullPath(), mSymbols, null, mStart, mEnd
						);
						String mText = mHeader.isEmpty() ? mContent : mHeader + "\n\n" + mContent;
						rawChunks.add(new RawChunk(mStart, mEnd, ChunkType.METHOD, mSymbols, c.fullPath(), mText, mContent));
						i = j;
						continue;
					}
				}

				emitCallableChunk(rawChunks, cur, lines, docMeta, ChunkType.METHOD, c.fullPath(), maxTokens, overlap);
				i++;
			}
		}

		// Sort in source order with sequential chunk indexes
		rawChunks.sort(Comparator.comparingInt(RawChunk::startLine)
				.thenComparing((a, b) -> {
					if (a.chunkType() == ChunkType.CLASS && b.chunkType() != ChunkType.CLASS) return -1;
					if (b.chunkType() == ChunkType.CLASS && a.chunkType() != ChunkType.CLASS) return 1;
					return Integer.compare(a.endLine(), b.endLine());
				}));

		List<Chunk> finalChunks = new ArrayList<>();
		for (int idx = 0; idx < rawChunks.size(); idx++) {
			RawChunk rc = rawChunks.get(idx);
			ChunkMetadata meta = ChunkMetadata.from(
					docMeta,
					rc.chunkType(),
					tokenCounter.countTokens(rc.text()),
					rc.symbolName(),
					rc.parentSymbol(),
					null,
					rc.startLine(),
					rc.endLine()
			);
			finalChunks.add(Chunk.of(document.id(), idx, rc.text(), rc.content(), meta));
		}

		return finalChunks;
	}

	private ContainerDef processContainer(
			TSNode node,
			String parentSymbol,
			String parentPath,
			int prevEndRow,
			List<String> lines,
			LanguageSpec spec,
			byte[] utf8Bytes
	) {
		String name = spec.extractContainerName(node, utf8Bytes);
		if (name == null || name.isBlank()) {
			name = "Anonymous";
		}
		String fullPath = parentPath == null ? name : parentPath + "." + name;
		int startLine = findPrecedingCommentsStartLine(node, prevEndRow, lines);
		int endLine = getNodeEndLine(node, lines.size());

		List<CallableDef> callables = new ArrayList<>();
		List<ContainerDef> nestedContainers = new ArrayList<>();

		TSNode body = spec.getContainerBody(node);
		if (body != null && !body.isNull()) {
			int childCount = body.getNamedChildCount();
			int innerPrevEndRow = -1;
			for (int j = 0; j < childCount; j++) {
				TSNode innerChild = body.getNamedChild(j);
				if (innerChild == null || innerChild.isNull()) continue;

				if (spec.isContainer(innerChild)) {
					ContainerDef nested = processContainer(innerChild, name, fullPath, innerPrevEndRow, lines, spec, utf8Bytes);
					nestedContainers.add(nested);
					innerPrevEndRow = innerChild.getEndPoint().getRow();
				} else if (spec.isCallable(innerChild)) {
					CallableDef m = processCallable(innerChild, fullPath, false, innerPrevEndRow, lines, spec, utf8Bytes);
					callables.add(m);
					innerPrevEndRow = innerChild.getEndPoint().getRow();
				} else if (!isCommentNode(innerChild)) {
					innerPrevEndRow = innerChild.getEndPoint().getRow();
				}
			}
		}

		return new ContainerDef(node, name, parentPath, fullPath, startLine, endLine, callables, nestedContainers);
	}

	private CallableDef processCallable(
			TSNode node,
			String parentSymbol,
			boolean isTopLevel,
			int prevEndRow,
			List<String> lines,
			LanguageSpec spec,
			byte[] utf8Bytes
	) {
		String name = spec.extractCallableName(node, utf8Bytes);
		if (name == null || name.isBlank()) {
			name = "anonymous";
		}
		int startLine = findPrecedingCommentsStartLine(node, prevEndRow, lines);
		int endLine = getNodeEndLine(node, lines.size());

		return new CallableDef(node, name, parentSymbol, isTopLevel, startLine, endLine);
	}

	private void collectAllContainers(List<ContainerDef> roots, List<ContainerDef> output) {
		for (ContainerDef c : roots) {
			output.add(c);
			collectAllContainers(c.nestedContainers(), output);
		}
	}

	private void collectAllInnerCallables(ContainerDef c, List<CallableDef> output) {
		output.addAll(c.callables());
		for (ContainerDef nested : c.nestedContainers()) {
			collectAllInnerCallables(nested, output);
		}
	}

	private void buildContainerSkeletonChunk(
			List<RawChunk> rawChunks,
			ContainerDef c,
			List<String> lines,
			DocumentMetadata docMeta,
			LanguageSpec spec,
			int maxTokens
	) {
		int startIdx = c.startLine() - 1;
		int endIdx = c.endLine() - 1;

		String[] skel = new String[lines.size()];
		for (int r = startIdx; r <= endIdx && r < lines.size(); r++) {
			skel[r] = lines.get(r);
		}

		List<CallableDef> allCallables = new ArrayList<>();
		collectAllInnerCallables(c, allCallables);

		for (CallableDef m : allCallables) {
			TSNode body = spec.getCallableBody(m.node());
			if (body == null || body.isNull()) {
				continue;
			}

			int bStartRow = body.getStartPoint().getRow();
			int bEndRow = body.getEndPoint().getRow();
			if (body.getEndPoint().getColumn() == 0 && bEndRow > bStartRow) {
				bEndRow--;
			}

			if (bStartRow > endIdx || bEndRow < startIdx) {
				continue;
			}

			String placeholder = spec.getBodySkeletonPlaceholder();

			if ("...".equals(placeholder)) {
				// Python indentation-based replacement
				int defRow = m.startLine() - 1;
				String indent = (defRow >= 0 && defRow < lines.size()) ? getLeadingWhitespace(lines.get(defRow)) : "";
				if (bStartRow > defRow) {
					if (bStartRow <= endIdx) {
						skel[bStartRow] = indent + "    ...";
					}
					for (int r = bStartRow + 1; r <= bEndRow && r <= endIdx; r++) {
						skel[r] = null;
					}
				} else {
					String line = lines.get(bStartRow);
					int colon = line.indexOf(':');
					if (colon >= 0) {
						skel[bStartRow] = line.substring(0, colon + 1) + " ...";
					}
					for (int r = bStartRow + 1; r <= bEndRow && r <= endIdx; r++) {
						skel[r] = null;
					}
				}
			} else {
				// C-style { ... } replacement
				String startLineText = (bStartRow < lines.size()) ? lines.get(bStartRow) : "";
				int braceIdx = startLineText.indexOf('{');
				if (braceIdx >= 0) {
					String prefix = startLineText.substring(0, braceIdx);
					if (bStartRow == bEndRow) {
						int closeIdx = startLineText.lastIndexOf('}');
						String suffix = (closeIdx >= 0 && closeIdx < startLineText.length() - 1)
								? startLineText.substring(closeIdx + 1)
								: "";
						skel[bStartRow] = prefix + placeholder + suffix;
					} else {
						skel[bStartRow] = prefix + placeholder;
						for (int r = bStartRow + 1; r <= bEndRow && r <= endIdx; r++) {
							skel[r] = null;
						}
					}
				}
			}
		}

		List<String> skeletonLines = new ArrayList<>();
		for (int r = startIdx; r <= endIdx && r < lines.size(); r++) {
			if (skel[r] != null) {
				skeletonLines.add(skel[r]);
			}
		}

		String header = headerBuilder.buildFileHeader(
				docMeta.path(), docMeta.language(), c.parentSymbol(), c.name(), null, c.startLine(), c.endLine()
		);
		String fullContent = String.join("\n", skeletonLines);
		String fullText = header.isEmpty() ? fullContent : header + "\n\n" + fullContent;

		String finalContent;
		String finalText;
		if (tokenCounter.countTokens(fullText) <= maxTokens) {
			finalContent = fullContent;
			finalText = fullText;
		} else {
			finalContent = truncateSkeleton(header, skeletonLines, maxTokens);
			finalText = header.isEmpty() ? finalContent : header + "\n\n" + finalContent;
		}

		rawChunks.add(new RawChunk(c.startLine(), c.endLine(), ChunkType.CLASS, c.name(), c.parentSymbol(), finalText, finalContent));
	}

	private String truncateSkeleton(String header, List<String> skeletonLines, int maxTokens) {
		int low = 1;
		int high = skeletonLines.size();
		int best = 1;
		while (low <= high) {
			int mid = (low + high) >>> 1;
			String content = String.join("\n", skeletonLines.subList(0, mid));
			String text = header.isEmpty() ? content : header + "\n\n" + content;
			if (tokenCounter.countTokens(text) <= maxTokens) {
				best = mid;
				low = mid + 1;
			} else {
				high = mid - 1;
			}
		}
		return String.join("\n", skeletonLines.subList(0, best));
	}

	private void emitCallableChunk(
			List<RawChunk> rawChunks,
			CallableDef callable,
			List<String> lines,
			DocumentMetadata docMeta,
			ChunkType chunkType,
			String parentSymbol,
			int maxTokens,
			int overlap
	) {
		int s = callable.startLine();
		int e = callable.endLine();
		String content = String.join("\n", lines.subList(s - 1, e));
		String header = headerBuilder.buildFileHeader(docMeta.path(), docMeta.language(), parentSymbol, callable.name(), null, s, e);
		String text = header.isEmpty() ? content : header + "\n\n" + content;

		if (tokenCounter.countTokens(text) <= maxTokens) {
			rawChunks.add(new RawChunk(s, e, chunkType, callable.name(), parentSymbol, text, content));
		} else {
			splitLinesIntoChunks(rawChunks, s, e, lines, docMeta, chunkType, callable.name(), parentSymbol, maxTokens, overlap);
		}
	}

	private void addModuleChunks(
			List<RawChunk> rawChunks,
			int startLine,
			int endLine,
			List<String> lines,
			DocumentMetadata docMeta,
			int maxTokens,
			int overlap
	) {
		int s = startLine;
		int e = endLine;
		while (s <= e && lines.get(s - 1).isBlank()) {
			s++;
		}
		while (e >= s && lines.get(e - 1).isBlank()) {
			e--;
		}
		if (s > e) {
			return;
		}

		String content = String.join("\n", lines.subList(s - 1, e));
		String header = headerBuilder.buildFileHeader(docMeta.path(), docMeta.language(), null, null, null, s, e);
		String text = header.isEmpty() ? content : header + "\n\n" + content;

		if (tokenCounter.countTokens(text) <= maxTokens) {
			rawChunks.add(new RawChunk(s, e, ChunkType.MODULE, null, null, text, content));
		} else {
			splitLinesIntoChunks(rawChunks, s, e, lines, docMeta, ChunkType.MODULE, null, null, maxTokens, overlap);
		}
	}

	private void splitLinesIntoChunks(
			List<RawChunk> rawChunks,
			int startLine,
			int endLine,
			List<String> lines,
			DocumentMetadata docMeta,
			ChunkType chunkType,
			String symbolName,
			String parentSymbol,
			int maxTokens,
			int overlap
	) {
		List<String> sliceLines = lines.subList(startLine - 1, endLine);
		int total = sliceLines.size();
		int start = 0;

		while (start < total) {
			int wStartLine = startLine + start;
			int end = start + 1;

			while (end < total) {
				int candidateEnd = end + 1;
				int wCandEndLine = startLine + candidateEnd - 1;
				String candContent = String.join("\n", sliceLines.subList(start, candidateEnd));
				String candHeader = headerBuilder.buildFileHeader(
						docMeta.path(), docMeta.language(), parentSymbol, symbolName, null, wStartLine, wCandEndLine
				);
				String candText = candHeader.isEmpty() ? candContent : candHeader + "\n\n" + candContent;
				if (tokenCounter.countTokens(candText) > maxTokens) {
					break;
				}
				end = candidateEnd;
			}

			int wEndLine = startLine + end - 1;
			String wContent = String.join("\n", sliceLines.subList(start, end));
			String wHeader = headerBuilder.buildFileHeader(
					docMeta.path(), docMeta.language(), parentSymbol, symbolName, null, wStartLine, wEndLine
			);
			String wText = wHeader.isEmpty() ? wContent : wHeader + "\n\n" + wContent;

			rawChunks.add(new RawChunk(wStartLine, wEndLine, chunkType, symbolName, parentSymbol, wText, wContent));

			if (end >= total) {
				break;
			}
			int nextStart = end - overlap;
			if (nextStart <= start) {
				nextStart = start + 1;
			}
			start = nextStart;
		}
	}

	private int countCallableTokens(CallableDef c, List<String> lines, DocumentMetadata docMeta, String parentSymbol) {
		int s = c.startLine();
		int e = c.endLine();
		String content = String.join("\n", lines.subList(s - 1, e));
		String header = headerBuilder.buildFileHeader(docMeta.path(), docMeta.language(), parentSymbol, c.name(), null, s, e);
		String text = header.isEmpty() ? content : header + "\n\n" + content;
		return tokenCounter.countTokens(text);
	}

	private int getNodeEndLine(TSNode node, int maxLines) {
		int endRow = node.getEndPoint().getRow();
		int endCol = node.getEndPoint().getColumn();
		int line = (endCol > 0 || endRow == node.getStartPoint().getRow()) ? endRow + 1 : endRow;
		return Math.min(Math.max(1, line), maxLines);
	}

	private int findPrecedingCommentsStartLine(TSNode node, int prevEndRow, List<String> lines) {
		int startRow = node.getStartPoint().getRow();
		int cur = startRow - 1;
		int blankCount = 0;
		int earliestCommentRow = startRow;

		while (cur > prevEndRow && cur >= 0) {
			String line = lines.get(cur).trim();
			if (line.isEmpty()) {
				blankCount++;
				if (blankCount > 1) {
					break;
				}
				cur--;
				continue;
			}
			if (isCommentLine(line)) {
				blankCount = 0;
				earliestCommentRow = cur;
				cur--;
			} else {
				break;
			}
		}
		return earliestCommentRow + 1;
	}

	private boolean isCommentNode(TSNode node) {
		if (node == null || node.isNull()) {
			return false;
		}
		String type = node.getType();
		return type != null && type.contains("comment");
	}

	private boolean isCommentLine(String trimmed) {
		return trimmed.startsWith("//")
				|| trimmed.startsWith("/*")
				|| trimmed.startsWith("*")
				|| trimmed.startsWith("*/")
				|| trimmed.startsWith("#");
	}

	private String getLeadingWhitespace(String s) {
		if (s == null) return "";
		int i = 0;
		while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
			i++;
		}
		return s.substring(0, i);
	}

	private record ContainerDef(
			TSNode node,
			String name,
			String parentSymbol,
			String fullPath,
			int startLine,
			int endLine,
			List<CallableDef> callables,
			List<ContainerDef> nestedContainers
	) {}

	private record CallableDef(
			TSNode node,
			String name,
			String parentSymbol,
			boolean isTopLevel,
			int startLine,
			int endLine
	) {}

	private record RawChunk(
			int startLine,
			int endLine,
			ChunkType chunkType,
			String symbolName,
			String parentSymbol,
			String text,
			String content
	) {}
}
