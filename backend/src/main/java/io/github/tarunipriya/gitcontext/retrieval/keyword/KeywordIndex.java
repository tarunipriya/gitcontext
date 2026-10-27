package io.github.tarunipriya.gitcontext.retrieval.keyword;

import io.github.tarunipriya.gitcontext.config.GitContextProperties;
import io.github.tarunipriya.gitcontext.indexing.ChunkPointMapper;
import io.github.tarunipriya.gitcontext.indexing.SearchHit;
import io.github.tarunipriya.gitcontext.model.Chunk;
import io.github.tarunipriya.gitcontext.model.ChunkType;
import io.github.tarunipriya.gitcontext.model.RepoRef;
import io.github.tarunipriya.gitcontext.model.SourceType;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.queryparser.classic.QueryParserBase;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchNoDocsQuery;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.SearcherManager;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.store.FSDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Thread-safe Lucene BM25 keyword index per repository.
 *
 * <p>Builds isolated Lucene index directories under {@code indexDir/<owner>/<name>} using an atomic
 * rebuild workflow (staging in a temporary directory and atomically replacing the existing directory).
 * Provides multi-field BM25 keyword scoring over text, symbol names, parent symbols, and file paths
 * with exact type and path-prefix filters.
 */
@Component
public class KeywordIndex implements AutoCloseable {

	private static final Logger log = LoggerFactory.getLogger(KeywordIndex.class);

	private final GitContextProperties properties;
	private final CodeAnalyzer codeAnalyzer;
	private final ConcurrentHashMap<String, SearcherManager> searcherManagers = new ConcurrentHashMap<>();

	/**
	 * Creates a new {@link KeywordIndex} backed by application properties and code analyzer.
	 *
	 * @param properties application configuration properties
	 * @param codeAnalyzer code-aware Lucene analyzer
	 */
	public KeywordIndex(GitContextProperties properties, CodeAnalyzer codeAnalyzer) {
		this.properties = Objects.requireNonNull(properties, "properties must not be null");
		this.codeAnalyzer = Objects.requireNonNull(codeAnalyzer, "codeAnalyzer must not be null");
	}

	/**
	 * Builds or rebuilds the Lucene index for the specified repository using atomic directory replacement.
	 *
	 * @param repo repository reference
	 * @param chunks list of chunks to index
	 */
	public void build(RepoRef repo, List<Chunk> chunks) {
		Objects.requireNonNull(repo, "repo must not be null");
		Objects.requireNonNull(chunks, "chunks must not be null");

		Path repoDir = getRepoDir(repo);
		Path parentDir = repoDir.getParent();
		try {
			Files.createDirectories(parentDir);
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to create index parent directory: " + parentDir, e);
		}

		Path tempDir;
		try {
			tempDir = Files.createTempDirectory(parentDir, repoDir.getFileName().toString() + "-tmp-");
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to create temporary build directory under: " + parentDir, e);
		}

		try {
			try (FSDirectory luceneDir = FSDirectory.open(tempDir)) {
				IndexWriterConfig config = new IndexWriterConfig(codeAnalyzer);
				config.setOpenMode(IndexWriterConfig.OpenMode.CREATE);
				config.setSimilarity(new BM25Similarity());
				try (IndexWriter writer = new IndexWriter(luceneDir, config)) {
					for (Chunk chunk : chunks) {
						Document doc = toDocument(repo, chunk);
						writer.addDocument(doc);
					}
					writer.commit();
				}
			}

			// Invalidate cached SearcherManager for this repo so active readers are released before directory replace
			String repoKey = repo.normalizedKey();
			SearcherManager oldSm = searcherManagers.remove(repoKey);
			if (oldSm != null) {
				try {
					oldSm.close();
				} catch (IOException e) {
					log.warn("Error closing old SearcherManager for {}: {}", repoKey, e.getMessage());
				}
			}

			atomicReplace(tempDir, repoDir);
			log.info("Built Lucene keyword index for {} with {} chunks in {}", repo.fullName(), chunks.size(), repoDir);
		} catch (IOException e) {
			deleteRecursively(tempDir);
			throw new UncheckedIOException("Failed to build Lucene keyword index for " + repo.fullName(), e);
		}
	}

