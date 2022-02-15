package io.jenkins.plugins.forensics.git.delta.model;

import java.util.HashMap;

import org.junit.jupiter.api.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

import static org.assertj.core.api.Assertions.*;

/**
 * Test class for {@link GitDelta}.
 *
 * @author Florian Orendi
 */
class GitDeltaTest {

    private static final String DIFF_FILE = "testContent";

    @Test
    void testGitDeltaGetters() {
        GitDelta delta = createGitDelta();

        assertThat(delta.getDiffFile()).isEqualTo(DIFF_FILE);
    }

    @Test
    void shouldObeyEqualsContract() {
        EqualsVerifier.simple().forClass(GitDelta.class).verify();
    }

    /**
     * Factory method which creates an instance of {@link GitDelta}.
     *
     * @return the created instance
     */
    private GitDelta createGitDelta() {
        return new GitDelta("", "", new HashMap<>(), DIFF_FILE);
    }
}
