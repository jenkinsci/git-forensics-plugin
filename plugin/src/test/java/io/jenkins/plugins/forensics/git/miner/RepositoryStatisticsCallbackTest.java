package io.jenkins.plugins.forensics.git.miner;

import org.eclipse.jgit.lib.Repository;
import org.junit.jupiter.api.Test;
import org.junitpioneer.jupiter.Issue;

import hudson.remoting.VirtualChannel;

import static org.mockito.Mockito.*;

/**
 * Tests the class {@link RepositoryStatisticsCallback}.
 *
 * @author Akash Manna
 */
class RepositoryStatisticsCallbackTest {
    @Test
    @SuppressWarnings("PMD.CloseResource")
    @Issue("JENKINS-74804")
    void invokeShouldNotCloseTheRepository() throws Exception {
        var repository = mock(Repository.class);
        when(repository.resolve(anyString())).thenReturn(null);

        var callback = new RepositoryStatisticsCallback("some-previous-commit");

        callback.invoke(repository, mock(VirtualChannel.class));

        verify(repository, never()).close();
    }
}