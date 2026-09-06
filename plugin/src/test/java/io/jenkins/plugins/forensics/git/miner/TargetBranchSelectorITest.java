package io.jenkins.plugins.forensics.git.miner;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import io.jenkins.plugins.forensics.git.util.GitITest;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests the class {@link TargetBranchSelector}.
 *
 * @author Michael Trimarchi
 */
class TargetBranchSelectorITest extends GitITest {
    @Test
    void shouldFindMergeBaseOfHeadAndTargetBranch() throws IOException, InterruptedException {
        var base = getHead();

        checkoutNewBranch("target");
        checkout(INITIAL_BRANCH);

        writeFileAsAuthorFoo("First\n");
        var head = getHead();

        var mergeBase = createGitClient().withRepository(new TargetBranchSelector("target"));

        assertThat(mergeBase).isEqualTo(base);
        assertThat(head).isNotEqualTo(base);
    }
}
