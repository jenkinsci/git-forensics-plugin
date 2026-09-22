package io.jenkins.plugins.forensics.git.miner;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import io.jenkins.plugins.forensics.git.util.GitITest;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests the class {@link MergeBaseSelector}.
 *
 * @author Michael Trimarchi
 */
class MergeBaseSelectorITest extends GitITest {
    @Test
    void shouldFindCorrectMergeBaseForDirectParent() throws IOException, InterruptedException {
        writeFileAsAuthorFoo("First\n");
        var target = getHead();
        writeFileAsAuthorFoo("Second\n");
        var head = getHead();

        var mergeBase = createGitClient().withRepository(new MergeBaseSelector(target));

        assertThat(mergeBase).isEqualTo(target);
        assertThat(head).isNotEqualTo(target);
    }

    @Test
    void shouldFindCorrectMergeBaseForAncestor() throws IOException, InterruptedException {
        writeFileAsAuthorFoo("First\n");
        writeFileAsAuthorFoo("Second\n");
        var target = getHead();
        writeFileAsAuthorFoo("Third\n");
        writeFileAsAuthorFoo("Fourth\n");
        var head = getHead();

        var mergeBase = createGitClient().withRepository(new MergeBaseSelector(target));

        assertThat(mergeBase).isEqualTo(target);
        assertThat(head).isNotEqualTo(target);
    }
}
