package io.jenkins.plugins.forensics.git;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.inject.Inject;

import org.junit.Before;
import org.junit.Test;

import org.jenkinsci.test.acceptance.docker.DockerContainerHolder;
import org.jenkinsci.test.acceptance.docker.fixtures.GitContainer;
import org.jenkinsci.test.acceptance.junit.AbstractJUnitTest;
import org.jenkinsci.test.acceptance.junit.WithPlugins;
import org.jenkinsci.test.acceptance.plugins.git.GitRepo;
import org.jenkinsci.test.acceptance.plugins.git.GitScm;
import org.jenkinsci.test.acceptance.plugins.maven.MavenInstallation;
import org.jenkinsci.test.acceptance.plugins.maven.MavenModuleSet;
import org.jenkinsci.test.acceptance.plugins.warnings_ng.ScrollerUtil;
import org.jenkinsci.test.acceptance.po.Build;
import org.jenkinsci.test.acceptance.po.FreeStyleJob;
import org.jenkinsci.test.acceptance.po.Job;

import io.jenkins.plugins.forensics.ForensicsPublisher;

/**
 * Acceptance tests for the Git Forensics Plugin.
 *
 * @author Ullrich Hafner
 */
@WithPlugins({"forensics-api", "git-forensics"})
public class ForensicsPluginUiTest extends AbstractJUnitTest {

    public static final String repoUrl = "www.woatschn.de";
    public static final String USERNAME = "forensicsTester";

    /**
     * Tests the build overview page by running two builds that aggregate the three different tools into a single
     * result. Checks the contents of the result summary.
     */
    @Test
    public void shouldAggregateToolsIntoSingleResult() {
        FreeStyleJob job = createFreeStyleJob();
        job.addPublisher(ForensicsPublisher.class);

        Build referenceBuild = shouldBuildSuccessfully(job);
        referenceBuild.open();
    }

    @Test
    public void shouldDoSomething() {
        FreeStyleJob job = createFreeStyleJob();
        job.addPublisher(ForensicsPublisher.class);

        job.useScm(GitScm.class)
                .url(repoUrl)
                .credentials(USERNAME);
        job.addShellStep("git checkout 8a52f0de17d8d4d8cb8bfa23c0b62f42991d183f");
        job.save();
        job.startBuild().shouldSucceed();
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

