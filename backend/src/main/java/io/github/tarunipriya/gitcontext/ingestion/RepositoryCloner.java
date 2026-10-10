package io.github.tarunipriya.gitcontext.ingestion;

import io.github.tarunipriya.gitcontext.config.GitContextProperties;
import io.github.tarunipriya.gitcontext.model.RepoRef;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.FetchCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Service responsible for cloning remote git repositories and keeping local checkouts synchronized.
 */
@Component
public class RepositoryCloner {

	private static final Logger log = LoggerFactory.getLogger(RepositoryCloner.class);

	private final GitContextProperties properties;

	/**
	 * Creates a new {@link RepositoryCloner}.
	 *
	 * @param properties GitContext configuration properties
	 */
	public RepositoryCloner(GitContextProperties properties) {
		this.properties = Objects.requireNonNull(properties, "properties must not be null");
	}

	/**
	 * Represents an active, checked-out local repository.
	 *
	 * @param repo repository identifier reference
	 * @param localPath absolute path to the local repository directory
	 * @param headCommitSha 40-character hexadecimal SHA of the HEAD commit
	 * @param defaultBranch name of the checked-out branch (e.g. "main" or "master")
	 */
	public record ClonedRepo(RepoRef repo, Path localPath, String headCommitSha, String defaultBranch) {
		public ClonedRepo {
			Objects.requireNonNull(repo, "repo must not be null");
			Objects.requireNonNull(localPath, "localPath must not be null");
			Objects.requireNonNull(headCommitSha, "headCommitSha must not be null");
			Objects.requireNonNull(defaultBranch, "defaultBranch must not be null");
		}
	}

	/**
	 * Clones or updates the specified repository using its canonical HTTPS clone URL.
	 *
	 * @param repo repository identifier
	 * @return descriptor of the cloned repository
	 * @throws GitException if cloning or updating fails
	 */
	public ClonedRepo cloneOrUpdate(RepoRef repo) {
		Objects.requireNonNull(repo, "repo must not be null");
		return cloneOrUpdate(repo, repo.httpsCloneUrl());
	}

	/**
	 * Clones or updates the specified repository from an explicit remote URL.
	 *
	 * @param repo repository identifier
	 * @param remoteUrl remote repository URL (HTTPS, SSH, or local file:// URL)
	 * @return descriptor of the cloned repository
	 * @throws GitException if cloning or updating fails
	 */
	public ClonedRepo cloneOrUpdate(RepoRef repo, String remoteUrl) {
		Objects.requireNonNull(repo, "repo must not be null");
		Objects.requireNonNull(remoteUrl, "remoteUrl must not be null");

		Path targetDir = properties.reposDir().resolve(repo.owner()).resolve(repo.name());
		CredentialsProvider credentialsProvider = getCredentialsProvider(remoteUrl);

		try {
			if (!Files.exists(targetDir.resolve(".git"))) {
				log.info("Cloning repository {} into {}", repo.fullName(), targetDir);
				Files.createDirectories(targetDir.getParent());

				CloneCommand cloneCommand = Git.cloneRepository()
						.setURI(remoteUrl)
						.setDirectory(targetDir.toFile())
						.setCloneAllBranches(false);

				if (credentialsProvider != null) {
					cloneCommand.setCredentialsProvider(credentialsProvider);
				}

				try (Git git = cloneCommand.call()) {
					return describeRepo(repo, git.getRepository(), targetDir);
				}
			} else {
				log.info("Updating existing repository {} at {}", repo.fullName(), targetDir);
				try (Git git = Git.open(targetDir.toFile())) {
					FetchCommand fetchCommand = git.fetch().setRemote("origin");
					if (credentialsProvider != null) {
						fetchCommand.setCredentialsProvider(credentialsProvider);
					}
					fetchCommand.call();

					Repository repository = git.getRepository();
					String targetRef = determineTargetRef(repository);
					git.reset().setMode(ResetType.HARD).setRef(targetRef).call();
					git.clean().setCleanDirectories(true).setForce(true).call();

					return describeRepo(repo, repository, targetDir);
				}
			}
		} catch (GitAPIException | IOException e) {
			throw new IllegalStateException("Failed to clone or update repository " + repo.fullName() + " from " + remoteUrl, e);
		}
	}

	private CredentialsProvider getCredentialsProvider(String remoteUrl) {
		if (properties.hasGithubToken() && (remoteUrl.startsWith("http://") || remoteUrl.startsWith("https://"))) {
			return new UsernamePasswordCredentialsProvider("x-access-token", properties.githubToken());
		}
		return null;
	}

	private static String determineTargetRef(Repository repository) throws IOException {
		Ref remoteHead = repository.findRef("refs/remotes/origin/HEAD");
		if (remoteHead != null && remoteHead.isSymbolic()) {
			return remoteHead.getTarget().getName();
		}
		if (repository.findRef("refs/remotes/origin/main") != null) {
			return "refs/remotes/origin/main";
		}
		if (repository.findRef("refs/remotes/origin/master") != null) {
			return "refs/remotes/origin/master";
		}
		return "FETCH_HEAD";
	}

	private static ClonedRepo describeRepo(RepoRef repo, Repository repository, Path targetDir) throws IOException {
		ObjectId headId = repository.resolve(Constants.HEAD);
		if (headId == null) {
			throw new IllegalStateException("Repository has no HEAD commit");
		}
		String headSha = headId.name();
		String branch = repository.getBranch();
		return new ClonedRepo(repo, targetDir.toAbsolutePath().normalize(), headSha, branch != null ? branch : "main");
	}
}
