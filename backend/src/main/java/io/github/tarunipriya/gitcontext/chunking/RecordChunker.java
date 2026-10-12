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
 * Chunks non-file repository records (Git commits, GitHub pull requests, and issues).
 *
 * <p>Produces a single chunk for concise records without line numbers. For large records exceeding
 * {@code maxChunkTokens}, content is segmented along paragraph boundaries (blank lines) into consecutive chunks,
 * each repeating the contextual record header. Large individual paragraphs are hard-split when they exceed the token limit.
 */
@Component
public class RecordChunker implements Chunker {

	private final GitContextProperties properties;
	private final TokenCounter tokenCounter;
	private final ContextHeaderBuilder headerBuilder;

	public RecordChunker(
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
		SourceType st = document.metadata().sourceType();
		return st == SourceType.COMMIT || st == SourceType.PULL_REQUEST || st == SourceType.ISSUE;
	}

	@Override
	public List<Chunk> chunk(Document document) {
		Objects.requireNonNull(document, "document must not be null");
		if (document.content() == null || document.content().isBlank()) {
			return List.of();
		}

		DocumentMetadata docMeta = document.metadata();
		ChunkType chunkType = switch (docMeta.sourceType()) {
			case COMMIT -> ChunkType.COMMIT;
			case PULL_REQUEST -> ChunkType.PULL_REQUEST;
			case ISSUE -> ChunkType.ISSUE;
			default -> throw new IllegalArgumentException("Unsupported sourceType: " + docMeta.sourceType());
		};

		String header = headerBuilder.buildRecordHeader(document);
		String fullText = header.isEmpty() ? document.content() : header + "\n\n" + document.content();
		int maxTokens = properties.chunking().maxChunkTokens();

		if (tokenCounter.countTokens(fullText) <= maxTokens) {
			ChunkMetadata meta = ChunkMetadata.from(
					docMeta,
					chunkType,
					tokenCounter.countTokens(fullText),
					null,
					null,
					null,
					null,
					null
			);
			return List.of(Chunk.of(document.id(), 0, fullText, document.content(), meta));
		}

		// Split on paragraph boundaries (blank lines)
		List<String> paragraphs = extractParagraphs(document.content());
		List<Chunk> chunks = new ArrayList<>();
		int[] chunkIndex = new int[]{0};

		StringBuilder curChunk = new StringBuilder();

		for (String para : paragraphs) {
			String candidateContent = curChunk.length() == 0 ? para : curChunk + "\n\n" + para;
			String candidateText = header.isEmpty() ? candidateContent : header + "\n\n" + candidateContent;

			if (tokenCounter.countTokens(candidateText) <= maxTokens) {
				curChunk = new StringBuilder(candidateContent);
			} else {
				if (curChunk.length() > 0) {
					emitChunk(chunks, document, chunkType, header, curChunk.toString(), chunkIndex[0]++);
					curChunk.setLength(0);
				}

				// Check if this single paragraph fits on its own
				String singleParaText = header.isEmpty() ? para : header + "\n\n" + para;
				if (tokenCounter.countTokens(singleParaText) <= maxTokens) {
					curChunk = new StringBuilder(para);
				} else {
					// Hard-split this single paragraph across lines
					List<String> paraLines = para.lines().toList();
					StringBuilder hardSlice = new StringBuilder();
					for (String pLine : paraLines) {
						String testHard = hardSlice.length() == 0 ? pLine : hardSlice + "\n" + pLine;
						String testHardText = header.isEmpty() ? testHard : header + "\n\n" + testHard;
						if (tokenCounter.countTokens(testHardText) <= maxTokens) {
							hardSlice = new StringBuilder(testHard);
						} else {
							if (hardSlice.length() > 0) {
								emitChunk(chunks, document, chunkType, header, hardSlice.toString(), chunkIndex[0]++);
								hardSlice.setLength(0);
							}
							hardSlice.append(pLine);
						}
					}
					if (hardSlice.length() > 0) {
						curChunk = new StringBuilder(hardSlice.toString());
					}
				}
			}
		}

		if (curChunk.length() > 0) {
			emitChunk(chunks, document, chunkType, header, curChunk.toString(), chunkIndex[0]++);
		}

		return chunks;
	}

	private void emitChunk(
			List<Chunk> chunks,
			Document document,
			ChunkType chunkType,
			String header,
			String sliceContent,
			int index
	) {
		String text = header.isEmpty() ? sliceContent : header + "\n\n" + sliceContent;
		ChunkMetadata meta = ChunkMetadata.from(
				document.metadata(),
				chunkType,
				tokenCounter.countTokens(text),
				null,
				null,
				null,
				null,
				null
		);
		chunks.add(Chunk.of(document.id(), index, text, sliceContent, meta));
	}

	private List<String> extractParagraphs(String content) {
		List<String> paragraphs = new ArrayList<>();
		StringBuilder current = new StringBuilder();

		for (String line : content.lines().toList()) {
			if (line.isBlank()) {
				if (current.length() > 0) {
					paragraphs.add(current.toString().trim());
					current.setLength(0);
				}
			} else {
				if (current.length() > 0) {
					current.append("\n");
				}
				current.append(line);
			}
		}
		if (current.length() > 0) {
			paragraphs.add(current.toString().trim());
		}

		return paragraphs;
	}
}
