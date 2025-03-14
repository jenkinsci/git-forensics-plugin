package io.jenkins.plugins.forensics.git.delta;

import org.assertj.core.util.Lists;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.jupiter.api.Test;

import edu.hm.hafner.util.FilteredLog;

import java.io.File;
import java.io.IOException;
import java.util.Optional;

import org.jenkinsci.plugins.gitclient.GitClient;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.model.Run;
import hudson.model.Saveable;
import hudson.model.TaskListener;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.extensions.impl.CloneOption;
import hudson.scm.NullSCM;
import hudson.util.DescribableList;

import io.jenkins.plugins.forensics.delta.DeltaCalculator;
import io.jenkins.plugins.forensics.git.util.GitRepositoryValidator;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Integration test for the class {@link GitDeltaCalculatorFactory}.
 *
 * @author Florian Orendi
 */
class GitDeltaCalculatorFactoryTest {
    private static final TaskListener NULL_LISTENER = TaskListener.NULL;

    @Test
    void shouldSkipIfScmIsNotGit() {
        var logger = createLogger();

        var factory = new GitDeltaCalculatorFactory();

        assertThat(factory.createDeltaCalculator(new NullSCM(), null, null, NULL_LISTENER, logger)).isEmpty();
        assertThat(logger.getErrorMessages()).isEmpty();
        assertThat(logger.getInfoMessages()).contains("SCM 'hudson.scm.NullSCM' is not of type GitSCM");
    }

    @Test
    void shouldCreateDeltaCalculatorForGit() throws IOException, InterruptedException {
        var gitSCM = createGitScm();

        Run<?, ?> run = mock(Run.class);

        var envVars = new EnvVars();
        when(run.getEnvironment(NULL_LISTENER)).thenReturn(envVars);

        GitClient gitClient = mock(GitClient.class);
        var workspace = createWorkTreeStub();
        ObjectId commit = mock(ObjectId.class);
        when(gitClient.revParse(anyString())).thenReturn(commit);
        when(gitClient.getWorkTree()).thenReturn(new FilePath(new File("/working-tree")));

        when(gitSCM.createClient(NULL_LISTENER, envVars, run, workspace)).thenReturn(gitClient);
        var logger = createLogger();

        var factory = new GitDeltaCalculatorFactory();
        Optional<DeltaCalculator> deltaCalculator = factory.createDeltaCalculator(gitSCM, run, workspace, NULL_LISTENER,
                logger);

        assertThat(deltaCalculator).isNotEmpty().containsInstanceOf(GitDeltaCalculator.class);
        assertThat(logger.getErrorMessages()).isEmpty();
        assertThat(logger.getInfoMessages()).contains(
                "-> Git delta calculator successfully created in working tree '/working-tree'");
    }

    @Test
    void shouldCreateNullDeltaCalculatorOnShallowGit() {
        CloneOption shallowCloneOption = mock(CloneOption.class);
        when(shallowCloneOption.isShallow()).thenReturn(true);

        GitSCM git = mock(GitSCM.class);
        when(git.getExtensions()).thenReturn(new DescribableList<>(Saveable.NOOP, Lists.list(shallowCloneOption)));

        var logger = createLogger();

        var factory = new GitDeltaCalculatorFactory();

        assertThat(factory.createDeltaCalculator(git, mock(Run.class), null, NULL_LISTENER, logger)).isEmpty();
        assertThat(logger.getInfoMessages()).contains(GitRepositoryValidator.INFO_SHALLOW_CLONE);
        assertThat(logger.getErrorMessages()).isEmpty();
    }

    @Test
    void shouldCreateNullDeltaCalculatorOnError() throws IOException, InterruptedException {
        var factory = new GitDeltaCalculatorFactory();

        Run<?, ?> run = mock(Run.class);
        when(run.getEnvironment(NULL_LISTENER)).thenThrow(new IOException("Error"));

        var logger = createLogger();

        assertThat(factory.createDeltaCalculator(createGitScm(), run, null, NULL_LISTENER, logger)).isEmpty();
        assertThat(logger.getErrorMessages()).isEmpty();
        assertThat(logger.getInfoMessages()).contains(
                "Exception while creating a GitClient instance for work tree 'null'");
    }

    private FilePath createWorkTreeStub() {
        File mock = mock(File.class);
        when(mock.getPath()).thenReturn("/");
        return new FilePath(mock);
    }

    private GitSCM createGitScm() {
        GitSCM git = mock(GitSCM.class);
        when(git.getExtensions()).thenReturn(new DescribableList<>(Saveable.NOOP));
        return git;
    }

    private FilteredLog createLogger() {
        return new FilteredLog("errors");
    }
}
