package io.jenkins.plugins.forensics.git.miner;

import java.io.IOException;
import java.util.List;
import java.util.stream.Stream;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Test;

import edu.hm.hafner.util.FilteredLog;

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
        runTest((repository, git) -> {
            List<RevCommit> actualCommits = findCommits(repository, git, "-");

            assertThat(actualCommits).hasSize(1);
            assertThat(actualCommits.get(0).getId()).isEqualTo(getHeadCommit());
        });
    }

    /** Verifies that additional commits are recorded and sorted correctly. */
    @Test
    public void shouldFindAdditionalCommits() {
        ObjectId start = getHeadCommit();
        writeFileAsAuthorFoo("Middle");
        ObjectId middle = getHeadCommit();

        runTest((repository, git) -> {
            List<RevCommit> actualCommits = findCommits(repository, git, "-");
            assertThat(actualCommits).hasSize(2);
            assertThat(extractCommitIds(actualCommits)).containsExactly(middle, start);
        });

        writeFileAsAuthorBar("Second");
        ObjectId head = getHeadCommit();

        runTest((repository, git) -> {
            List<RevCommit> actualCommits = findCommits(repository, git, "-");
            assertThat(actualCommits).hasSize(3);
            assertThat(extractCommitIds(actualCommits)).containsExactly(head, middle, start);
        });
    }

    /** Verifies that commits are recorded only up to a given commit. */
    @Test
    public void shouldFindCommitsUpToGivenCommit() {
        ObjectId start = getHeadCommit();
        writeFileAsAuthorFoo("Middle");
        ObjectId middle = getHeadCommit();
        writeFileAsAuthorBar("Head");
        ObjectId head = getHeadCommit();

        runTest((repository, git) -> {
            List<RevCommit> upToHeadCommits = findCommits(repository, git, start.getName());
            assertThat(upToHeadCommits).hasSize(2);
            assertThat(extractCommitIds(upToHeadCommits)).containsExactly(head, middle);

            List<RevCommit> upToMiddleCommits = findCommits(repository, git, middle.getName());
            assertThat(upToMiddleCommits).hasSize(1);
            assertThat(extractCommitIds(upToMiddleCommits)).containsExactly(head);
        });
    }

    private Stream<ObjectId> extractCommitIds(final List<RevCommit> actualCommits) {
        return actualCommits.stream().map(RevCommit::getId);
    }

    private List<RevCommit> findCommits(final Repository repository, final Git git, final String lastCommitId)
            throws IOException, GitAPIException {
        CommitCollector collector = new CommitCollector();

        return collector.findAllCommits(repository, git, lastCommitId, new FilteredLog("unused"));
    }
}
