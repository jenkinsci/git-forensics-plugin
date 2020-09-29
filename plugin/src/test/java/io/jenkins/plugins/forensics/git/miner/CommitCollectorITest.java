package io.jenkins.plugins.forensics.git.miner;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Test;

import org.jenkinsci.plugins.gitclient.GitClient;
import org.jenkinsci.plugins.gitclient.RepositoryCallback;

import io.jenkins.plugins.forensics.git.util.GitITest;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests the class {@link CommitCollector}.
 *
 * @author Ullrich Hafner
 */
public class CommitCollectorITest extends GitITest {
    /** Verifies that the initial repository contains a single commit. */
    @Test
    public void shouldFindInitialCommits() {
        runCollector((repository, git) -> {
            CommitCollector collector = new CommitCollector(repository, git, "-");

            List<RevCommit> actualCommits = collector.findAllCommits();
            assertThat(actualCommits).hasSize(1);
            assertThat(actualCommits.get(0).getId()).isEqualTo(getHeadCommit());
        });
    }

    /** Verifies that additional commits are recorded and sorted correctly. */
    @Test
    public void shouldFindAdditionalCommits() {
        List<ObjectId> expectedCommits = new ArrayList<>();
        expectedCommits.add(getHeadCommit());

        writeFileAsAuthorFoo("First");
        expectedCommits.add(getHeadCommit());

        runCollector((repository, git) -> {
            CommitCollector collector = new CommitCollector(repository, git, "-");

            List<RevCommit> actualCommits = collector.findAllCommits();
            assertThat(actualCommits).hasSize(2);
            assertThat(actualCommits.stream().map(RevCommit::getId)).containsExactlyElementsOf(expectedCommits);
        });

        writeFileAsAuthorBar("Second");
        expectedCommits.add(getHeadCommit());

        runCollector((repository, git) -> {
            CommitCollector collector = new CommitCollector(repository, git, "-");

            List<RevCommit> actualCommits = collector.findAllCommits();
            assertThat(actualCommits).hasSize(3);
            assertThat(actualCommits.stream().map(RevCommit::getId)).containsExactlyElementsOf(expectedCommits);
        });
    }

    /** Verifies that commits are recorded only up to a given commit. */
    @Test
    public void shouldFindCommitsUpToGivenCommit() {
        List<ObjectId> expectedCommits = new ArrayList<>();
        expectedCommits.add(getHeadCommit());
        writeFileAsAuthorFoo("First");
        expectedCommits.add(getHeadCommit());
        writeFileAsAuthorBar("Second");
        expectedCommits.add(getHeadCommit());

        runCollector((repository, git) -> {
            CommitCollector allCollector = new CommitCollector(repository, git, expectedCommits.get(0).getName());
            List<RevCommit> actualCommits = allCollector.findAllCommits();
            assertThat(actualCommits).hasSize(3);
            assertThat(actualCommits.stream().map(RevCommit::getId)).containsExactlyElementsOf(expectedCommits);

            expectedCommits.remove(0); // remove first commit
            CommitCollector twoCollector = new CommitCollector(repository, git, expectedCommits.get(0).getName());
            List<RevCommit> twoCommits = twoCollector.findAllCommits();
            assertThat(twoCommits).hasSize(2);
            assertThat(twoCommits.stream().map(RevCommit::getId)).containsExactlyElementsOf(expectedCommits);

            expectedCommits.remove(0); // remove second commit
            CommitCollector oneCollector = new CommitCollector(repository, git, expectedCommits.get(0).getName());
            List<RevCommit> oneCommits = oneCollector.findAllCommits();
            assertThat(oneCommits).hasSize(1);
            assertThat(oneCommits.stream().map(RevCommit::getId)).containsExactlyElementsOf(expectedCommits);
        });
    }

    private void runCollector(final GitTestCase testCase) {
        try {
            GitClient gitClient = createGitClient();
            gitClient.withRepository((RepositoryCallback<Void>) (repository, virtualChannel) -> {
                try (Git git = new Git(repository)) {
                    testCase.run(repository, git);
                }
                catch (GitAPIException exception) {
                    // ignore
                }
                return null;
            });
        }
        catch (IOException exception) {
            throw new AssertionError(exception);
        }
        catch (InterruptedException exception) {
            exception.printStackTrace();
        }
    }

    @FunctionalInterface
    private interface GitTestCase {
        void run(Repository repository, Git git) throws GitAPIException, IOException;
    }
}
