package io.github.tarunipriya.gitcontext.ingestion;

import io.github.tarunipriya.gitcontext.config.GitContextProperties;
import io.github.tarunipriya.gitcontext.ingestion.RepositoryCloner.ClonedRepo;
import io.github.tarunipriya.gitcontext.model.Document;
import io.github.tarunipriya.gitcontext.model.DocumentMetadata;
import io.github.tarunipriya.gitcontext.model.SourceType;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Ingests source code and documentation files from a cloned working tree into {@link Document} records.
 */
@Component
public class FileIngestor {

	private static final Logger log = LoggerFactory.getLogger(FileIngestor.class);

	private final GitContextProperties properties;
	private final FileFilter fileFilter;
	private final Set<String> docExtensionsLower;

	/**
	 * Creates a new {@link FileIngestor}.
	 *
	 * @param properties GitContext configuration properties
	 * @param fileFilter candidate file filter
	 */
	public FileIngestor(GitContextProperties properties, FileFilter fileFilter) {
		this.properties = Objects.requireNonNull(properties, "properties must not be null");
		this.fileFilter = Objects.requireNonNull(fileFilter, "fileFilter must not be null");
		this.docExtensionsLower = new HashSet<>();
		for (String ext : properties.ingestion().docExtensions()) {
			this.docExtensionsLower.add(ext.toLowerCase(Locale.ROOT));
		}
	}

	/**
	 * Ingests all qualifying files from the cloned repository working tree.
	 *
	 * @param clonedRepo descriptor of the cloned repository
	 * @return list of ingested {@link Document} records sorted by POSIX path
	 */
	public List<Document> ingest(ClonedRepo clonedRepo) {
		Objects.requireNonNull(clonedRepo, "clonedRepo must not be null");

		final LastModifiedIndex lastModifiedIndex = buildLastModifiedIndex(clonedRepo);
		List<Document> documents = new ArrayList<>();
		Path repoRoot = clonedRepo.localPath();

		try (Stream<Path> walk = Files.walk(repoRoot)) {
			walk.filter(path -> fileFilter.shouldIngest(repoRoot, path))
					.forEach(filePath -> {
						Optional<String> contentOpt = readFileUtf8(filePath);
						if (contentOpt.isEmpty()) {
							return;
						}

						String content = contentOpt.get();
						Path relative = repoRoot.relativize(filePath);
						String posixPath = relative.toString().replace('\\', '/');

						SourceType sourceType = isDocExtension(posixPath) ? SourceType.DOC : SourceType.CODE;
						String language = LanguageDetector.detectLanguage(posixPath).orElse(null);
						String url = "https://github.com/" + clonedRepo.repo().fullName() + "/blob/" +
								clonedRepo.headCommitSha() + "/" + posixPath;

						String commitSha = clonedRepo.headCommitSha();
						String author = null;
						Instant timestamp = null;

						if (lastModifiedIndex != null) {
							Optional<LastModifiedIndex.CommitInfo> infoOpt = lastModifiedIndex.get(posixPath);
							if (infoOpt.isPresent()) {
								LastModifiedIndex.CommitInfo info = infoOpt.get();
								commitSha = info.commitSha();
								author = info.author();
								timestamp = info.timestamp();
							}
						}

						String title = filePath.getFileName() != null ? filePath.getFileName().toString() : posixPath;

						DocumentMetadata metadata = new DocumentMetadata(
								clonedRepo.repo().fullName(),
								sourceType,
								posixPath,
								url,
								author,
								timestamp,
								commitSha,
								null,
								null,
								language,
								title,
								Map.of()
						);

						Document document = Document.create(content, metadata, posixPath);
						documents.add(document);
					});
		} catch (IOException e) {
			throw new IllegalStateException("Failed to traverse working tree of " + clonedRepo.repo().fullName(), e);
		}

		documents.sort(Comparator.comparing(doc -> doc.metadata().path()));
		log.info("Ingested {} file documents for repository {}", documents.size(), clonedRepo.repo().fullName());
		return documents;
	}

	private LastModifiedIndex buildLastModifiedIndex(ClonedRepo clonedRepo) {
		try (Git git = Git.open(clonedRepo.localPath().toFile())) {
			Repository repository = git.getRepository();
			return LastModifiedIndex.build(repository, properties.ingestion().maxCommits());
		} catch (IOException e) {
			log.warn("Failed to build LastModifiedIndex for {}: {}", clonedRepo.repo().fullName(), e.getMessage());
			return null;
		}
	}

	private boolean isDocExtension(String posixPath) {
		String ext = LanguageDetector.extractExtension(posixPath).toLowerCase(Locale.ROOT);
		return docExtensionsLower.contains(ext);
	}

	private static Optional<String> readFileUtf8(Path path) {
		try {
			byte[] bytes = Files.readAllBytes(path);
			CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
					.onMalformedInput(CodingErrorAction.REPORT)
					.onUnmappableCharacter(CodingErrorAction.REPORT);
			CharBuffer buffer = decoder.decode(ByteBuffer.wrap(bytes));
			return Optional.of(buffer.toString());
		} catch (CharacterCodingException e) {
			log.debug("Skipping non-UTF8 decodable file {}: {}", path, e.getMessage());
			return Optional.empty();
		} catch (IOException e) {
			log.debug("Skipping unreadable file {}: {}", path, e.getMessage());
			return Optional.empty();
		}
	}
}
