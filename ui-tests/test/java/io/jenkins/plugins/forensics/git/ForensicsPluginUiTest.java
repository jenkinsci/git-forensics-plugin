package io.jenkins.plugins.forensics.git;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.junit.Test;

import org.jenkinsci.test.acceptance.junit.AbstractJUnitTest;
import org.jenkinsci.test.acceptance.junit.WithPlugins;
import org.jenkinsci.test.acceptance.plugins.git.GitScm;
import org.jenkinsci.test.acceptance.plugins.maven.MavenInstallation;
import org.jenkinsci.test.acceptance.plugins.maven.MavenModuleSet;
import org.jenkinsci.test.acceptance.plugins.warnings_ng.ScrollerUtil;
import org.jenkinsci.test.acceptance.po.Build;
import org.jenkinsci.test.acceptance.po.FreeStyleJob;
import org.jenkinsci.test.acceptance.po.Job;

import io.jenkins.plugins.forensics.DetailsTable;
import io.jenkins.plugins.forensics.DetailsTableRow;
import io.jenkins.plugins.forensics.ForensicsPublisher;
import io.jenkins.plugins.forensics.ScmForensics;

/**
 * Acceptance tests for the Git Forensics Plugin.
 *
 * @author Ullrich Hafner
 */
@WithPlugins({"forensics-api", "git-forensics", "git"})
public class ForensicsPluginUiTest extends AbstractJUnitTest {

    private static final String repositoryUrl = "https://github.com/jenkinsci/git-forensics-plugin.git";

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
                .url(repositoryUrl)
                .branch("28af63def44286729e3b19b03464d100fd1d0587");
        job.save();
        Build build = shouldBuildSuccessfully(job);

        ScmForensics scmForensics = new ScmForensics(build, "forensics");
        scmForensics.open();
        DetailsTable detailsTable = new DetailsTable(scmForensics);
        int size = detailsTable.getHeaderSize();
        List<DetailsTableRow> detailsTableRows = detailsTable.getTableRows();
        System.out.println(detailsTableRows.get(1).getNumberOfAuthors());
        System.out.println(detailsTable.getForensicsInfo());
        detailsTable.clickOnPagination(2);
        System.out.println(detailsTable.getForensicsInfo());
        detailsTable.searchTable("plugin/src/test/resources/design.puml");
        System.out.println(detailsTable.getForensicsInfo());
        detailsTable.clearSearch();
        detailsTable.showFiftyEntries();
        System.out.println(detailsTable.getForensicsInfo());
        detailsTable.sortColumn("#Authors");
        detailsTable.sortColumn("File");
        detailsTable.sortColumn("#Commits");
        System.out.println(detailsTableRows.get(1).getFileName());
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

