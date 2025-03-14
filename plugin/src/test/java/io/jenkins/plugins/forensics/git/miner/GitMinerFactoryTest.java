package io.jenkins.plugins.forensics.git.miner;

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

import io.jenkins.plugins.forensics.git.util.GitRepositoryValidator;
import io.jenkins.plugins.forensics.miner.RepositoryMiner;

import static io.jenkins.plugins.forensics.assertions.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests the class {@link GitMinerFactory}.
 *
 * @author Ullrich Hafner
 */
class GitMinerFactoryTest {
    private static final TaskListener NULL_LISTENER = TaskListener.NULL;

    @Test
    void shouldSkipIfScmIsNotGit() {
        var logger = createLogger();

        var factory = new GitMinerFactory();
        assertThat(factory.createMiner(new NullSCM(), null, createWorkTreeStub(), NULL_LISTENER, logger)).isEmpty();

        assertThat(logger.getErrorMessages()).isEmpty();
        assertThat(logger.getInfoMessages()).contains("SCM 'hudson.scm.NullSCM' is not of type GitSCM");
    }

    @Test
    void shouldCreateBlamerForGit() throws IOException, InterruptedException {
        GitSCM gitSCM = mock(GitSCM.class);
        when(gitSCM.getExtensions()).thenReturn(new DescribableList<>(Saveable.NOOP));

        Run<?, ?> run = mock(Run.class);
        var envVars = new EnvVars();
        envVars.put("GIT_COMMIT", "test_commit");
        when(run.getEnvironment(NULL_LISTENER)).thenReturn(envVars);

        var workspace = createWorkTreeStub();
        GitClient gitClient = mock(GitClient.class);
        when(gitSCM.createClient(NULL_LISTENER, envVars, run, workspace)).thenReturn(gitClient);
        ObjectId commit = mock(ObjectId.class);
        when(gitClient.revParse(anyString())).thenReturn(commit);

        var logger = createLogger();

        var factory = new GitMinerFactory();
        Optional<RepositoryMiner> blamer = factory.createMiner(gitSCM, run, workspace, NULL_LISTENER, logger);

        assertThat(blamer).isNotEmpty().containsInstanceOf(GitRepositoryMiner.class);
        assertThat(logger.getErrorMessages()).isEmpty();
        assertThat(logger.getInfoMessages()).contains("-> Git miner successfully created in working tree '/'");
    }

    @Test
    void shouldCreateNullBlamerOnShallowGit() {
        CloneOption shallowCloneOption = mock(CloneOption.class);
        when(shallowCloneOption.isShallow()).thenReturn(true);

        GitSCM gitSCM = mock(GitSCM.class);
        when(gitSCM.getExtensions()).thenReturn(
                new DescribableList<>(Saveable.NOOP, Lists.list(shallowCloneOption)));

        var logger = createLogger();

        var gitChecker = new GitMinerFactory();

        assertThat(gitChecker.createMiner(gitSCM, mock(Run.class), createWorkTreeStub(), NULL_LISTENER, logger))
                .isEmpty();
        assertThat(logger.getInfoMessages()).contains(GitRepositoryValidator.INFO_SHALLOW_CLONE);
        assertThat(logger.getErrorMessages()).isEmpty();
    }

    @Test
    void shouldCreateNullBlamerOnError() throws IOException, InterruptedException {
        Run<?, ?> run = mock(Run.class);
        when(run.getEnvironment(NULL_LISTENER)).thenThrow(new IOException("Error"));

        var logger = createLogger();

        var gitChecker = new GitMinerFactory();

        GitSCM git = mock(GitSCM.class);
        when(git.getExtensions()).thenReturn(new DescribableList<>(Saveable.NOOP));

        assertThat(gitChecker.createMiner(git, run, createWorkTreeStub(), NULL_LISTENER, logger)).isEmpty();
        assertThat(logger.getErrorMessages()).isEmpty();
        assertThat(logger.getInfoMessages()).contains(
                "Exception while creating a GitClient instance for work tree '/'");
    }

    private FilePath createWorkTreeStub() {
        File mock = mock(File.class);
        when(mock.getPath()).thenReturn("/");
        return new FilePath(mock);
    }

    private FilteredLog createLogger() {
        return new FilteredLog("errors");
    }
}
