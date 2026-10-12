package io.github.tarunipriya.gitcontext.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.tarunipriya.gitcontext.ingestion.RepositoryCloner.ClonedRepo;
import io.github.tarunipriya.gitcontext.ingestion.github.GitHubApiException;
import io.github.tarunipriya.gitcontext.ingestion.github.IssueIngestor;
import io.github.tarunipriya.gitcontext.ingestion.github.PullRequestIngestor;
import io.github.tarunipriya.gitcontext.model.Document;
import io.github.tarunipriya.gitcontext.model.DocumentMetadata;
import io.github.tarunipriya.gitcontext.model.RepoRef;
import io.github.tarunipriya.gitcontext.model.SourceType;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IngestionServiceTest {

	@Mock
	private RepositoryCloner repositoryCloner;

	@Mock
	private FileIngestor fileIngestor;

	@Mock
	private CommitIngestor commitIngestor;

	@Mock
	private PullRequestIngestor pullRequestIngestor;

	@Mock
	private IssueIngestor issueIngestor;

	private IngestionService ingestionService;
	private final RepoRef repoRef = new RepoRef("testowner", "testrepo");

	@BeforeEach
	void setUp() {
		ingestionService = new IngestionService(
				repositoryCloner,
				fileIngestor,
				commitIngestor,
				pullRequestIngestor,
				issueIngestor
		);
	}

	@Test
	@DisplayName("Happy path returns combined documents and correct counts by source type")
	void happyPath_returnsCombinedDocumentsAndCorrectCounts() {
		String headSha = "0123456789abcdef0123456789abcdef01234567";
		ClonedRepo clonedRepo = new ClonedRepo(repoRef, Path.of("mock/path"), headSha, "main");
		when(repositoryCloner.cloneOrUpdate(repoRef)).thenReturn(clonedRepo);

		Document codeDoc1 = createDoc(SourceType.CODE, "src/App.java", "code 1");
		Document codeDoc2 = createDoc(SourceType.CODE, "src/Util.java", "code 2");
		Document commitDoc = createDoc(SourceType.COMMIT, "sha1", "commit message");
		Document prDoc = createDoc(SourceType.PULL_REQUEST, "pr-1", "pr content");
		Document issueDoc = createDoc(SourceType.ISSUE, "issue-1", "issue content");

		when(fileIngestor.ingest(clonedRepo)).thenReturn(List.of(codeDoc1, codeDoc2));
		when(commitIngestor.ingest(clonedRepo)).thenReturn(List.of(commitDoc));
		when(pullRequestIngestor.ingest(repoRef)).thenReturn(List.of(prDoc));
		when(issueIngestor.ingest(repoRef)).thenReturn(List.of(issueDoc));

		IngestionResult result = ingestionService.ingest(repoRef);

		assertThat(result.repo()).isEqualTo(repoRef);
		assertThat(result.headCommitSha()).isEqualTo(headSha);
		assertThat(result.documents()).containsExactly(codeDoc1, codeDoc2, commitDoc, prDoc, issueDoc);
		assertThat(result.countsBySourceType().get(SourceType.CODE)).isEqualTo(2);
		assertThat(result.countsBySourceType().get(SourceType.COMMIT)).isEqualTo(1);
		assertThat(result.countsBySourceType().get(SourceType.PULL_REQUEST)).isEqualTo(1);
		assertThat(result.countsBySourceType().get(SourceType.ISSUE)).isEqualTo(1);
		assertThat(result.warnings()).isEmpty();
		assertThat(result.stageDurationsMillis()).containsKeys("clone", "files", "commits", "pullRequests", "issues");
		assertThat(result.totalDurationMillis()).isGreaterThanOrEqualTo(0);
	}

	@Test
	@DisplayName("GitHubApiException becomes a warning while keeping local documents")
	void gitHubFailure_becomesWarningAndPreservesLocalDocs() {
		String headSha = "abcdef0123456789abcdef0123456789abcdef01";
		ClonedRepo clonedRepo = new ClonedRepo(repoRef, Path.of("mock/path"), headSha, "main");
		when(repositoryCloner.cloneOrUpdate(repoRef)).thenReturn(clonedRepo);

		Document fileDoc = createDoc(SourceType.CODE, "src/Main.java", "main code");
		Document commitDoc = createDoc(SourceType.COMMIT, "sha99", "initial commit");
		Document issueDoc = createDoc(SourceType.ISSUE, "issue-12", "issue doc");

		when(fileIngestor.ingest(clonedRepo)).thenReturn(List.of(fileDoc));
		when(commitIngestor.ingest(clonedRepo)).thenReturn(List.of(commitDoc));
		when(pullRequestIngestor.ingest(repoRef)).thenThrow(new GitHubApiException(403, "GitHub API rate limit exceeded"));
		when(issueIngestor.ingest(repoRef)).thenReturn(List.of(issueDoc));

		IngestionResult result = ingestionService.ingest(repoRef);

		assertThat(result.documents()).containsExactly(fileDoc, commitDoc, issueDoc);
		assertThat(result.countsBySourceType().get(SourceType.CODE)).isEqualTo(1);
		assertThat(result.countsBySourceType().get(SourceType.COMMIT)).isEqualTo(1);
		assertThat(result.countsBySourceType().get(SourceType.PULL_REQUEST)).isZero();
		assertThat(result.countsBySourceType().get(SourceType.ISSUE)).isEqualTo(1);
		assertThat(result.warnings()).hasSize(1);
		assertThat(result.warnings().getFirst()).contains("Pull requests stage failed: GitHub API rate limit exceeded");
	}

	@Test
	@DisplayName("Clone failure throws and prevents downstream stages from executing")
	void cloneFailure_throwsExceptionAndStopsPipeline() {
		when(repositoryCloner.cloneOrUpdate(repoRef)).thenThrow(new RuntimeException("Git clone failed"));

		assertThatThrownBy(() -> ingestionService.ingest(repoRef))
				.isInstanceOf(RuntimeException.class)
				.hasMessageContaining("Git clone failed");

		verifyNoInteractions(fileIngestor, commitIngestor, pullRequestIngestor, issueIngestor);
	}

	private Document createDoc(SourceType sourceType, String key, String content) {
		DocumentMetadata metadata = new DocumentMetadata(
				repoRef.fullName(),
				sourceType,
				key,
				"https://example.com/" + key,
				"author",
				Instant.now(),
				null,
				null,
				null,
				null,
				"Title " + key,
				Map.of()
		);
		return Document.create(content, metadata, key);
	}
}