	/**
	 * Executes BM25 keyword search across chunks of the specified repository.
	 *
	 * @param repo repository identifier reference
	 * @param query search query or code snippet
	 * @param k maximum hits to return
	 * @param types optional filter on chunk types
	 * @param pathPrefix optional file path prefix filter
	 * @return ranked search hits
	 */
	public List<SearchHit> search(RepoRef repo, String query, int k, Set<ChunkType> types, String pathPrefix) {
		Objects.requireNonNull(repo, "repo must not be null");
		Objects.requireNonNull(query, "query must not be null");
		if (k <= 0 || !exists(repo)) {
			return List.of();
		}

		String repoKey = repo.normalizedKey();
		SearcherManager sm = getOrOpenSearcherManager(repo);
		if (sm == null) {
			return List.of();
		}

		IndexSearcher searcher;
		try {
			sm.maybeRefresh();
			searcher = sm.acquire();
		} catch (IOException e) {
			log.warn("Failed to acquire Lucene IndexSearcher for {}: {}", repoKey, e.getMessage());
			return List.of();
		}

		try {
			return executeSearch(searcher, query, k, types, pathPrefix);
		} catch (IOException e) {
			throw new UncheckedIOException("Error executing Lucene search for " + repo.fullName(), e);
		} finally {
			try {
				sm.release(searcher);
			} catch (IOException e) {
				log.warn("Failed to release Lucene IndexSearcher for {}: {}", repoKey, e.getMessage());
			}
		}
	}

	/**
	 * Returns {@code true} if a valid Lucene index exists for the specified repository.
	 *
	 * @param repo repository identifier reference
	 * @return true if index directory exists and contains valid index segments
	 */
	public boolean exists(RepoRef repo) {
		if (repo == null) {
			return false;
		}
		Path repoDir = getRepoDir(repo);
		if (!Files.isDirectory(repoDir)) {
			return false;
		}
		try (FSDirectory dir = FSDirectory.open(repoDir)) {
			return DirectoryReader.indexExists(dir);
		} catch (IOException e) {
			return false;
		}
	}

	/**
	 * Deletes all Lucene index data and closes readers for the specified repository.
	 *
	 * @param repo repository reference
	 */
	public void deleteRepo(RepoRef repo) {
		Objects.requireNonNull(repo, "repo must not be null");
		String repoKey = repo.normalizedKey();
		SearcherManager sm = searcherManagers.remove(repoKey);
		if (sm != null) {
			try {
				sm.close();
			} catch (IOException e) {
				log.warn("Error closing SearcherManager for {}: {}", repoKey, e.getMessage());
			}
		}
		Path repoDir = getRepoDir(repo);
		if (Files.exists(repoDir)) {
			deleteRecursively(repoDir);
			log.info("Deleted Lucene keyword index directory for {}", repo.fullName());
		}
	}

	/**
	 * Returns the local filesystem directory for the repository's Lucene index under {@code indexDir/<owner>/<name>}.
	 *
	 * @param repo repository reference
	 * @return resolved directory path
	 */
	public Path getRepoDir(RepoRef repo) {
		Objects.requireNonNull(repo, "repo must not be null");
		String owner = repo.owner().toLowerCase(Locale.ROOT);
		String name = repo.name().toLowerCase(Locale.ROOT);
		return properties.keyword().indexDir().resolve(owner).resolve(name);
	}

