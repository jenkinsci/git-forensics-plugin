package io.jenkins.plugins.forensics.git.reference;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;
import org.junit.jupiter.api.Test;

import edu.hm.hafner.util.FilteredLog;

import java.io.IOException;

import io.jenkins.plugins.forensics.git.util.GitITest;
import io.jenkins.plugins.forensics.git.util.RemoteResultWrapper;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link GitCommitsCollector}.
 *
 * @author Akash Manna
 */
class GitCommitsCollectorITest extends GitITest {
    /**
     * Verifies that the {@code maxCommitsReached} flag is set (so the UI can suppress the count) and the count
     * does NOT equal the false MAX_COMMITS value of 200 in the collector's returned commits list — i.e., the flag
     * mechanism works correctly.
     */
    @Test
    void shouldSetMaxCommitsReachedFlagWhenAnchorCommitIsNotFoundInHistory()
            throws IOException, InterruptedException {
        writeFile("V1.java", "v1");
        addFile("V1.java");
        commit("Version 1");
        var stalePreviousCommit = getHead();

        writeFile("V1.java", "v1 amended");
        getGitRepository().git("commit", "--amend", "--message=Version 1 amended");
        var newHead = getHead();
        assertThat(newHead).isNotEqualTo(stalePreviousCommit);

        var collector = new GitCommitsCollector(stalePreviousCommit);
        var gitClient = createGitClient();
        RemoteResultWrapper<BuildCommits> wrapper = gitClient.withRepository(collector);

        BuildCommits result = wrapper.getResult();

        assertThat(result.isMaxCommitsReached())
                .as("maxCommitsReached should be true when anchor commit is not in history")
                .isTrue();
    }

    /**
     * Verifies that when the previous build's anchor commit still exists in history, the collector
     * counts only the new commits added after it and does NOT set the {@code maxCommitsReached} flag.
     */
    @Test
    void shouldNotSetMaxCommitsReachedFlagWhenAnchorCommitIsFoundNormally()
            throws IOException, InterruptedException {
        writeFile("Base.java", "base");
        addFile("Base.java");
        commit("Base");
        var anchorCommit = getHead();

        writeFile("New1.java", "new1");
        addFile("New1.java");
        commit("Add New1");
        var commit1 = getHead();

        writeFile("New2.java", "new2");
        addFile("New2.java");
        commit("Add New2");
        var commit2 = getHead();

        var collector = new GitCommitsCollector(anchorCommit);
        var gitClient = createGitClient();
        RemoteResultWrapper<BuildCommits> wrapper = gitClient.withRepository(collector);

        BuildCommits result = wrapper.getResult();

        assertThat(result.isMaxCommitsReached())
                .as("maxCommitsReached should be false when anchor commit is reachable")
                .isFalse();

        assertThat(result.size()).isEqualTo(2);
        assertThat(result.getCommits()).containsExactly(commit2, commit1);
        assertThat(result.getRecordingType()).isEqualTo(GitCommitsRecord.RecordingType.INCREMENTAL);
    }

    /**
     * Verifies that when there is no previous build (first build), {@code maxCommitsReached} is not set and
     * {@code RecordingType} is {@code START}.
     */
    @Test
    void shouldNotSetMaxCommitsReachedFlagForFirstBuild() throws IOException, InterruptedException {
        writeFile("Init.java", "init");
        addFile("Init.java");
        commit("Init");

        var collector = new GitCommitsCollector("");
        var gitClient = createGitClient();
        RemoteResultWrapper<BuildCommits> wrapper = gitClient.withRepository(collector);

        BuildCommits result = wrapper.getResult();

        assertThat(result.isMaxCommitsReached()).isFalse();
        assertThat(result.getRecordingType()).isEqualTo(GitCommitsRecord.RecordingType.START);
        assertThat(result.size()).isGreaterThan(0);
    }
}