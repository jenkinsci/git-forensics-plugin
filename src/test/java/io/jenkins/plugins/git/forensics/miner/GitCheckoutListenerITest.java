package io.jenkins.plugins.git.forensics.miner;

import java.io.IOException;
import java.util.Collections;

import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.SubmoduleConfig;
import hudson.plugins.git.extensions.GitSCMExtension;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests the class {@link GitCheckoutListener}.
 */
public class GitCheckoutListenerITest {
    /** Jenkins rule per suite. */
    @ClassRule
    public static final JenkinsRule JENKINS_PER_SUITE = new JenkinsRule();

    /**
     * Verifies that the statistics about a repository are correctly evaluated.
     */
    @Test
    public void shouldInitiateForensicsAnalyzer() {
        FreeStyleProject job = createJob();

        FreeStyleBuild build = scheduleSuccessfulBuild(job);

        assertThat(getConsoleLog(build)).contains(
                "[Git Forensics] Analyzed history of",
                "[Git Forensics] File with most commits",
                "[Git Forensics] File with most number of authors",
                "[Git Forensics] Least recently modified file");
    }

    private String getConsoleLog(final FreeStyleBuild build) {
        try {
            return JenkinsRule.getLog(build);
        }
        catch (IOException exception) {
            throw new AssertionError(exception);
        }
    }

    @SuppressWarnings("IllegalCatch")
    private FreeStyleBuild scheduleSuccessfulBuild(final FreeStyleProject job) {
        try {
            return JENKINS_PER_SUITE.buildAndAssertSuccess(job);
        }
        catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private FreeStyleProject createJob() {
        try {
            FreeStyleProject project = createFreeStyleProject();
            GitSCM scm = new GitSCM(
                    GitSCM.createRepoList("https://github.com/jenkinsci/warnings-ng-plugin.git", null),
                    Collections.emptyList(), false, Collections.<SubmoduleConfig>emptyList(),
                    null, null, Collections.<GitSCMExtension>emptyList());
            project.setScm(scm);
            return project;
        }
        catch (IOException exception) {
            throw new AssertionError(exception);
        }
    }

    private  FreeStyleProject createFreeStyleProject() {
        try {
            return JENKINS_PER_SUITE.createProject(FreeStyleProject.class);
        }
        catch (IOException e) {
            throw new AssertionError(e);
        }
    }

}