	/**
	 * Lists all repositories that have an existing Lucene keyword index on disk under {@code properties.keyword().indexDir()}.
	 *
	 * @return sorted list of repository references
	 */
	public List<RepoRef> listIndexedRepos() {
		Path base = properties.keyword().indexDir();
		if (base == null || !Files.isDirectory(base)) {
			return List.of();
		}
		List<RepoRef> repos = new ArrayList<>();
		try (Stream<Path> owners = Files.list(base)) {
			for (Path ownerDir : owners.filter(Files::isDirectory).toList()) {
				String owner = ownerDir.getFileName().toString();
				try (Stream<Path> names = Files.list(ownerDir)) {
					for (Path nameDir : names.filter(Files::isDirectory).toList()) {
						String name = nameDir.getFileName().toString();
						if (name.contains("-tmp-")) {
							continue;
						}
						try (FSDirectory dir = FSDirectory.open(nameDir)) {
							if (DirectoryReader.indexExists(dir)) {
								repos.add(new RepoRef(owner, name));
							}
						} catch (IOException ignored) {
							// skip directories that are not valid Lucene indexes
						}
					}
				} catch (IOException ignored) {
					// skip unreadable owner dir
				}
			}
		} catch (IOException e) {
			log.warn("Failed to list keyword index directories under {}: {}", base, e.getMessage());
		}
		repos.sort(Comparator.comparing(RepoRef::fullName));
		return repos;
	}

	/**
	 * Returns the total number of indexed documents for the specified repository.
	 *
	 * @param repo repository identifier reference
	 * @return document count, or 0 if repository does not exist or index is invalid
	 */
	public int getDocumentCount(RepoRef repo) {
		if (repo == null) {
			return 0;
		}
		Path repoDir = getRepoDir(repo);
		if (!Files.isDirectory(repoDir)) {
			return 0;
		}
		try (FSDirectory dir = FSDirectory.open(repoDir)) {
			if (!DirectoryReader.indexExists(dir)) {
				return 0;
			}
			try (DirectoryReader reader = DirectoryReader.open(dir)) {
				return reader.numDocs();
			}
		} catch (IOException e) {
			log.warn("Could not read document count for {}: {}", repo.fullName(), e.getMessage());
			return 0;
		}
	}

	/**
	 * Returns the last modified timestamp of the repository index directory.
	 *
	 * @param repo repository identifier reference
	 * @return last modified instant, or null if repository directory does not exist
	 */
	public Instant getLastModified(RepoRef repo) {
		if (repo == null) {
			return null;
		}
		Path repoDir = getRepoDir(repo);
		try {
			if (Files.isDirectory(repoDir)) {
				return Files.getLastModifiedTime(repoDir).toInstant();
			}
		} catch (IOException e) {
			log.debug("Could not read last modified time for {}: {}", repo.fullName(), e.getMessage());
		}
		return null;
	}

	@Override
	@PreDestroy
	public void close() {
		for (Map.Entry<String, SearcherManager> entry : searcherManagers.entrySet()) {
			try {
				entry.getValue().close();
			} catch (IOException e) {
				log.warn("Error closing SearcherManager for {}: {}", entry.getKey(), e.getMessage());
			}
		}
		searcherManagers.clear();
	}

	private SearcherManager getOrOpenSearcherManager(RepoRef repo) {
		String repoKey = repo.normalizedKey();
		return searcherManagers.compute(repoKey, (key, existing) -> {
			if (existing != null) {
				return existing;
			}
			Path repoDir = getRepoDir(repo);
			if (!Files.isDirectory(repoDir)) {
				return null;
			}
			try {
				FSDirectory dir = FSDirectory.open(repoDir);
				if (!DirectoryReader.indexExists(dir)) {
					dir.close();
					return null;
				}
				SearcherManager sm = new SearcherManager(dir, null);
				return sm;
			} catch (IOException e) {
				log.warn("Unable to open SearcherManager for {}: {}", repoKey, e.getMessage());
				return null;
			}
		});
	}

