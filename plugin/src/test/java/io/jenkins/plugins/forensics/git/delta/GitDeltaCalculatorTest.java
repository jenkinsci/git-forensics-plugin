package io.jenkins.plugins.forensics.git.delta;

import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;

import edu.hm.hafner.util.FilteredLog;

import org.jenkinsci.plugins.gitclient.GitClient;
import hudson.model.Run;

import io.jenkins.plugins.forensics.delta.Delta;

import static io.jenkins.plugins.forensics.git.delta.GitDeltaCalculator.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for the class {@link GitDeltaCalculator}.
 *
 * @author Florian Orendi
 */
class GitDeltaCalculatorTest {

    private static final String EMPTY_SCM_KEY = "";

    @Test
    void shouldAbortIfCommitsAreEmpty() {
        GitClient gitClient = mock(GitClient.class);
        GitDeltaCalculator deltaCalculator = new GitDeltaCalculator(gitClient);
        FilteredLog log = new FilteredLog(StringUtils.EMPTY);

        Optional<Delta> result = deltaCalculator.calculateDelta(mock(Run.class), mock(Run.class), EMPTY_SCM_KEY, log);

        assertThat(result).isEmpty();
        assertThat(log.getErrorMessages()).contains(EMPTY_COMMIT_ERROR);
    }
}
