package io.github.tarunipriya.gitcontext.api;

import io.github.tarunipriya.gitcontext.ingestion.IngestionJobService;
import io.github.tarunipriya.gitcontext.ingestion.IngestionResult;
import io.github.tarunipriya.gitcontext.model.Chunk;
import io.github.tarunipriya.gitcontext.model.ChunkType;
import io.github.tarunipriya.gitcontext.model.Document;
import io.github.tarunipriya.gitcontext.model.RepoRef;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Stream;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for inspecting ingested repository files, chunks, and individual chunk contents.
 */
@Tag(name = "Repository Chunks", description = "Inspection of indexed repository files and chunks")
@RestController
public class RepoChunksController {

	private final IngestionJobService ingestionJobService;

	/**
	 * Creates a new {@link RepoChunksController}.
	 *
	 * @param ingestionJobService ingestion job and results service
	 */
	public RepoChunksController(IngestionJobService ingestionJobService) {
		this.ingestionJobService = Objects.requireNonNull(ingestionJobService, "ingestionJobService must not be null");
	}

	/**
	 * Returns distinct file paths from the latest ingestion result with their source type, language,
	 * and chunk count, sorted alphabetically by path.
	 *
	 * @param owner repository owner
	 * @param name repository name
	 * @return list of file summary items sorted by path
	 */
	@Operation(summary = "List distinct ingested files in a repository")
	@GetMapping("/api/repos/{owner}/{name}/files")
	public ResponseEntity<List<RepoFileItem>> getFiles(
			@PathVariable String owner,
			@PathVariable String name
	) {
		RepoRef repo = new RepoRef(owner, name);
		IngestionResult result = ingestionJobService.getLatestResult(repo)
				.orElseThrow(() -> new RepoNotFoundException(repo.fullName()));

		Map<String, Integer> chunkCountsByPath = new HashMap<>();
		for (Chunk chunk : result.chunks()) {
			if (chunk.metadata().path() != null) {
				chunkCountsByPath.merge(chunk.metadata().path(), 1, Integer::sum);
			}
		}

		Map<String, RepoFileItem> filesByPath = new TreeMap<>();
		for (Document doc : result.documents()) {
			String p = doc.metadata().path();
			if (p != null) {
				filesByPath.putIfAbsent(p, new RepoFileItem(
						p,
						doc.metadata().sourceType(),
						doc.metadata().language(),
						chunkCountsByPath.getOrDefault(p, 0)
				));
			}
		}

		for (Chunk chunk : result.chunks()) {
			String p = chunk.metadata().path();
			if (p != null && !filesByPath.containsKey(p)) {
				filesByPath.put(p, new RepoFileItem(
						p,
						chunk.metadata().sourceType(),
						chunk.metadata().language(),
						chunkCountsByPath.getOrDefault(p, 0)
				));
			}
		}

		return ResponseEntity.ok(new ArrayList<>(filesByPath.values()));
	}

	/**
	 * Returns paginated chunks for the repository matching optional filters for path, chunk type, and symbol name.
	 *
	 * @param owner repository owner
	 * @param name repository name
	 * @param path exact file path filter (optional)
	 * @param type chunk classification filter (optional)
	 * @param symbol case-insensitive substring filter on symbol name (optional)
	 * @param limit maximum items to return (1-200, default 50)
	 * @param offset starting index offset (default 0)
	 * @return page of chunk items
	 */
	@Operation(summary = "List paginated chunks matching query filters")
	@GetMapping("/api/repos/{owner}/{name}/chunks")
	public ResponseEntity<PageResponse<ChunkItemResponse>> getChunks(
			@PathVariable String owner,
			@PathVariable String name,
			@RequestParam(required = false) String path,
			@RequestParam(required = false) String type,
			@RequestParam(required = false) String symbol,
			@RequestParam(defaultValue = "50") int limit,
			@RequestParam(defaultValue = "0") int offset
	) {
		if (limit < 1 || limit > 200) {
			throw new IllegalArgumentException("limit must be between 1 and 200 (received " + limit + ")");
		}
		if (offset < 0) {
			throw new IllegalArgumentException("offset must be non-negative (received " + offset + ")");
		}

		ChunkType chunkType = null;
		if (type != null && !type.isBlank()) {
			try {
				chunkType = ChunkType.valueOf(type.trim().toUpperCase(Locale.ROOT));
			} catch (IllegalArgumentException e) {
				throw new IllegalArgumentException(
						"Invalid chunk type '" + type + "'. Allowed values: " + Arrays.toString(ChunkType.values())
				);
			}
		}

		RepoRef repo = new RepoRef(owner, name);
		IngestionResult result = ingestionJobService.getLatestResult(repo)
				.orElseThrow(() -> new RepoNotFoundException(repo.fullName()));

		Stream<Chunk> stream = result.chunks().stream();

		if (path != null && !path.isBlank()) {
			String normalizedPath = path.trim().replace('\\', '/');
			stream = stream.filter(c -> normalizedPath.equals(c.metadata().path()));
		}

		if (chunkType != null) {
			ChunkType targetType = chunkType;
			stream = stream.filter(c -> c.metadata().chunkType() == targetType);
		}

		if (symbol != null && !symbol.isBlank()) {
			String lowerSymbol = symbol.trim().toLowerCase(Locale.ROOT);
			stream = stream.filter(c -> c.metadata().symbolName() != null
					&& c.metadata().symbolName().toLowerCase(Locale.ROOT).contains(lowerSymbol));
		}

		List<Chunk> filtered = stream.toList();
		int total = filtered.size();
		List<ChunkItemResponse> items;
		if (offset >= total) {
			items = List.of();
		} else {
			int toIndex = Math.min(offset + limit, total);
			items = filtered.subList(offset, toIndex).stream()
					.map(ChunkItemResponse::from)
					.toList();
		}

		return ResponseEntity.ok(new PageResponse<>(total, limit, offset, items));
	}

	/**
	 * Returns full details and raw content for a single chunk identified by its ID.
	 *
	 * @param owner repository owner
	 * @param name repository name
	 * @param chunkId chunk identifier
	 * @return chunk details with text and source content
	 */
	@Operation(summary = "Get details and content for a specific chunk")
	@GetMapping("/api/repos/{owner}/{name}/chunks/{chunkId}")
	public ResponseEntity<ChunkDetailResponse> getChunk(
			@PathVariable String owner,
			@PathVariable String name,
			@PathVariable String chunkId
	) {
		RepoRef repo = new RepoRef(owner, name);
		IngestionResult result = ingestionJobService.getLatestResult(repo)
				.orElseThrow(() -> new RepoNotFoundException(repo.fullName()));

		Chunk chunk = result.chunks().stream()
				.filter(c -> c.id().equals(chunkId))
				.findFirst()
				.orElseThrow(() -> new ChunkNotFoundException(chunkId));

		return ResponseEntity.ok(ChunkDetailResponse.from(chunk));
	}
}
