package io.jenkins.plugins.forensics.git.util;

import org.assertj.core.util.Lists;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.jupiter.api.Test;

import edu.hm.hafner.util.FilteredLog;

import java.io.File;
import java.io.IOException;

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

import static io.jenkins.plugins.forensics.assertions.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests the class {@link GitRepositoryValidator}.
 *
 * @author Akash Manna
 */
class GitRepositoryValidatorTest {
    private static final TaskListener NULL_LISTENER = TaskListener.NULL;

    @Test
    void isGitRepositoryShouldReturnFalseForNonGitScm() {
        var logger = createLogger();
        var validator = new GitRepositoryValidator(new NullSCM(), null, createWorkTree(), NULL_LISTENER, logger);

        assertThat(validator.isGitRepository()).isFalse();
        assertThat(logger.getInfoMessages()).contains("SCM 'hudson.scm.NullSCM' is not of type GitSCM");
    }

    @Test
    void isGitRepositoryShouldReturnTrueForNonShallowGit() throws IOException, InterruptedException {
        GitSCM gitSCM = createNonShallowGitScm();
        Run<?, ?> run = mock(Run.class);
        var envVars = new EnvVars();
        when(run.getEnvironment(NULL_LISTENER)).thenReturn(envVars);
        var workspace = createWorkTree();
        GitClient gitClient = mock(GitClient.class);
        when(gitSCM.createClient(NULL_LISTENER, envVars, run, workspace)).thenReturn(gitClient);
        when(gitClient.revParse(anyString())).thenReturn(mock(ObjectId.class));

        var logger = createLogger();
        var validator = new GitRepositoryValidator(gitSCM, run, workspace, NULL_LISTENER, logger);

        assertThat(validator.isGitRepository()).isTrue();
        assertThat(logger.getInfoMessages()).doesNotContain(GitRepositoryValidator.INFO_SHALLOW_CLONE);
        assertThat(logger.getInfoMessages()).doesNotContain(GitRepositoryValidator.INFO_SHALLOW_CLONE_COMMIT_RECORDING);
    }

    @Test
    void isGitRepositoryShouldReturnTrueForShallowClone() throws IOException, InterruptedException {
        GitSCM gitSCM = createShallowGitScm();
        Run<?, ?> run = mock(Run.class);
        var envVars = new EnvVars();
        when(run.getEnvironment(NULL_LISTENER)).thenReturn(envVars);
        var workspace = createWorkTree();
        GitClient gitClient = mock(GitClient.class);
        when(gitSCM.createClient(NULL_LISTENER, envVars, run, workspace)).thenReturn(gitClient);
        when(gitClient.revParse(anyString())).thenReturn(mock(ObjectId.class));

        var logger = createLogger();
        var validator = new GitRepositoryValidator(gitSCM, run, workspace, NULL_LISTENER, logger);

        assertThat(validator.isGitRepository()).isTrue();
        assertThat(logger.getInfoMessages()).contains(GitRepositoryValidator.INFO_SHALLOW_CLONE_COMMIT_RECORDING);
        assertThat(logger.getInfoMessages()).doesNotContain(GitRepositoryValidator.INFO_SHALLOW_CLONE);
    }

    @Test
    void isFullGitRepositoryShouldReturnFalseForNonGitScm() {
        var logger = createLogger();
        var validator = new GitRepositoryValidator(new NullSCM(), null, createWorkTree(), NULL_LISTENER, logger);

        assertThat(validator.isFullGitRepository()).isFalse();
        assertThat(logger.getInfoMessages()).contains("SCM 'hudson.scm.NullSCM' is not of type GitSCM");
    }

    @Test
    void isFullGitRepositoryShouldReturnTrueForNonShallowGit() throws IOException, InterruptedException {
        GitSCM gitSCM = createNonShallowGitScm();
        Run<?, ?> run = mock(Run.class);
        var envVars = new EnvVars();
        when(run.getEnvironment(NULL_LISTENER)).thenReturn(envVars);
        var workspace = createWorkTree();
        GitClient gitClient = mock(GitClient.class);
        when(gitSCM.createClient(NULL_LISTENER, envVars, run, workspace)).thenReturn(gitClient);
        when(gitClient.revParse(anyString())).thenReturn(mock(ObjectId.class));

        var logger = createLogger();
        var validator = new GitRepositoryValidator(gitSCM, run, workspace, NULL_LISTENER, logger);

        assertThat(validator.isFullGitRepository()).isTrue();
        assertThat(logger.getInfoMessages()).doesNotContain(GitRepositoryValidator.INFO_SHALLOW_CLONE);
    }

    @Test
    void isFullGitRepositoryShouldReturnFalseForShallowClone() {
        CloneOption shallowOption = mock(CloneOption.class);
        when(shallowOption.isShallow()).thenReturn(true);

        GitSCM gitSCM = mock(GitSCM.class);
        when(gitSCM.getExtensions()).thenReturn(new DescribableList<>(Saveable.NOOP, Lists.list(shallowOption)));

        var logger = createLogger();
        var validator = new GitRepositoryValidator(gitSCM, mock(Run.class), createWorkTree(), NULL_LISTENER, logger);

        assertThat(validator.isFullGitRepository()).isFalse();
        assertThat(logger.getInfoMessages()).contains(GitRepositoryValidator.INFO_SHALLOW_CLONE);
        assertThat(logger.getInfoMessages()).doesNotContain(GitRepositoryValidator.INFO_SHALLOW_CLONE_COMMIT_RECORDING);
    }

    @Test
    void isShallowCloneShouldReturnFalseForNonGitScm() {
        var validator = new GitRepositoryValidator(new NullSCM(), null, createWorkTree(), NULL_LISTENER, createLogger());

        assertThat(validator.isShallowClone()).isFalse();
    }

    @Test
    void isShallowCloneShouldReturnFalseForNonShallowGit() {
        var validator = new GitRepositoryValidator(createNonShallowGitScm(), null, createWorkTree(), NULL_LISTENER, createLogger());

        assertThat(validator.isShallowClone()).isFalse();
    }

    @Test
    void isShallowCloneShouldReturnTrueForShallowGit() {
        var validator = new GitRepositoryValidator(createShallowGitScm(), null, createWorkTree(), NULL_LISTENER, createLogger());

        assertThat(validator.isShallowClone()).isTrue();
    }

    private GitSCM createNonShallowGitScm() {
        GitSCM git = mock(GitSCM.class);
        when(git.getExtensions()).thenReturn(new DescribableList<>(Saveable.NOOP));
        return git;
    }

    private GitSCM createShallowGitScm() {
        CloneOption shallowOption = mock(CloneOption.class);
        when(shallowOption.isShallow()).thenReturn(true);

        GitSCM git = mock(GitSCM.class);
        when(git.getExtensions()).thenReturn(new DescribableList<>(Saveable.NOOP, Lists.list(shallowOption)));
        return git;
    }

    private FilePath createWorkTree() {
        File mock = mock(File.class);
        when(mock.getPath()).thenReturn("/");
        return new FilePath(mock);
    }

    private FilteredLog createLogger() {
        return new FilteredLog("errors");
    }
}
