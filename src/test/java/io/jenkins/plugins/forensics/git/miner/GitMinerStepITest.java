package io.jenkins.plugins.forensics.git.miner;

import java.io.IOException;

import org.junit.Test;

import hudson.model.FreeStyleProject;
import hudson.model.Run;
import hudson.plugins.git.GitSCM;

import io.jenkins.plugins.forensics.git.util.GitITest;
import io.jenkins.plugins.forensics.miner.RepositoryMinerStep;

/**
 * Integration tests for the {@link RepositoryMinerStep} using a Git repository.
 *
 * @author Ullrich Hafner
 */
public class GitMinerStepITest extends GitITest {
    /** Loads the web page with enabled JS. */
    @Test
    public void shouldLoadJS() throws IOException {
        writeFileAsAuthorFoo("First");
        writeFileAsAuthorBar("Second");
        writeFileAsAuthorFoo("First");
        writeFileAsAuthorBar("Second");

        FreeStyleProject job = createFreeStyleProject();
        job.setScm(new GitSCM(getRepositoryRoot()));
        job.getPublishersList().add(new RepositoryMinerStep());

        Run<?, ?> build = buildSuccessfully(job);

        getWebPage(JavaScriptSupport.JS_ENABLED, build, "forensics");
    }
}
