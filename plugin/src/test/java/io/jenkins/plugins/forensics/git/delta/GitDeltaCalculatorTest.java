package io.jenkins.plugins.forensics.git.delta;

import java.io.IOException;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;

import edu.hm.hafner.util.FilteredLog;

import org.jenkinsci.plugins.gitclient.GitClient;

import io.jenkins.plugins.forensics.delta.model.Delta;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for the class {@link GitDeltaCalculator}.
 *
 * @author Florian Orendi
 */
class GitDeltaCalculatorTest {

    @Test
    void shouldAbortIfWithRepositoryThrowsException() throws IOException, InterruptedException {
        GitClient gitClient = createGitClientWithException(new IOException());
        GitDeltaCalculator deltaCalculator = new GitDeltaCalculator(gitClient);
        FilteredLog log = new FilteredLog(StringUtils.EMPTY);

        Optional<Delta> result = deltaCalculator.calculateDelta("x", "x", log);

        assertThat(result).isEmpty();
        assertThat(log.getErrorMessages()).contains(GitDeltaCalculator.DELTA_ERROR);
    }

    private GitClient createGitClientWithException(final Exception exception) throws InterruptedException, IOException {
        GitClient gitClient = mock(GitClient.class);
        when(gitClient.withRepository(any())).thenThrow(exception);
        return gitClient;
    }
}
