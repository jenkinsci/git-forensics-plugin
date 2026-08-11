package io.jenkins.plugins.forensics.git.miner;

import org.junit.jupiter.api.Test;

import edu.hm.hafner.util.FilteredLog;
import edu.hm.hafner.util.TreeString;

import java.util.List;

import io.jenkins.plugins.forensics.miner.CommitDiffItem;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests the class {@link TemporalCouplingMiner}.
 *
 * @author Akash Manna
 */
class TemporalCouplingMinerTest {
    private static final String FILE_A = "src/main/A.java";
    private static final String FILE_B = "src/main/B.java";
    private static final String FILE_C = "src/main/C.java";

    private static final String COMMIT_1 = "aaaa0000000000000000000000000000000000001";
    private static final String COMMIT_2 = "bbbb0000000000000000000000000000000000002";
    private static final String COMMIT_3 = "cccc0000000000000000000000000000000000003";
    private static final String COMMIT_4 = "dddd0000000000000000000000000000000000004";

    /** Verifies that an empty input produces an empty result. */
    @Test
    void shouldReturnEmptyListForNoCommits() {
        var miner = new TemporalCouplingMiner();
        List<TemporalCoupling> result = miner.compute(List.of(), createLogger());

        assertThat(result).isEmpty();
    }

    /** Verifies that a single file changed alone never produces a coupling. */
    @Test
    void shouldReturnEmptyListForSingleFileCommits() {
        var item = createItem(COMMIT_1, FILE_A);
        var miner = new TemporalCouplingMiner();

        List<TemporalCoupling> result = miner.compute(List.of(item), createLogger());

        assertThat(result).isEmpty();
    }

    /** Verifies that two files changed together in only one commit do not meet the default minimum threshold. */
    @Test
    void shouldNotReportCouplingBelowMinimumThreshold() {
        List<CommitDiffItem> items = List.of(
                createItem(COMMIT_1, FILE_A),
                createItem(COMMIT_1, FILE_B)
        );
        var miner = new TemporalCouplingMiner();

        List<TemporalCoupling> result = miner.compute(items, createLogger());

        assertThat(result).isEmpty();
    }

    /** Verifies that two files changed together in two commits are reported when the minimum is 2. */
    @Test
    void shouldReportCouplingAtMinimumThreshold() {
        List<CommitDiffItem> items = List.of(
                createItem(COMMIT_1, FILE_A),
                createItem(COMMIT_1, FILE_B),
                createItem(COMMIT_2, FILE_A),
                createItem(COMMIT_2, FILE_B)
        );
        var miner = new TemporalCouplingMiner(2);

        List<TemporalCoupling> result = miner.compute(items, createLogger());

        assertThat(result).hasSize(1);
        TemporalCoupling coupling = result.get(0);
        assertThat(coupling.getCoChanges()).isEqualTo(2);
        assertThat(coupling.getCouplingRatio()).isEqualTo(1.0);
        assertThat(List.of(coupling.getLeftFile(), coupling.getRightFile()))
                .containsExactlyInAnyOrder(FILE_A, FILE_B);
    }

    /** Verifies that a coupling ratio of 1.0 is computed when files always change together. */
    @Test
    void shouldComputeFullCouplingRatioWhenFilesAlwaysChangeTogether() {
        List<CommitDiffItem> items = List.of(
                createItem(COMMIT_1, FILE_A),
                createItem(COMMIT_1, FILE_B),
                createItem(COMMIT_2, FILE_A),
                createItem(COMMIT_2, FILE_B),
                createItem(COMMIT_3, FILE_A),
                createItem(COMMIT_3, FILE_B)
        );
        var miner = new TemporalCouplingMiner(1);

        List<TemporalCoupling> result = miner.compute(items, createLogger());

        assertThat(result).hasSize(1);
        TemporalCoupling coupling = result.get(0);
        assertThat(coupling.getCoChanges()).isEqualTo(3);
        assertThat(coupling.getCouplingRatio()).isEqualTo(1.0);
    }

    /** Verifies that the coupling ratio is correctly computed when files sometimes diverge. */
    @Test
    void shouldComputePartialCouplingRatioWhenFilesOccasionallyDiverge() {
        List<CommitDiffItem> items = List.of(
                createItem(COMMIT_1, FILE_A),
                createItem(COMMIT_1, FILE_B),
                createItem(COMMIT_2, FILE_A),
                createItem(COMMIT_2, FILE_B),
                createItem(COMMIT_3, FILE_A),
                createItem(COMMIT_4, FILE_B)
        );
        var miner = new TemporalCouplingMiner(1);

        List<TemporalCoupling> result = miner.compute(items, createLogger());

        assertThat(result).hasSize(1);
        TemporalCoupling coupling = result.get(0);
        assertThat(coupling.getCoChanges()).isEqualTo(2);
        assertThat(coupling.getCouplingRatio()).isCloseTo(2.0 / 3.0, within(0.0001));
    }

