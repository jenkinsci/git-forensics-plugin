package io.jenkins.plugins.forensics.git.miner;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Test;

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

        runTest((repository, git) -> {
            CommitCollector collector = new CommitCollector(repository, git, "-");

            List<RevCommit> actualCommits = collector.findAllCommits();
            assertThat(actualCommits).hasSize(2);
            assertThat(extractCommitIds(actualCommits)).containsExactlyElementsOf(expectedCommits);
        });

        writeFileAsAuthorBar("Second");
        expectedCommits.add(getHeadCommit());

        runTest((repository, git) -> {
            CommitCollector collector = new CommitCollector(repository, git, "-");

            List<RevCommit> actualCommits = collector.findAllCommits();
            assertThat(actualCommits).hasSize(3);
            assertThat(extractCommitIds(actualCommits)).containsExactlyElementsOf(expectedCommits);
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

        runTest((repository, git) -> {
            CommitCollector allCollector = new CommitCollector(repository, git, expectedCommits.get(0).getName());
            List<RevCommit> actualCommits = allCollector.findAllCommits();
            assertThat(actualCommits).hasSize(3);
            assertThat(extractCommitIds(actualCommits)).containsExactlyElementsOf(expectedCommits);

            expectedCommits.remove(0); // remove first commit
            CommitCollector twoCollector = new CommitCollector(repository, git, expectedCommits.get(0).getName());
            List<RevCommit> twoCommits = twoCollector.findAllCommits();
            assertThat(twoCommits).hasSize(2);
            assertThat(extractCommitIds(twoCommits)).containsExactlyElementsOf(expectedCommits);

            expectedCommits.remove(0); // remove second commit
            CommitCollector oneCollector = new CommitCollector(repository, git, expectedCommits.get(0).getName());
            List<RevCommit> oneCommits = oneCollector.findAllCommits();
            assertThat(oneCommits).hasSize(1);
            assertThat(extractCommitIds(oneCommits)).containsExactlyElementsOf(expectedCommits);
        });
    }

    private Stream<ObjectId> extractCommitIds(final List<RevCommit> actualCommits) {
        return actualCommits.stream().map(RevCommit::getId);
    }

}
