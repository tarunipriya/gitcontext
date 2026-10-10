package io.github.tarunipriya.gitcontext.ingestion;

import io.github.tarunipriya.gitcontext.config.GitContextProperties;
import io.github.tarunipriya.gitcontext.ingestion.RepositoryCloner.ClonedRepo;
import io.github.tarunipriya.gitcontext.model.Document;
import io.github.tarunipriya.gitcontext.model.DocumentMetadata;
import io.github.tarunipriya.gitcontext.model.SourceType;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Ingests git commit history into {@link Document} records.
 */
@Component
public class CommitIngestor {

	private static final Logger log = LoggerFactory.getLogger(CommitIngestor.class);

	private final GitContextProperties properties;

	/**
	 * Creates a new {@link CommitIngestor}.
	 *
	 * @param properties GitContext configuration properties
	 */
	public CommitIngestor(GitContextProperties properties) {
		this.properties = Objects.requireNonNull(properties, "properties must not be null");
	}

	/**
	 * Reads the latest commits up to {@code maxCommits} on the repository's HEAD branch.
	 *
	 * @param clonedRepo descriptor of the cloned repository
	 * @return list of ingested commit {@link Document} records
	 */
	public List<Document> ingest(ClonedRepo clonedRepo) {
		Objects.requireNonNull(clonedRepo, "clonedRepo must not be null");

		List<Document> documents = new ArrayList<>();
		int maxCommits = properties.ingestion().maxCommits();
		int maxDiffChars = properties.ingestion().maxDiffChars();

		try (Git git = Git.open(clonedRepo.localPath().toFile())) {
			Repository repository = git.getRepository();
			ObjectId headId = repository.resolve(Constants.HEAD);
			if (headId == null) {
				return documents;
			}

			try (RevWalk revWalk = new RevWalk(repository)) {
				RevCommit headCommit = revWalk.parseCommit(headId);
				revWalk.markStart(headCommit);

				int count = 0;
				for (RevCommit commit : revWalk) {
					if (count++ >= maxCommits) {
						break;
					}

					String sha = commit.name();
					PersonIdent authorIdent = commit.getAuthorIdent();
					String author = authorIdent != null ? authorIdent.getName() : null;
					Instant timestamp = authorIdent != null ? authorIdent.getWhenAsInstant() : null;
					String title = commit.getShortMessage();
					String fullMessage = commit.getFullMessage().trim();

					ByteArrayOutputStream diffOut = new ByteArrayOutputStream();
					List<String> filesChangedList = new ArrayList<>();

					try (DiffFormatter diffFormatter = new DiffFormatter(diffOut)) {
						diffFormatter.setRepository(repository);
						diffFormatter.setDetectRenames(true);
						diffFormatter.setContext(3);

						List<DiffEntry> diffs;
						if (commit.getParentCount() > 0) {
							RevCommit parent = revWalk.parseCommit(commit.getParent(0).getId());
							diffs = diffFormatter.scan(parent.getTree(), commit.getTree());
						} else {
							diffs = diffFormatter.scan(null, commit.getTree());
						}

						for (DiffEntry diff : diffs) {
							String path = diff.getChangeType() == DiffEntry.ChangeType.DELETE
									? diff.getOldPath()
									: diff.getNewPath();
							filesChangedList.add("- " + diff.getChangeType().name() + " " + path.replace('\\', '/'));
							diffFormatter.format(diff);
						}
					}

					StringBuilder contentBuilder = new StringBuilder();
					contentBuilder.append(fullMessage);

					if (!filesChangedList.isEmpty()) {
						contentBuilder.append("\n\nFiles changed:\n");
						for (String fileLine : filesChangedList) {
							contentBuilder.append(fileLine).append("\n");
						}
					}

					String diffString = diffOut.toString(StandardCharsets.UTF_8).trim();
					if (!diffString.isEmpty()) {
						contentBuilder.append("\n\n");
						if (diffString.length() > maxDiffChars) {
							contentBuilder.append(diffString, 0, maxDiffChars);
							contentBuilder.append("\n[diff truncated]");
						} else {
							contentBuilder.append(diffString);
						}
					}

					String url = "https://github.com/" + clonedRepo.repo().fullName() + "/commit/" + sha;
					Map<String, String> extra = Map.of("filesChanged", String.valueOf(filesChangedList.size()));

					DocumentMetadata metadata = new DocumentMetadata(
							clonedRepo.repo().fullName(),
							SourceType.COMMIT,
							null,
							url,
							author,
							timestamp,
							sha,
							null,
							null,
							null,
							title,
							extra
					);

					Document doc = Document.create(contentBuilder.toString().trim(), metadata, sha);
					documents.add(doc);
				}
			}
		} catch (IOException e) {
			throw new IllegalStateException("Failed to ingest commits for repository " + clonedRepo.repo().fullName(), e);
		}

		log.info("Ingested {} commit documents for repository {}", documents.size(), clonedRepo.repo().fullName());
		return documents;
	}
}