    /** Verifies that three files in the same commit produce three coupling pairs. */
    @Test
    void shouldProduceThreePairsForThreeFilesInSameCommit() {
        List<CommitDiffItem> items = List.of(
                createItem(COMMIT_1, FILE_A),
                createItem(COMMIT_1, FILE_B),
                createItem(COMMIT_1, FILE_C),
                createItem(COMMIT_2, FILE_A),
                createItem(COMMIT_2, FILE_B),
                createItem(COMMIT_2, FILE_C)
        );
        var miner = new TemporalCouplingMiner(1);

        List<TemporalCoupling> result = miner.compute(items, createLogger());

        assertThat(result).hasSize(3);
        assertThat(result).allMatch(c -> c.getCoChanges() == 2);
        assertThat(result).allMatch(c -> c.getCouplingRatio() == 1.0);
    }

    /** Verifies that independent file pairs are each computed correctly. */
    @Test
    void shouldHandleIndependentFilePairs() {
        List<CommitDiffItem> items = List.of(
                createItem(COMMIT_1, FILE_A),
                createItem(COMMIT_1, FILE_B),
                createItem(COMMIT_2, FILE_A),
                createItem(COMMIT_2, FILE_B),
                createItem(COMMIT_3, FILE_C)
        );
        var miner = new TemporalCouplingMiner(1);

        List<TemporalCoupling> result = miner.compute(items, createLogger());

        assertThat(result).hasSize(1);
        TemporalCoupling coupling = result.get(0);
        assertThat(List.of(coupling.getLeftFile(), coupling.getRightFile()))
                .containsExactlyInAnyOrder(FILE_A, FILE_B);
        assertThat(coupling.getCoChanges()).isEqualTo(2);
    }

    /** Verifies that a minimum threshold of 1 accepts all pairs including single co-changes. */
    @Test
    void shouldAcceptAllPairsWithMinimumThresholdOfOne() {
        List<CommitDiffItem> items = List.of(
                createItem(COMMIT_1, FILE_A),
                createItem(COMMIT_1, FILE_B)
        );
        var miner = new TemporalCouplingMiner(1);

        List<TemporalCoupling> result = miner.compute(items, createLogger());

        assertThat(result).hasSize(1);
    }

    /** Verifies that files changed only alone (never with another file) are excluded. */
    @Test
    void shouldExcludeFilesNeverChangedTogether() {
        List<CommitDiffItem> items = List.of(
                createItem(COMMIT_1, FILE_A),
                createItem(COMMIT_2, FILE_B),
                createItem(COMMIT_3, FILE_C)
        );
        var miner = new TemporalCouplingMiner(1);

        List<TemporalCoupling> result = miner.compute(items, createLogger());

        assertThat(result).isEmpty();
    }

    /** Verifies that multiple changes to the same file in the same commit are deduplicated within that commit. */
    @Test
    void shouldDeduplicateMultipleEntriesForSameFileInSameCommit() {
        List<CommitDiffItem> items = List.of(
                createItem(COMMIT_1, FILE_A),
                createItem(COMMIT_1, FILE_A),
                createItem(COMMIT_1, FILE_B),
                createItem(COMMIT_2, FILE_A),
                createItem(COMMIT_2, FILE_B)
        );
        var miner = new TemporalCouplingMiner(1);

        List<TemporalCoupling> result = miner.compute(items, createLogger());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCoChanges()).isEqualTo(2);
    }

    /** Verifies that an invalid minimum co-changes argument is rejected. */
    @Test
    void shouldRejectInvalidMinimumCoChanges() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new TemporalCouplingMiner(0))
                .withMessageContaining("minimumCoChanges must be >= 1");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> new TemporalCouplingMiner(-5))
                .withMessageContaining("minimumCoChanges must be >= 1");
    }

    /** Verifies that deleted files (oldPath set, newPath is /dev/null) are included in the coupling computation. */
    @Test
    void shouldIncludeDeletedFilesUsingOldPath() {
        CommitDiffItem deleted1 = new CommitDiffItem(COMMIT_1, "author@example.com", 1000);
        deleted1.setOldPath(TreeString.valueOf(FILE_A));

        CommitDiffItem other1 = createItem(COMMIT_1, FILE_B);

        CommitDiffItem deleted2 = new CommitDiffItem(COMMIT_2, "author@example.com", 2000);
        deleted2.setOldPath(TreeString.valueOf(FILE_A));

        CommitDiffItem other2 = createItem(COMMIT_2, FILE_B);

        var miner = new TemporalCouplingMiner(1);
        List<TemporalCoupling> result = miner.compute(
                List.of(deleted1, other1, deleted2, other2), createLogger());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCoChanges()).isEqualTo(2);
    }

    /** Verifies that the coupling ratio is capped at 1.0 even when co-changes equals total commits. */
    @Test
    void shouldNotExceedCouplingRatioOfOne() {
        List<CommitDiffItem> items = List.of(
                createItem(COMMIT_1, FILE_A),
                createItem(COMMIT_1, FILE_B),
                createItem(COMMIT_2, FILE_A),
                createItem(COMMIT_2, FILE_B),
                createItem(COMMIT_3, FILE_A),
                createItem(COMMIT_3, FILE_B)
        );
        var miner = new TemporalCouplingMiner(1);

        List<TemporalCoupling> result = miner.compute(items, createLogger());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCouplingRatio()).isLessThanOrEqualTo(1.0);
    }

    private CommitDiffItem createItem(final String commitId, final String filePath) {
        var item = new CommitDiffItem(commitId, "author@example.com", 0);
        item.setNewPath(TreeString.valueOf(filePath));
        return item;
    }

    private FilteredLog createLogger() {
        return new FilteredLog("Temporal Coupling Test");
    }
}