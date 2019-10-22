package io.jenkins.plugins.git.forensics.miner;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.assertj.core.util.Lists;
import org.eclipse.jgit.lib.ObjectId;
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

import io.jenkins.plugins.forensics.miner.RepositoryMiner;
import io.jenkins.plugins.forensics.util.FilteredLog;
import io.jenkins.plugins.git.forensics.GitRepositoryValidator;

import static io.jenkins.plugins.forensics.assertions.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests the class {@link GitMinerFactory}.
 *
 * @author Ullrich Hafner
 */
public class GitMinerFactoryTest {
    private static final TaskListener NULL_LISTENER = TaskListener.NULL;

    @Test
    void shouldSkipIfScmIsNotGit() {
        FilteredLog logger = createLogger();

        GitMinerFactory factory = new GitMinerFactory();
        assertThat(factory.createMiner(new NullSCM(), null, null, NULL_LISTENER, logger)).isEmpty();

        assertThat(logger).hasNoErrorMessages();
        assertThat(logger).hasInfoMessages("SCM 'hudson.scm.NullSCM' is not of type GitSCM");
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
        ObjectId commit = mock(ObjectId.class);
        when(gitClient.revParse(anyString())).thenReturn(commit);

        FilteredLog logger = createLogger();

        GitMinerFactory factory = new GitMinerFactory();
        Optional<RepositoryMiner> blamer = factory.createMiner(gitSCM, run, null, NULL_LISTENER, logger);

        assertThat(blamer).isNotEmpty().containsInstanceOf(GitRepositoryMiner.class);
        assertThat(logger).hasNoErrorMessages();
        assertThat(logger).hasInfoMessages(GitMinerFactory.INFO_MINER_CREATED);
    }

    @Test
    void shouldCreateNullBlamerOnShallowGit() {
        CloneOption shallowCloneOption = mock(CloneOption.class);
        when(shallowCloneOption.isShallow()).thenReturn(true);

        GitSCM gitSCM = mock(GitSCM.class);
        when(gitSCM.getExtensions()).thenReturn(new DescribableList<>(Saveable.NOOP, Lists.list(shallowCloneOption)));

        FilteredLog logger = createLogger();

        GitMinerFactory gitChecker = new GitMinerFactory();

        assertThat(gitChecker.createMiner(gitSCM, mock(Run.class), null, NULL_LISTENER, logger)).isEmpty();
        assertThat(logger).hasInfoMessages(GitRepositoryValidator.INFO_SHALLOW_CLONE);
        assertThat(logger).hasNoErrorMessages();
    }

    @Test
    void shouldCreateNullBlamerOnError() throws IOException, InterruptedException {
        GitMinerFactory gitChecker = new GitMinerFactory();
        Run<?, ?> run = mock(Run.class);
        List<GitSCMExtension> extensions = new ArrayList<>();
        GitSCM gitSCM = new GitSCM(null, null, false, null, null, null, extensions);

        when(run.getEnvironment(NULL_LISTENER)).thenThrow(new IOException());

        FilteredLog logger = createLogger();

        assertThat(gitChecker.createMiner(gitSCM, run, null, NULL_LISTENER, logger)).isEmpty();
        assertThat(logger).hasNoErrorMessages();
        assertThat(logger).hasInfoMessages("Exception while creating a GitClient instance for work tree 'null'");
    }

    private FilteredLog createLogger() {
        return new FilteredLog("errors");
    }
}
