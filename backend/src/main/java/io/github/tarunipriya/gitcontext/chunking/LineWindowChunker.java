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
import org.springframework.stereotype.Component;

/**
 * Fallback chunker for source code and documentation files without dedicated structural chunkers.
 *
 * <p>Splits documents into windows of {@code fallbackWindowLines} lines with {@code overlapLines} overlap,
 * ensuring each chunk respects {@code maxChunkTokens}. If a window exceeds {@code maxChunkTokens}, it is shrunk
 * to fit. If a single line alone exceeds the limit, it is hard-split into pieces sharing the same line number.
 */
@Component
public class LineWindowChunker implements Chunker {

	private final GitContextProperties properties;
	private final TokenCounter tokenCounter;
	private final ContextHeaderBuilder headerBuilder;

	/**
	 * Creates a new {@link LineWindowChunker}.
	 *
	 * @param properties configuration properties defining window and overlap sizing
	 * @param tokenCounter token counter for sizing chunk windows
	 * @param headerBuilder context header builder for chunk headers
	 */
	public LineWindowChunker(
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
		SourceType sourceType = document.metadata().sourceType();
		return sourceType == SourceType.CODE || sourceType == SourceType.DOC;
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

		int totalLines = lines.size();
		int windowSize = properties.chunking().fallbackWindowLines();
		int overlap = properties.chunking().overlapLines();
		int maxTokens = properties.chunking().maxChunkTokens();
		DocumentMetadata docMeta = document.metadata();

		List<Chunk> chunks = new ArrayList<>();
		int chunkIndex = 0;
		int start = 0;

		while (start < totalLines) {
			String firstLine = lines.get(start);
			int lineNum = start + 1;
			String singleLineHeader = headerBuilder.buildFileHeader(
					docMeta.path(), docMeta.language(), null, null, null, lineNum, lineNum
			);
			String singleLineText = singleLineHeader.isEmpty() ? firstLine : singleLineHeader + "\n\n" + firstLine;

			// If a single line alone exceeds maxChunkTokens, hard-split that line into pieces
			if (tokenCounter.countTokens(singleLineText) > maxTokens) {
				List<String> pieces = splitLongLine(firstLine, singleLineHeader, maxTokens);
				for (String piece : pieces) {
					String pieceText = singleLineHeader.isEmpty() ? piece : singleLineHeader + "\n\n" + piece;
					ChunkMetadata meta = ChunkMetadata.from(
							docMeta,
							ChunkType.LINES,
							tokenCounter.countTokens(pieceText),
							null,
							null,
							null,
							lineNum,
							lineNum
					);
					chunks.add(Chunk.of(document.id(), chunkIndex++, pieceText, piece, meta));
				}
				start++;
				continue;
			}

			// Expand window up to windowSize lines, shrinking if maxChunkTokens is exceeded
			int maxEnd = Math.min(start + windowSize, totalLines);
			int end = start + 1;

			while (end < maxEnd) {
				int candidateEnd = end + 1;
				String candidateContent = String.join("\n", lines.subList(start, candidateEnd));
				String candidateHeader = headerBuilder.buildFileHeader(
						docMeta.path(), docMeta.language(), null, null, null, lineNum, candidateEnd
				);
				String candidateText = candidateHeader.isEmpty() ? candidateContent : candidateHeader + "\n\n" + candidateContent;

				if (tokenCounter.countTokens(candidateText) > maxTokens) {
					break;
				}
				end = candidateEnd;
			}

			int startLine = start + 1;
			int endLine = end;
			String content = String.join("\n", lines.subList(start, end));
			String header = headerBuilder.buildFileHeader(
					docMeta.path(), docMeta.language(), null, null, null, startLine, endLine
			);
			String text = header.isEmpty() ? content : header + "\n\n" + content;
			ChunkMetadata meta = ChunkMetadata.from(
					docMeta,
					ChunkType.LINES,
					tokenCounter.countTokens(text),
					null,
					null,
					null,
					startLine,
					endLine
			);
			chunks.add(Chunk.of(document.id(), chunkIndex++, text, content, meta));

			if (end == totalLines) {
				break;
			}

			int step = Math.max(1, (end - start) - overlap);
			start += step;
		}

		return chunks;
	}

	private List<String> splitLongLine(String line, String header, int maxTokens) {
		List<String> pieces = new ArrayList<>();
		int charStart = 0;
		while (charStart < line.length()) {
			int low = charStart + 1;
			int high = line.length();
			int best = low;
			while (low <= high) {
				int mid = (low + high) >>> 1;
				String slice = line.substring(charStart, mid);
				String text = header.isEmpty() ? slice : header + "\n\n" + slice;
				if (tokenCounter.countTokens(text) <= maxTokens) {
					best = mid;
					low = mid + 1;
				} else {
					high = mid - 1;
				}
			}
			pieces.add(line.substring(charStart, best));
			charStart = best;
		}
		return pieces;
	}
}
