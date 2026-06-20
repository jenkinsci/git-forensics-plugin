package io.jenkins.plugins.forensics.git.miner;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.junit.jupiter.api.Test;

import edu.hm.hafner.util.FilteredLog;

import java.io.IOException;
import java.util.List;

import io.jenkins.plugins.forensics.git.util.GitITest;
import io.jenkins.plugins.forensics.miner.CommitDiffItem;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link TemporalCouplingMiner} using a real Git repository.
 *
 * <p>These tests verify that temporal coupling — files frequently changed in the same commit — is correctly detected
 * from actual Git commit history, as described in "Your Code as a Crime Scene" (Adam Tornhill, page 72).
 *
 * @author Akash Manna
 */
@SuppressWarnings("checkstyle:LambdaBodyLength")
class TemporalCouplingMinerITest extends GitITest {
    private static final String FILE_X = "X.java";
    private static final String FILE_Y = "Y.java";
    private static final String FILE_Z = "Z.java";

    /**
     * Verifies that no coupling is found in the initial repository with a single file.
     */
    @Test
    void shouldFindNoCouplingInInitialRepository() {
        runTest((repository, git) -> {
            List<CommitDiffItem> commits = analyze(repository, git, "");
            List<TemporalCoupling> couplings = mine(commits, 1);

            assertThat(couplings).isEmpty();
        });
    }

    /**
     * Verifies that two files always changed together in the same commits are detected as fully coupled.
     */
    @Test
    void shouldDetectFullCouplingForFilesAlwaysChangedTogether() {
        var initial = getHead();

        commitTwoFiles(FILE_X, "content-x-1", FILE_Y, "content-y-1", "First change");
        commitTwoFiles(FILE_X, "content-x-2", FILE_Y, "content-y-2", "Second change");
        commitTwoFiles(FILE_X, "content-x-3", FILE_Y, "content-y-3", "Third change");

        runTest((repository, git) -> {
            List<CommitDiffItem> commits = analyze(repository, git, initial);
            List<TemporalCoupling> couplings = mine(commits, 1);

            TemporalCoupling xyCoupling = findCoupling(couplings, FILE_X, FILE_Y);
            assertThat(xyCoupling).isNotNull();
            assertThat(xyCoupling.getCoChanges()).isEqualTo(3);
            assertThat(xyCoupling.getCouplingRatio()).isEqualTo(1.0);
        });
    }

    /**
     * Verifies that a file changed alone is not coupled with any other file.
     */
    @Test
    void shouldNotCoupleFileChangedAlone() {
        var initial = getHead();

        commitTwoFiles(FILE_X, "x-1", FILE_Y, "y-1", "X and Y together");
        commitSingleFile(FILE_Z, "z-only", "Only Z");

        runTest((repository, git) -> {
            List<CommitDiffItem> commits = analyze(repository, git, initial);
            List<TemporalCoupling> couplings = mine(commits, 1);

            assertThat(findCoupling(couplings, FILE_X, FILE_Z)).isNull();
            assertThat(findCoupling(couplings, FILE_Y, FILE_Z)).isNull();
        });
    }

    /**
     * Verifies that the minimum co-change threshold filters out pairs with too few shared commits.
     */
    @Test
    void shouldRespectMinimumCoChangesThreshold() {
        var initial = getHead();

        commitTwoFiles(FILE_X, "x-1", FILE_Y, "y-1", "X and Y once");

        runTest((repository, git) -> {
            List<CommitDiffItem> commits = analyze(repository, git, initial);

            List<TemporalCoupling> withMinOne = mine(commits, 1);
            List<TemporalCoupling> withMinTwo = mine(commits, 2);

            assertThat(findCoupling(withMinOne, FILE_X, FILE_Y))
                    .as("Should find coupling when threshold is 1")
                    .isNotNull();
            assertThat(findCoupling(withMinTwo, FILE_X, FILE_Y))
                    .as("Should not find coupling when threshold is 2 but only 1 co-change exists")
                    .isNull();
        });
    }

    /**
     * Verifies that three files always modified together produce all three expected coupling pairs.
     */
    @Test
    void shouldDetectThreePairsForThreeFilesChangedTogether() {
        var initial = getHead();

        commitThreeFiles(FILE_X, "x-1", FILE_Y, "y-1", FILE_Z, "z-1", "All three first");
        commitThreeFiles(FILE_X, "x-2", FILE_Y, "y-2", FILE_Z, "z-2", "All three second");

        runTest((repository, git) -> {
            List<CommitDiffItem> commits = analyze(repository, git, initial);
            List<TemporalCoupling> couplings = mine(commits, 1);

            assertThat(findCoupling(couplings, FILE_X, FILE_Y)).isNotNull();
            assertThat(findCoupling(couplings, FILE_X, FILE_Z)).isNotNull();
            assertThat(findCoupling(couplings, FILE_Y, FILE_Z)).isNotNull();

            assertThat(findCoupling(couplings, FILE_X, FILE_Y).getCoChanges()).isEqualTo(2);
            assertThat(findCoupling(couplings, FILE_X, FILE_Z).getCoChanges()).isEqualTo(2);
            assertThat(findCoupling(couplings, FILE_Y, FILE_Z).getCoChanges()).isEqualTo(2);
        });
    }

