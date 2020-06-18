package io.jenkins.plugins.forensics.git;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Test;

import org.jenkinsci.test.acceptance.junit.AbstractJUnitTest;
import org.jenkinsci.test.acceptance.junit.WithPlugins;
import org.jenkinsci.test.acceptance.plugins.maven.MavenInstallation;
import org.jenkinsci.test.acceptance.plugins.maven.MavenModuleSet;
import org.jenkinsci.test.acceptance.plugins.warnings_ng.ScrollerUtil;
import org.jenkinsci.test.acceptance.po.Build;
import org.jenkinsci.test.acceptance.po.FreeStyleJob;
import org.jenkinsci.test.acceptance.po.Job;
import hudson.plugins.git.GitSCM;
import jenkins.plugins.git.AbstractGitSCMSource;
import jenkins.plugins.git.GitSCMBuilder;
import jenkins.scm.api.SCMHead;

import io.jenkins.plugins.forensics.ForensicsPublisher;

/**
 * Acceptance tests for the Git Forensics Plugin.
 *
 * @author Ullrich Hafner
 */
@WithPlugins({"forensics-api", "git-forensics"})
public class ForensicsPluginUiTest extends AbstractJUnitTest {

    private final SCMHead head = new SCMHead("master");
    private final GitSCMBuilder<?> instance = new GitSCMBuilder<>(head, new AbstractGitSCMSource.SCMRevisionImpl(head, "a5fc98f679f1e12ba5d7152521904e1c40ccf971"), "https://github.com/jenkinsci/git-forensics-plugin.git", null);

    /**
     * Tests the build overview page by running two builds that aggregate the three different tools into a single
     * result. Checks the contents of the result summary.
     */
    @Test
    public void shouldAggregateToolsIntoSingleResult() {
        FreeStyleJob job = createFreeStyleJob();
        job.addPublisher(ForensicsPublisher.class);

        job.save();

        Build referenceBuild = shouldBuildSuccessfully(job);
        referenceBuild.open();
    }

    @Test
    public void shouldDoSomething() {
        FreeStyleJob job = createFreeStyleJob();
        job.addPublisher(ForensicsPublisher.class);
        job.save();


        GitSCM scm = instance.build();

        Build someBuild = shouldBuildSuccessfully(job);
    }

    private FreeStyleJob createFreeStyleJob(final String... resourcesToCopy) {
        FreeStyleJob job = jenkins.getJobs().create(FreeStyleJob.class);
        ScrollerUtil.hideScrollerTabBar(driver);
        for (String resource : resourcesToCopy) {
            job.copyResource("/" + resource);
        }
        return job;
    }

    protected Build shouldBuildSuccessfully(final Job job) {
        return job.startBuild().waitUntilFinished().shouldSucceed();
    }

    protected Build buildJob(final Job job) {
        return job.startBuild().waitUntilFinished();
    }

    Path getPath(final String name) throws URISyntaxException {
        URL resource = getClass().getResource(name);
        if (resource == null) {
            throw new AssertionError("Can't find resource " + name);
        }
        return Paths.get(resource.toURI());
    }
    protected void copyResourceFilesToWorkspace(final Job job, final String... resources) {
        for (String file : resources) {
            job.copyResource(file);
        }
    }

    protected MavenModuleSet createMavenProject() {
        MavenInstallation.installMaven(jenkins, MavenInstallation.DEFAULT_MAVEN_ID, "3.6.3");

        return jenkins.getJobs().create(MavenModuleSet.class);
    }

}

