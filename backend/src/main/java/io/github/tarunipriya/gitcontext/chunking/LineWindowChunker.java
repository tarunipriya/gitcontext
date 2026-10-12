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
 * <p>Splits documents into fixed line windows of {@code fallbackWindowLines} lines with {@code overlapLines} overlap,
 * tagging each chunk with {@link ChunkType#LINES} and exact line bounds without splitting lines.
 */
@Component
public class LineWindowChunker implements Chunker {

	private final GitContextProperties properties;
	private final TokenCounter tokenCounter;
	private final ContextHeaderBuilder headerBuilder;

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
		DocumentMetadata docMeta = document.metadata();

		List<Chunk> chunks = new ArrayList<>();
		int chunkIndex = 0;

		if (totalLines <= windowSize) {
			int startLine = 1;
			int endLine = totalLines;
			String content = String.join("\n", lines);
			String header = headerBuilder.buildFileHeader(
					docMeta.path(),
					docMeta.language(),
					null,
					null,
					null,
					startLine,
					endLine
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
			chunks.add(Chunk.of(document.id(), chunkIndex, text, content, meta));
			return chunks;
		}

		int step = Math.max(1, windowSize - overlap);
		int start = 0;
		while (start < totalLines) {
			int end = Math.min(start + windowSize, totalLines);
			int startLine = start + 1;
			int endLine = end;
			String content = String.join("\n", lines.subList(start, end));
			String header = headerBuilder.buildFileHeader(
					docMeta.path(),
					docMeta.language(),
					null,
					null,
					null,
					startLine,
					endLine
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
			start += step;
		}

		return chunks;
	}
}
