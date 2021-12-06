package io.jenkins.plugins.forensics.git.delta;

import edu.hm.hafner.util.FilteredLog;
import io.jenkins.plugins.forensics.delta.model.Delta;
import org.apache.commons.lang3.StringUtils;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

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
        GitClient gitClient = Mockito.mock(GitClient.class);
        Mockito.when(gitClient.withRepository(ArgumentMatchers.any())).thenThrow(exception);
        return gitClient;
    }
}
