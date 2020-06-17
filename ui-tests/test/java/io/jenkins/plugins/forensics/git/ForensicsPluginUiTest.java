package io.jenkins.plugins.forensics.git;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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

import io.jenkins.plugins.forensics.ForensicsPublisher;

/**
 * Acceptance tests for the Git Forensics Plugin.
 *
 * @author Ullrich Hafner
 */
@WithPlugins({"forensics-api", "git-forensics"})
public class ForensicsPluginUiTest extends AbstractJUnitTest {
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

    /**
     * Finds a resource with the given name and returns the content (decoded with UTF-8) as String.
     *
     * @param fileName
     *         name of the desired resource
     *
     * @return the content represented as {@link String}
     */
    protected String readFileToString(final String fileName) {
        return new String(readAllBytes(fileName), StandardCharsets.UTF_8);
    }

    /**
     * Reads the contents of the desired resource. The rules for searching resources associated with this test class are
     * implemented by the defining {@linkplain ClassLoader class loader} of this test class.  This method delegates to
     * this object's class loader.  If this object was loaded by the bootstrap class loader, the method delegates to
     * {@link ClassLoader#getSystemResource}.
     * <p>
     * Before delegation, an absolute resource name is constructed from the given resource name using this algorithm:
     * </p>
     * <ul>
     * <li> If the {@code name} begins with a {@code '/'} (<tt>'&#92;u002f'</tt>), then the absolute name of the
     * resource is the portion of the {@code name} following the {@code '/'}.</li>
     * <li> Otherwise, the absolute name is of the following form:
     * <blockquote> {@code modified_package_name/name} </blockquote>
     * <p> Where the {@code modified_package_name} is the package name of this object with {@code '/'}
     * substituted for {@code '.'} (<tt>'&#92;u002e'</tt>).</li>
     * </ul>
     *
     * @param fileName
     *         name of the desired resource
     *
     * @return the content represented by a byte array
     */
    private byte[] readAllBytes(final String fileName) {
        try {
            return Files.readAllBytes(getPath(fileName));
        }
        catch (IOException | URISyntaxException e) {
            throw new AssertionError("Can't read resource " + fileName, e);
        }
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

