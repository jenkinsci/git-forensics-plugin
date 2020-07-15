package io.jenkins.plugins.forensics.git;

import java.util.List;

import org.junit.Test;
import org.openqa.selenium.By;

import org.jenkinsci.test.acceptance.junit.AbstractJUnitTest;
import org.jenkinsci.test.acceptance.junit.WithPlugins;
import org.jenkinsci.test.acceptance.plugins.git.GitScm;
import org.jenkinsci.test.acceptance.plugins.warnings_ng.ScrollerUtil;
import org.jenkinsci.test.acceptance.po.Build;
import org.jenkinsci.test.acceptance.po.FreeStyleJob;
import org.jenkinsci.test.acceptance.po.Job;

import io.jenkins.plugins.forensics.DetailsTable;
import io.jenkins.plugins.forensics.DetailsTableRow;
import io.jenkins.plugins.forensics.ForensicsPublisher;
import io.jenkins.plugins.forensics.ScmForensics;

import static io.jenkins.plugins.forensics.DetailsTable.*;
import static org.assertj.core.api.Assertions.*;

/**
 * Acceptance tests for the Git Forensics Plugin.
 *
 * @author Ullrich Hafner
 */
@WithPlugins({"forensics-api", "git-forensics", "git"})
public class ForensicsPluginUiTest extends AbstractJUnitTest {

    private static final String REPOSITORY_URL = "https://github.com/jenkinsci/git-forensics-plugin.git";

    /**
     * Tests the build overview page by running a Build with the forensics plugin analyzing a commit hash of the
     * git-forensics-plugin repository. Checks the contents of the result summary.
     */
    @Test
    public void shouldAggregateToolsIntoSingleResult() {
        FreeStyleJob job = createFreeStyleJob();
        job.addPublisher(ForensicsPublisher.class);

        job.useScm(GitScm.class)
                .url(REPOSITORY_URL)
                .branch("28af63def44286729e3b19b03464d100fd1d0587");
        job.save();
        Build referenceBuild = shouldBuildSuccessfully(job);
        referenceBuild.open();

        String gitRevision = referenceBuild.getElement(
                By.xpath("/html/body/div[4]/div[2]/table/tbody/tr[3]/td[2]"))
                .getText();
        String scmStatistics = referenceBuild.getElement(
                By.xpath("/html/body/div[4]/div[2]/table/tbody/tr[4]/td[2]"))
                .getText();

        assertThat(gitRevision).isEqualTo("Revision: 28af63def44286729e3b19b03464d100fd1d0587\n"
                + "detached");
        assertThat(scmStatistics).isEqualTo("SCM Repository Statistics: 51 repository files\n"
                + "Created report in 1 seconds");
    }

    /**
     * Tests the Details table on ScmForensics page.
     */
    @Test
    public void shouldShowTableWithCompleteFunctionality() {
        FreeStyleJob job = createFreeStyleJob();
        job.addPublisher(ForensicsPublisher.class);

        job.useScm(GitScm.class)
                .url(REPOSITORY_URL)
                .branch("28af63def44286729e3b19b03464d100fd1d0587");
        job.save();
        Build build = shouldBuildSuccessfully(job);

        ScmForensics scmForensics = new ScmForensics(build, "forensics");
        scmForensics.open();
        DetailsTable detailsTable = new DetailsTable(scmForensics);

        assertThat(scmForensics.getTotal()).isEqualTo(51);
        assertTableHeaders(detailsTable);
        assertTableEntriesAndSorting(detailsTable);
        assertSearch(detailsTable);
        assertPagination(detailsTable);
    }

    /**
     * asserts the headers of the table by their size and entries.
     *
     * @param detailsTable
     *         detailsTable object we want to assert the headers for.
     */
    private void assertTableHeaders(final DetailsTable detailsTable) {
        assertThat(detailsTable.getHeaderSize()).isEqualTo(5);

        List<String> tableHeaders = detailsTable.getHeaders();
        assertThat(tableHeaders.get(0)).isEqualTo(DetailsTable.FILE);
        assertThat(tableHeaders.get(1)).isEqualTo(AUTHORS);
        assertThat(tableHeaders.get(2)).isEqualTo(COMMITS);
        assertThat(tableHeaders.get(3)).isEqualTo(LAST_COMMIT);
        assertThat(tableHeaders.get(4)).isEqualTo(ADDED);
    }

    /**
     * asserts the certain table entries and then assert them again after sorting.
     *
     * @param detailsTable
     *         detailsTable object we want to assert the entries for.
     */
    private void assertTableEntriesAndSorting(final DetailsTable detailsTable) {
        assertThat(detailsTable.getNumberOfTableEntries()).isEqualTo(10);
        assertThat(detailsTable.getForensicsInfo()).isEqualTo("Showing 1 to 10 of 51 entries");

        detailsTable.showFiftyEntries();
        assertThat(detailsTable.getNumberOfTableEntries()).isEqualTo(50);

        detailsTable.sortColumn(DetailsTable.FILE);
        assertRow(detailsTable,
                0,
                "config.yml",
                1,
                1
        );

        detailsTable.sortColumn(AUTHORS);
        detailsTable.sortColumn(AUTHORS);
        assertRow(detailsTable,
                0,
                "pom.xml",
                4,
                298
        );

        detailsTable.sortColumn(COMMITS);
        detailsTable.sortColumn(COMMITS);
        assertRow(detailsTable,
                1,
                "README.md",
                1,
                20
        );

        detailsTable.showTenEntries();
    }

    /**
     * asserts the search of the Table by searching for a filename and then clearing the search afterwards.
     *
     * @param detailsTable
     *         detailsTable object we want to assert the search for.
     */
    private void assertSearch(final DetailsTable detailsTable) {
        detailsTable.searchTable("ui-tests/pom.xml");
        assertThat(detailsTable.getNumberOfTableEntries()).isEqualTo(2);

        detailsTable.sortColumn(AUTHORS);
        detailsTable.sortColumn(AUTHORS);

        assertRow(detailsTable,
                0,
                "pom.xml",
                4,
                298
        );
        detailsTable.clearSearch();
        assertThat(detailsTable.getTableRows().size()).isEqualTo(10);
    }

    private void assertPagination(final DetailsTable detailsTable) {
        assertThat(detailsTable.getForensicsInfo()).isEqualTo("Showing 1 to 10 of 51 entries");

        detailsTable.clickOnPagination(2);
        assertThat(detailsTable.getForensicsInfo()).isEqualTo("Showing 11 to 20 of 51 entries");

        detailsTable.showFiftyEntries();
        assertThat(detailsTable.getForensicsInfo()).isEqualTo("Showing 1 to 50 of 51 entries");

        detailsTable.clickOnPagination(2);
        assertThat(detailsTable.getForensicsInfo()).isEqualTo("Showing 51 to 51 of 51 entries");
    }

    private void assertRow(
            final DetailsTable detailsTable,
            final int rowNum,
            final String fileName,
            final int numAuthors,
            final int numCommits
    ) {
        DetailsTableRow secondRow = detailsTable.getTableRows().get(rowNum);

        assertThat(secondRow.getFileName()).isEqualTo(fileName);
        assertThat(secondRow.getNumberOfAuthors()).isEqualTo(numAuthors);
        assertThat(secondRow.getNumberOfCommits()).isEqualTo(numCommits);
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
}

