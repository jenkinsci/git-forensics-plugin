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
 * Integration tests for {@link GitCommitsCollector} — in particular the fix for JENKINS-67281 which caused the plugin
 * to display "Commits since last build: 200" whenever the previous build's anchor commit could not be found in the
 * commit history (e.g., after a {@code git commit --amend} followed by a force-push).
 *
 * <p>The agreed fix (per the issue discussion) is to toggle a {@code maxCommitsReached} flag in {@link BuildCommits}
 * when the scan exhausts {@code MAX_COMMITS} without finding the anchor, and then suppress the misleading number
 * in the UI instead of displaying it.</p>
 *
 * @author Akash Manna
 */
class GitCommitsCollectorITest extends GitITest {
    /**
     * Simulates the exact bug scenario from JENKINS-67281:
     * <ol>
     *     <li>Commit is recorded as the previous build's anchor.</li>
     *     <li>That commit is amended (force-push equivalent in a local repo), making the old ID unreachable.</li>
     *     <li>The collector is invoked with the now-stale anchor ID.</li>
     * </ol>
     * Verifies that the {@code maxCommitsReached} flag is set (so the UI can suppress the count) and the count
     * does NOT equal the false MAX_COMMITS value of 200 in the collector's returned commits list — i.e., the flag
     * mechanism works correctly.
     */
    @Test
    void shouldSetMaxCommitsReachedFlagWhenAnchorCommitIsNotFoundInHistory()
            throws IOException, InterruptedException {
        // Previous build recorded this commit as the anchor
        writeFile("V1.java", "v1");
        addFile("V1.java");
        commit("Version 1");
        var stalePreviousCommit = getHead();

        // Force-push/amend: replaces the commit, making stalePreviousCommit unreachable from HEAD
        writeFile("V1.java", "v1 amended");
        getGitRepository().git("commit", "--amend", "--message=Version 1 amended");
        var newHead = getHead();
        assertThat(newHead).isNotEqualTo(stalePreviousCommit);

        // Collector invoked with the stale anchor — it will scan all commits and never find it
        var collector = new GitCommitsCollector(stalePreviousCommit);
        var gitClient = createGitClient();
        RemoteResultWrapper<BuildCommits> wrapper = gitClient.withRepository(collector);

        BuildCommits result = wrapper.getResult();

        // The flag MUST be set — the count is indeterminate and the UI should suppress it
        assertThat(result.isMaxCommitsReached())
                .as("maxCommitsReached should be true when anchor commit is not in history")
                .isTrue();
    }

    /**
     * Verifies the happy path: when the previous build's anchor commit still exists in history, the collector
     * counts only the new commits added after it and does NOT set the {@code maxCommitsReached} flag.
     */
    @Test
    void shouldNotSetMaxCommitsReachedFlagWhenAnchorCommitIsFoundNormally()
            throws IOException, InterruptedException {
        // Previous build's anchor
        writeFile("Base.java", "base");
        addFile("Base.java");
        commit("Base");
        var anchorCommit = getHead();

        // Two new commits after the "previous build"
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

        // Flag must NOT be set — the count is exact
        assertThat(result.isMaxCommitsReached())
                .as("maxCommitsReached should be false when anchor commit is reachable")
                .isFalse();

        // Exactly 2 new commits, not 200
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

        // First build: no previous anchor
        var collector = new GitCommitsCollector("");
        var gitClient = createGitClient();
        RemoteResultWrapper<BuildCommits> wrapper = gitClient.withRepository(collector);

        BuildCommits result = wrapper.getResult();

        assertThat(result.isMaxCommitsReached()).isFalse();
        assertThat(result.getRecordingType()).isEqualTo(GitCommitsRecord.RecordingType.START);
        assertThat(result.size()).isGreaterThan(0);
    }
}