package io.github.tarunipriya.gitcontext.chunking;

import io.github.tarunipriya.gitcontext.model.Chunk;
import io.github.tarunipriya.gitcontext.model.ChunkType;
import io.github.tarunipriya.gitcontext.model.Document;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Orchestrates document chunking across registered {@link Chunker} strategies.
 *
 * <p>For each {@link Document}, selects the first matching chunker from an ordered list, ensuring
 * {@link LineWindowChunker} is positioned last as the universal line-window fallback. Sequential 0-based
 * chunk indexes and deterministic chunk IDs are produced per document.
 */
@Service
public class ChunkingService {

	private static final Logger log = LoggerFactory.getLogger(ChunkingService.class);

	private final List<Chunker> chunkers;

	public ChunkingService(List<Chunker> chunkers) {
		Objects.requireNonNull(chunkers, "chunkers must not be null");
		List<Chunker> ordered = new ArrayList<>();
		Chunker lineWindowChunker = null;

		for (Chunker c : chunkers) {
			if (c instanceof LineWindowChunker) {
				lineWindowChunker = c;
			} else {
				ordered.add(c);
			}
		}
		if (lineWindowChunker != null) {
			ordered.add(lineWindowChunker);
		}

		this.chunkers = List.copyOf(ordered);
	}

	/**
	 * Chunks all provided documents using the first matching chunker strategy for each document.
	 *
	 * @param documents the list of documents to chunk
	 * @return list of all generated chunks across all documents
	 */
	public List<Chunk> chunkAll(List<Document> documents) {
		if (documents == null || documents.isEmpty()) {
			return List.of();
		}

		List<Chunk> allChunks = new ArrayList<>();

		for (Document doc : documents) {
			Chunker selected = null;
			for (Chunker chunker : chunkers) {
				if (chunker.supports(doc)) {
					selected = chunker;
					break;
				}
			}

			if (selected == null) {
				log.warn("No chunker found supporting document id={} (sourceType={}, path={})",
						doc.id(), doc.metadata().sourceType(), doc.metadata().path());
				continue;
			}

			List<Chunk> docChunks = selected.chunk(doc);
			allChunks.addAll(docChunks);
		}

		Map<ChunkType, Long> countsByType = allChunks.stream()
				.collect(Collectors.groupingBy(c -> c.metadata().chunkType(), Collectors.counting()));

		log.info("Chunked {} documents into {} chunks: {}", documents.size(), allChunks.size(), countsByType);

		return List.copyOf(allChunks);
	}
}
