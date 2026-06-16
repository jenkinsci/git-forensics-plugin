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
 * Integration tests for {@link GitCommitsCollector} — in particular the fix for JENKINS-67281, which caused the plugin
 * to report 200 "commits since last build" when the commit recorded in the previous build no longer exists in the
 * repository (e.g., after a {@code git commit --amend} followed by a force-push).
 *
 * @author Akash Manna
 */
class GitCommitsCollectorITest extends GitITest {
    /**
     * Verifies that {@link GitCommitsCollector#resolveLatestRecordedCommit} returns an empty string when the given
     * commit ID is not present in the repository. This is the core guard that prevents the MAX_COMMITS (200) bug.
     */
    @Test
    void shouldReturnEmptyStringWhenPreviousCommitNoLongerExists() {
        var nonExistentCommit = "deadbeefdeadbeefdeadbeefdeadbeefdeadbeef";
        var collector = new GitCommitsCollector(nonExistentCommit);

        runTest((repository, git) -> {
            var resolved = collector.resolveLatestRecordedCommit(repository);
            assertThat(resolved).as("Should return empty when commit does not exist").isEmpty();
        });
    }

    /**
     * Verifies that {@link GitCommitsCollector#resolveLatestRecordedCommit} returns the original commit ID when the
     * commit still exists in the repository (the happy path).
     */
    @Test
    void shouldReturnOriginalCommitWhenItStillExistsInRepository() {
        writeFile("Foo.java", "class Foo {}");
        addFile("Foo.java");
        commit("Add Foo.java");
        var existingCommit = getHead();

        var collector = new GitCommitsCollector(existingCommit);

        runTest((repository, git) -> {
            var resolved = collector.resolveLatestRecordedCommit(repository);
            assertThat(resolved).isEqualTo(existingCommit);
        });
    }

    /**
     * Verifies that {@link GitCommitsCollector#resolveLatestRecordedCommit} returns the original (empty) string when
     * no previous commit was recorded (i.e., this is the first build).
     */
    @Test
    void shouldReturnEmptyStringWhenNoPreviousCommitWasRecorded() {
        var collector = new GitCommitsCollector("");

        runTest((repository, git) -> {
            var resolved = collector.resolveLatestRecordedCommit(repository);
            assertThat(resolved).isEmpty();
        });
    }

    /**
     * Simulates the exact bug scenario from JENKINS-67281: a commit is recorded, then it is amended (creating a new
     * commit ID), and the collector is invoked with the now-stale commit ID. Verifies that:
     * <ol>
     *     <li>The returned {@link BuildCommits} is treated as a fresh start ({@code RecordingType.START}).</li>
     *     <li>The number of collected commits is NOT 200.</li>
     *     <li>A descriptive warning message is logged.</li>
     * </ol>
     */
    @Test
    void shouldTreatMissingPreviousCommitAsStartAndNotReturn200() throws IOException, InterruptedException {
        // Simulate the previous build's commit
        writeFile("V1.java", "v1");
        addFile("V1.java");
        commit("Version 1");
        var originalCommit = getHead();

        // Simulate the force-push/amend: replace the commit so its ID changes
        getGitRepository().git("commit", "--amend", "--message=Version 1 amended");
        var amendedCommit = getHead();
        assertThat(amendedCommit).isNotEqualTo(originalCommit);

        // The collector is given the original (now-stale) commit as the "latest recorded"
        var collector = new GitCommitsCollector(originalCommit);

        var gitClient = createGitClient();
        RemoteResultWrapper<BuildCommits> wrapper = gitClient.withRepository(collector);

        BuildCommits result = wrapper.getResult();

        // Should be treated as a fresh start, NOT incremental
        assertThat(result.getRecordingType())
                .as("Recording type should be START when previous commit is missing")
                .isEqualTo(GitCommitsRecord.RecordingType.START);

        // Must NOT return 200 commits just because the stop-condition was never found
        assertThat(result.size())
                .as("Commit count should not be 200 when previous commit was unreachable")
                .isNotEqualTo(200);

        // Log should contain the warning about the missing commit
        var infoMessages = wrapper.getInfoMessages();
        assertThat(infoMessages)
                .anySatisfy(msg -> assertThat(msg).contains("no longer exists in the repository"));
    }

    /**
     * Verifies that when the previous build commit is valid (normal, non-force-pushed case), the collector correctly
     * counts only the new commits added since that build.
     */
    @Test
    void shouldCollectOnlyNewCommitsSincePreviousBuildNormally() throws IOException, InterruptedException {
        // Initial commit (will be the "previous build" commit)
        writeFile("Base.java", "base");
        addFile("Base.java");
        commit("Base");
        var previousBuildCommit = getHead();

        // Add two new commits after the "previous build"
        writeFile("New1.java", "new1");
        addFile("New1.java");
        commit("Add New1");
        var commit1 = getHead();

        writeFile("New2.java", "new2");
        addFile("New2.java");
        commit("Add New2");
        var commit2 = getHead();

        var collector = new GitCommitsCollector(previousBuildCommit);
        var gitClient = createGitClient();
        RemoteResultWrapper<BuildCommits> wrapper = gitClient.withRepository(collector);

        BuildCommits result = wrapper.getResult();

        // Should record exactly 2 new commits
        assertThat(result.size()).isEqualTo(2);
        assertThat(result.getCommits()).containsExactly(commit2, commit1);
        assertThat(result.getRecordingType()).isEqualTo(GitCommitsRecord.RecordingType.INCREMENTAL);
        assertThat(result.size()).isNotEqualTo(200);
    }
}