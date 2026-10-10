package io.github.tarunipriya.gitcontext.ingestion;

import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.util.io.DisabledOutputStream;

/**
 * An immutable index mapping file paths to their most recent commit information within a bounded commit window.
 *
 * <h2>Single Walk vs. Per-File Log Queries</h2>
 * <p>A naive approach to resolving last-modified metadata is querying git log individually for each file
 * (e.g. {@code git.log().addPath(filePath).setMaxCount(1).call()}). For a repository with 5,000 files, this requires
 * 5,000 discrete commit graph traversals and tree parsing operations (an \(O(N_{\text{files}} \times C)\) complexity),
 * which can take tens of seconds or even minutes.
 *
 * <p>In contrast, {@link LastModifiedIndex} performs a <b>single pass</b> over the commit history starting from {@code HEAD}
 * backwards up to {@code maxCommits}. Because traversal proceeds in reverse chronological order (newest to oldest), the
 * first time a file path appears in a commit's tree diff is guaranteed to be its latest revision within that window.
 * This reduces total complexity to \(O(C \times \text{filesPerCommit})\), building the complete last-modified index in
 * milliseconds.
 */
public final class LastModifiedIndex {

	/**
	 * Metadata about the commit that last touched a file.
	 *
	 * @param commitSha 40-character hexadecimal commit SHA
	 * @param author name of the commit author
	 * @param timestamp timestamp of the commit
	 */
	public record CommitInfo(String commitSha, String author, Instant timestamp) {
		public CommitInfo {
			Objects.requireNonNull(commitSha, "commitSha must not be null");
		}
	}

	private final Map<String, CommitInfo> pathToCommit;

	private LastModifiedIndex(Map<String, CommitInfo> pathToCommit) {
		this.pathToCommit = Collections.unmodifiableMap(new HashMap<>(pathToCommit));
	}

	/**
	 * Builds a {@link LastModifiedIndex} by performing a single reverse-chronological commit walk.
	 *
	 * @param repository JGit repository instance
	 * @param maxCommits maximum number of historical commits to inspect
	 * @return a populated {@link LastModifiedIndex}
	 * @throws IOException if git objects or trees cannot be read
	 */
	public static LastModifiedIndex build(Repository repository, int maxCommits) throws IOException {
		Objects.requireNonNull(repository, "repository must not be null");
		Map<String, CommitInfo> index = new HashMap<>();

		ObjectId headId = repository.resolve(Constants.HEAD);
		if (headId == null) {
			return new LastModifiedIndex(index);
		}

		try (RevWalk revWalk = new RevWalk(repository);
			 DiffFormatter diffFormatter = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
			diffFormatter.setRepository(repository);
			diffFormatter.setDetectRenames(true);

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
				CommitInfo info = new CommitInfo(sha, author, timestamp);

				if (commit.getParentCount() > 0) {
					RevCommit parent = revWalk.parseCommit(commit.getParent(0).getId());
					List<DiffEntry> diffs = diffFormatter.scan(parent.getTree(), commit.getTree());
					for (DiffEntry diff : diffs) {
						if (diff.getNewPath() != null && !DiffEntry.DEV_NULL.equals(diff.getNewPath())) {
							index.putIfAbsent(diff.getNewPath().replace('\\', '/'), info);
						}
						if (diff.getOldPath() != null && !DiffEntry.DEV_NULL.equals(diff.getOldPath())) {
							index.putIfAbsent(diff.getOldPath().replace('\\', '/'), info);
						}
					}
				} else {
					// Initial commit with no parent: all files in its tree were introduced here
					try (TreeWalk treeWalk = new TreeWalk(repository)) {
						treeWalk.addTree(commit.getTree());
						treeWalk.setRecursive(true);
						while (treeWalk.next()) {
							index.putIfAbsent(treeWalk.getPathString().replace('\\', '/'), info);
						}
					}
				}
			}
		}

		return new LastModifiedIndex(index);
	}

	/**
	 * Retrieves the newest commit information for the given POSIX relative path.
	 *
	 * @param posixPath relative file path in POSIX format
	 * @return optional containing the {@link CommitInfo} if the file was touched within the walked commit window
	 */
	public Optional<CommitInfo> get(String posixPath) {
		if (posixPath == null) {
			return Optional.empty();
		}
		return Optional.ofNullable(pathToCommit.get(posixPath.replace('\\', '/')));
	}
}