    /**
     * Verifies that the coupling ratio is computed as a partial value when files diverge in some commits.
     */
    @Test
    void shouldComputePartialCouplingRatioForDivergingFiles() {
        var initial = getHead();

        commitTwoFiles(FILE_X, "x-1", FILE_Y, "y-1", "X and Y first");
        commitTwoFiles(FILE_X, "x-2", FILE_Y, "y-2", "X and Y second");
        commitSingleFile(FILE_X, "x-3", "X alone");
        commitSingleFile(FILE_X, "x-4", "X alone again");

        runTest((repository, git) -> {
            List<CommitDiffItem> commits = analyze(repository, git, initial);
            List<TemporalCoupling> couplings = mine(commits, 1);

            TemporalCoupling xyCoupling = findCoupling(couplings, FILE_X, FILE_Y);
            assertThat(xyCoupling).isNotNull();
            assertThat(xyCoupling.getCoChanges()).isEqualTo(2);
            // FILE_Y: 2 commits, FILE_X: 4 commits → min = 2, ratio = 2/2 = 1.0
            assertThat(xyCoupling.getCouplingRatio()).isCloseTo(1.0, within(0.0001));
        });
    }

    /**
     * Verifies that all coupling ratios computed from a real repository are in the valid range [0.0, 1.0].
     */
    @Test
    void shouldHaveValidCouplingRatiosForAllResults() {
        var initial = getHead();

        commitTwoFiles(FILE_X, "x-1", FILE_Y, "y-1", "XY first");
        commitTwoFiles(FILE_X, "x-2", FILE_Y, "y-2", "XY second");
        commitTwoFiles(FILE_Y, "y-3", FILE_Z, "z-1", "YZ first");
        commitSingleFile(FILE_X, "x-3", "X alone");

        runTest((repository, git) -> {
            List<CommitDiffItem> commits = analyze(repository, git, initial);
            List<TemporalCoupling> couplings = mine(commits, 1);

            for (TemporalCoupling coupling : couplings) {
                assertThat(coupling.getCouplingRatio())
                        .as("Coupling ratio for %s <-> %s should be in [0.0, 1.0]",
                                coupling.getLeftFile(), coupling.getRightFile())
                        .isBetween(0.0, 1.0);
                assertThat(coupling.getCoChanges())
                        .as("Co-change count should be positive")
                        .isPositive();
            }
        });
    }

    // ---- helpers ----

    private void commitSingleFile(final String file, final String content, final String message) {
        git("config", "user.name", FOO_NAME);
        git("config", "user.email", FOO_EMAIL);
        writeFile(file, content);
        addFile(file);
        commit(message);
    }

    private void commitTwoFiles(final String file1, final String content1,
            final String file2, final String content2, final String message) {
        git("config", "user.name", FOO_NAME);
        git("config", "user.email", FOO_EMAIL);
        writeFile(file1, content1);
        writeFile(file2, content2);
        addFile(file1);
        addFile(file2);
        commit(message);
    }

    private void commitThreeFiles(final String file1, final String content1,
            final String file2, final String content2,
            final String file3, final String content3,
            final String message) {
        git("config", "user.name", FOO_NAME);
        git("config", "user.email", FOO_EMAIL);
        writeFile(file1, content1);
        writeFile(file2, content2);
        writeFile(file3, content3);
        addFile(file1);
        addFile(file2);
        addFile(file3);
        commit(message);
    }

    private List<CommitDiffItem> analyze(final Repository repository, final Git git,
            final String previousCommit) throws IOException, GitAPIException {
        return new CommitAnalyzer().run(repository, git, previousCommit, new FilteredLog("ITest"));
    }

    private List<TemporalCoupling> mine(final List<CommitDiffItem> commits, final int minCoChanges) {
        return new TemporalCouplingMiner(minCoChanges).compute(commits, new FilteredLog("ITest"));
    }

    private TemporalCoupling findCoupling(final List<TemporalCoupling> couplings,
            final String fileA, final String fileB) {
        return couplings.stream()
                .filter(c -> matchesFile(c.getLeftFile(), fileA) && matchesFile(c.getRightFile(), fileB)
                        || matchesFile(c.getLeftFile(), fileB) && matchesFile(c.getRightFile(), fileA))
                .findFirst()
                .orElse(null);
    }

    private boolean matchesFile(final String path, final String name) {
        return path.endsWith(name) || path.equals(name);
    }
}