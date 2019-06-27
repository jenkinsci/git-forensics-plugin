package io.jenkins.plugins.git.forensics.blame;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.assertj.core.util.Lists;
import org.junit.jupiter.api.Test;

import org.jenkinsci.plugins.gitclient.GitClient;
import hudson.EnvVars;
import hudson.model.Run;
import hudson.model.Saveable;
import hudson.model.TaskListener;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.extensions.impl.CloneOption;
import hudson.scm.NullSCM;
import hudson.util.DescribableList;

import io.jenkins.plugins.forensics.blame.Blamer;
import io.jenkins.plugins.forensics.util.FilteredLog;

import static io.jenkins.plugins.forensics.assertions.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests the class {@link GitBlamerFactory}.
 *
 * @author Andreas Pabst
 */
class GitBlamerFactoryTest {
    private static final TaskListener NULL_LISTENER = TaskListener.NULL;

    @Test
    void shouldSkipIfScmIsNotGit() {
        FilteredLog logger = createLogger();

        GitBlamerFactory factory = new GitBlamerFactory();
        assertThat(factory.createBlamer(new NullSCM(), null, null, NULL_LISTENER, logger)).isEmpty();

        assertThat(logger).hasNoErrorMessages();
        assertThat(logger).hasInfoMessages("Skipping blame since SCM 'hudson.scm.NullSCM' is not of type GitSCM");
    }

    @Test
    void shouldCreateBlamerForGit() throws IOException, InterruptedException {
        GitSCM gitSCM = mock(GitSCM.class);
        when(gitSCM.getExtensions()).thenReturn(new DescribableList<>(Saveable.NOOP));

        Run<?, ?> run = mock(Run.class);
        EnvVars envVars = new EnvVars();
        envVars.put("GIT_COMMIT", "test_commit");
        when(run.getEnvironment(NULL_LISTENER)).thenReturn(envVars);

        GitClient gitClient = mock(GitClient.class);
        when(gitSCM.createClient(NULL_LISTENER, envVars, run, null)).thenReturn(gitClient);

        FilteredLog logger = createLogger();

        GitBlamerFactory factory = new GitBlamerFactory();
        Optional<Blamer> blamer = factory.createBlamer(gitSCM, run, null, NULL_LISTENER, logger);

        assertThat(blamer).isNotEmpty().containsInstanceOf(GitBlamer.class);
        assertThat(logger).hasNoErrorMessages();
        assertThat(logger).hasInfoMessages(GitBlamerFactory.INFO_BLAMER_CREATED);
    }

    @Test
    void shouldCreateNullBlamerOnShallowGit() {
        CloneOption shallowCloneOption = mock(CloneOption.class);
        when(shallowCloneOption.isShallow()).thenReturn(true);

        GitSCM gitSCM = mock(GitSCM.class);
        when(gitSCM.getExtensions()).thenReturn(new DescribableList<>(Saveable.NOOP, Lists.list(shallowCloneOption)));

        FilteredLog logger = createLogger();

        GitBlamerFactory gitChecker = new GitBlamerFactory();

        assertThat(gitChecker.createBlamer(gitSCM, mock(Run.class), null, NULL_LISTENER, logger)).isEmpty();
        assertThat(logger).hasInfoMessages(GitBlamerFactory.INFO_SHALLOW_CLONE);
        assertThat(logger).hasNoErrorMessages();
    }

    @Test
    void shouldCreateNullBlamerOnError() throws IOException, InterruptedException {
        GitBlamerFactory gitChecker = new GitBlamerFactory();
        Run<?, ?> run = mock(Run.class);
        List<GitSCMExtension> extensions = new ArrayList<>();
        GitSCM gitSCM = new GitSCM(null, null, false, null, null, null, extensions);

        when(run.getEnvironment(NULL_LISTENER)).thenThrow(new IOException());

        FilteredLog logger = createLogger();

        assertThat(gitChecker.createBlamer(gitSCM, run, null, NULL_LISTENER, logger)).isEmpty();
        assertThat(logger).hasErrorMessages(GitBlamerFactory.ERROR_BLAMER);
        assertThat(logger).hasNoInfoMessages();
    }

    private FilteredLog createLogger() {
        return new FilteredLog("errors");
    }
}