	private List<SearchHit> executeSearch(
			IndexSearcher searcher,
			String query,
			int k,
			Set<ChunkType> types,
			String pathPrefix
	) throws IOException {
		Map<String, Float> boosts = new HashMap<>();
		boosts.put("text", 1.0f);
		boosts.put("symbolName", (float) properties.keyword().symbolBoost());
		boosts.put("parentSymbol", (float) properties.keyword().parentSymbolBoost());
		boosts.put("path", (float) properties.keyword().pathBoost());

		MultiFieldQueryParser parser = new MultiFieldQueryParser(
				new String[]{"text", "symbolName", "parentSymbol", "path"},
				codeAnalyzer,
				boosts
		);
		parser.setDefaultOperator(QueryParser.Operator.OR);

		String escapedQuery = QueryParserBase.escape(query.trim());
		Query scoringQuery;
		try {
			if (escapedQuery.isBlank()) {
				scoringQuery = new MatchNoDocsQuery();
			} else {
				scoringQuery = parser.parse(escapedQuery);
			}
		} catch (ParseException e) {
			log.warn("Could not parse escaped query '{}': {}", escapedQuery, e.getMessage());
			scoringQuery = new MatchNoDocsQuery();
		}

		BooleanQuery.Builder rootBuilder = new BooleanQuery.Builder();
		rootBuilder.add(scoringQuery, BooleanClause.Occur.MUST);

		if (types != null && !types.isEmpty()) {
			BooleanQuery.Builder typeBuilder = new BooleanQuery.Builder();
			for (ChunkType type : types) {
				typeBuilder.add(new TermQuery(new Term("chunkType", type.name())), BooleanClause.Occur.SHOULD);
			}
			rootBuilder.add(typeBuilder.build(), BooleanClause.Occur.FILTER);
		}

		if (pathPrefix != null && !pathPrefix.isBlank()) {
			String normalizedPrefix = pathPrefix.replace('\\', '/').trim();
			rootBuilder.add(new PrefixQuery(new Term("pathExact", normalizedPrefix)), BooleanClause.Occur.FILTER);
		}

		TopDocs topDocs = searcher.search(rootBuilder.build(), k);
		List<SearchHit> hits = new ArrayList<>(topDocs.scoreDocs.length);
		for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
			Document doc = searcher.storedFields().document(scoreDoc.doc);
			hits.add(toSearchHit(doc, scoreDoc.score));
		}
		return hits;
	}

	private Document toDocument(RepoRef repo, Chunk chunk) {
		Document doc = new Document();
		doc.add(new StringField("chunkId", chunk.id(), Field.Store.YES));
		doc.add(new StringField("id", ChunkPointMapper.chunkIdToUuid(chunk.id()), Field.Store.YES));
		doc.add(new StringField("repo", repo.normalizedKey(), Field.Store.YES));
		doc.add(new StringField("chunkType", chunk.metadata().chunkType().name(), Field.Store.YES));
		doc.add(new StoredField("sourceType", chunk.metadata().sourceType().name()));
		doc.add(new StoredField("documentId", chunk.documentId()));
		doc.add(new StoredField("chunkIndex", chunk.chunkIndex()));
		doc.add(new StoredField("tokenCount", chunk.metadata().tokenCount()));

		if (chunk.metadata().path() != null) {
			doc.add(new TextField("path", chunk.metadata().path(), Field.Store.YES));
			doc.add(new StringField("pathExact", chunk.metadata().path(), Field.Store.NO));
		}
		if (chunk.metadata().language() != null) {
			doc.add(new StoredField("language", chunk.metadata().language()));
		}
		if (chunk.metadata().symbolName() != null) {
			doc.add(new TextField("symbolName", chunk.metadata().symbolName(), Field.Store.YES));
		}
		if (chunk.metadata().parentSymbol() != null) {
			doc.add(new TextField("parentSymbol", chunk.metadata().parentSymbol(), Field.Store.YES));
		}
		if (chunk.metadata().headingPath() != null) {
			doc.add(new StoredField("headingPath", chunk.metadata().headingPath()));
		}
		if (chunk.metadata().startLine() != null) {
			doc.add(new StoredField("startLine", chunk.metadata().startLine()));
		}
		if (chunk.metadata().endLine() != null) {
			doc.add(new StoredField("endLine", chunk.metadata().endLine()));
		}
		if (chunk.metadata().url() != null) {
			doc.add(new StoredField("url", chunk.metadata().url()));
		}
		if (chunk.metadata().commitSha() != null) {
			doc.add(new StoredField("commitSha", chunk.metadata().commitSha()));
		}
		doc.add(new TextField("text", chunk.text(), Field.Store.YES));
		doc.add(new StoredField("content", chunk.content()));

		return doc;
	}

	private SearchHit toSearchHit(Document doc, float score) {
		String chunkId = doc.get("chunkId");
		String id = doc.get("id");
		if (id == null && chunkId != null) {
			id = ChunkPointMapper.chunkIdToUuid(chunkId);
		}
		String repo = doc.get("repo");
		String runId = doc.get("runId");
		String documentId = doc.get("documentId");
		int chunkIndex = getIntField(doc, "chunkIndex", 0);
		SourceType sourceType = getEnumField(doc, "sourceType", SourceType.class);
		ChunkType chunkType = getEnumField(doc, "chunkType", ChunkType.class);
		String path = doc.get("path");
		String language = doc.get("language");
		String symbolName = doc.get("symbolName");
		String parentSymbol = doc.get("parentSymbol");
		String headingPath = doc.get("headingPath");
		Integer startLine = getIntegerField(doc, "startLine");
		Integer endLine = getIntegerField(doc, "endLine");
		int tokenCount = getIntField(doc, "tokenCount", 0);
		String url = doc.get("url");
		String commitSha = doc.get("commitSha");
		String text = doc.get("text");
		String content = doc.get("content");

		return new SearchHit(
				id,
				(double) score,
				repo,
				runId != null ? runId : "",
				chunkId,
				documentId,
				chunkIndex,
				sourceType,
				chunkType,
				path,
				language,
				symbolName,
				parentSymbol,
				headingPath,
				startLine,
				endLine,
				tokenCount,
				url,
				commitSha,
				text,
				content
		);
	}

	private synchronized void atomicReplace(Path tempDir, Path repoDir) throws IOException {
		if (!Files.exists(repoDir)) {
			try {
				Files.move(tempDir, repoDir, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(tempDir, repoDir);
			}
			return;
		}

		Path backupDir = repoDir.resolveSibling(repoDir.getFileName().toString() + "-old-" + UUID.randomUUID());
		Files.move(repoDir, backupDir);
		try {
			Files.move(tempDir, repoDir);
		} catch (Exception e) {
			try {
				Files.move(backupDir, repoDir);
			} catch (Exception restoreEx) {
				log.error("Failed to restore backup directory {} to {}: {}", backupDir, repoDir, restoreEx.getMessage(), restoreEx);
			}
			throw e;
		}
		deleteRecursively(backupDir);
	}

	private void deleteRecursively(Path path) {
		if (path == null || !Files.exists(path)) {
			return;
		}
		try (var stream = Files.walk(path)) {
			stream.sorted(Comparator.reverseOrder())
					.forEach(p -> {
						try {
							Files.deleteIfExists(p);
						} catch (IOException e) {
							log.warn("Failed to delete file {}: {}", p, e.getMessage());
						}
					});
		} catch (IOException e) {
			log.warn("Failed to walk directory for deletion {}: {}", path, e.getMessage());
		}
	}

	private int getIntField(Document doc, String fieldName, int fallback) {
		IndexableField field = doc.getField(fieldName);
		if (field != null && field.numericValue() != null) {
			return field.numericValue().intValue();
		}
		String s = doc.get(fieldName);
		if (s != null && !s.isBlank()) {
			try {
				return Integer.parseInt(s);
			} catch (NumberFormatException ignored) {
			}
		}
		return fallback;
	}

	private Integer getIntegerField(Document doc, String fieldName) {
		IndexableField field = doc.getField(fieldName);
		if (field != null && field.numericValue() != null) {
			return field.numericValue().intValue();
		}
		String s = doc.get(fieldName);
		if (s != null && !s.isBlank()) {
			try {
				return Integer.parseInt(s);
			} catch (NumberFormatException ignored) {
			}
		}
		return null;
	}

	private <E extends Enum<E>> E getEnumField(Document doc, String fieldName, Class<E> enumClass) {
		String val = doc.get(fieldName);
		if (val == null || val.isBlank()) {
			return null;
		}
		try {
			return Enum.valueOf(enumClass, val);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}
}
