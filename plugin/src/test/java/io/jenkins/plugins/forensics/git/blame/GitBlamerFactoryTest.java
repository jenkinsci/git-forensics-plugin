package io.jenkins.plugins.forensics.git.blame;

import java.io.File;
import java.io.IOException;
import java.util.Optional;

import org.assertj.core.util.Lists;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.jupiter.api.Test;

import edu.hm.hafner.util.FilteredLog;

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

import io.jenkins.plugins.forensics.blame.Blamer;
import io.jenkins.plugins.forensics.git.util.GitRepositoryValidator;

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

        assertThat(logger.getErrorMessages()).isEmpty();
        assertThat(logger.getInfoMessages()).contains("SCM 'hudson.scm.NullSCM' is not of type GitSCM");
    }

    @Test
    void shouldCreateBlamerForGit() throws IOException, InterruptedException {
        GitSCM gitSCM = createGitScm();

        Run<?, ?> run = mock(Run.class);

        EnvVars envVars = new EnvVars();
        envVars.put("GIT_COMMIT", "test_commit");
        when(run.getEnvironment(NULL_LISTENER)).thenReturn(envVars);

        GitClient gitClient = mock(GitClient.class);
        FilePath workspace = createWorkTreeStub();
        ObjectId commit = mock(ObjectId.class);
        when(gitClient.revParse(anyString())).thenReturn(commit);
        when(gitClient.getWorkTree()).thenReturn(new FilePath(new File("/working-tree")));

        when(gitSCM.createClient(NULL_LISTENER, envVars, run, workspace)).thenReturn(gitClient);
        FilteredLog logger = createLogger();

        GitBlamerFactory factory = new GitBlamerFactory();
        Optional<Blamer> blamer = factory.createBlamer(gitSCM, run, workspace, NULL_LISTENER, logger);

        assertThat(blamer).isNotEmpty().containsInstanceOf(GitBlamer.class);
        assertThat(logger.getErrorMessages()).isEmpty();
        assertThat(logger.getInfoMessages()).contains("-> Git blamer successfully created in working tree '/working-tree'");
    }

    private FilePath createWorkTreeStub() {
        File mock = mock(File.class);
        when(mock.getPath()).thenReturn("/");
        return new FilePath(mock);
    }

    @Test
    void shouldCreateNullBlamerOnShallowGit() {
        CloneOption shallowCloneOption = mock(CloneOption.class);
        when(shallowCloneOption.isShallow()).thenReturn(true);

        GitSCM git = mock(GitSCM.class);
        when(git.getExtensions()).thenReturn(new DescribableList<>(Saveable.NOOP, Lists.list(shallowCloneOption)));

        FilteredLog logger = createLogger();

        GitBlamerFactory gitChecker = new GitBlamerFactory();

        assertThat(gitChecker.createBlamer(git, mock(Run.class), null, NULL_LISTENER, logger)).isEmpty();
        assertThat(logger.getInfoMessages()).contains(GitRepositoryValidator.INFO_SHALLOW_CLONE);
        assertThat(logger.getErrorMessages()).isEmpty();
    }

    @Test
    void shouldCreateNullBlamerOnError() throws IOException, InterruptedException {
        GitBlamerFactory gitChecker = new GitBlamerFactory();

        Run<?, ?> run = mock(Run.class);
        when(run.getEnvironment(NULL_LISTENER)).thenThrow(new IOException("Error"));

        FilteredLog logger = createLogger();

        assertThat(gitChecker.createBlamer(createGitScm(), run, null, NULL_LISTENER, logger)).isEmpty();
        assertThat(logger.getErrorMessages()).isEmpty();
        assertThat(logger.getInfoMessages()).contains(
                "Exception while creating a GitClient instance for work tree 'null'");
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
