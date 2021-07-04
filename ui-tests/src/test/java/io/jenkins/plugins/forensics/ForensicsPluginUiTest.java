package io.jenkins.plugins.forensics;

import java.util.List;

import org.junit.Test;
import org.openqa.selenium.By;

import org.jenkinsci.test.acceptance.junit.AbstractJUnitTest;
import org.jenkinsci.test.acceptance.junit.WithPlugins;
import org.jenkinsci.test.acceptance.po.Build;
import org.jenkinsci.test.acceptance.po.Job;
import org.jenkinsci.test.acceptance.po.WorkflowJob;

import static io.jenkins.plugins.forensics.DetailsTable.*;
import static org.assertj.core.api.Assertions.*;

/**
 * Acceptance tests for the Git Forensics Plugin.
 *
 * @author Ullrich Hafner
 */
@WithPlugins({"forensics-api", "git-forensics", "git", "workflow-durable-task-step", "workflow-basic-steps"})
public class ForensicsPluginUiTest extends AbstractJUnitTest {
    private static final String REPOSITORY_URL = "https://github.com/jenkinsci/git-forensics-plugin.git";
    private static final int GIT_SUMMARY_ROW = 2;
    private static final int COMMIT_RECORDER_ROW = 3;
    private static final int MINER_ROW = 4;

    /**
     * Verifies the Git miner by running a build with the forensics plugin analyzing a commit hash of the
     * git-forensics-plugin repository. Checks the contents of the summary and details view.
     */
    @Test
    public void shouldAggregateToolsIntoSingleResult() {
        WorkflowJob job = createJob();
        Build build = buildSuccessfully(job);

        assertThat(build.getConsole()).contains(
                "Found 428 commits",
                "-> 16510 lines added",
                "-> 10444 lines deleted");

        build.open();
        assertThat(getSummaryText(build, GIT_SUMMARY_ROW)).contains(
                "Revision: 28af63def44286729e3b19b03464d100fd1d0587", "detached");

        // TODO: create page objects
        assertThat(getSummaryText(build, COMMIT_RECORDER_ROW)).contains(
                "SCM: git " + REPOSITORY_URL,
                "Initial recording of 200 commits",
                "Latest commit: 28af63d");

        assertThat(getSummaryText(build, MINER_ROW)).contains(
                "New commits: 402",
                "4 authors",
                "131 files",
                "16510 added",
                "10444 deleted");

        ScmForensics scmForensics = new ScmForensics(build, "forensics");
        scmForensics.open();
        DetailsTable detailsTable = new DetailsTable(scmForensics);

        assertThat(scmForensics.getTotal()).isEqualTo(51);
        assertTableHeaders(detailsTable);
        assertTableEntriesAndSorting(detailsTable);
        assertSearch(detailsTable);
        assertPagination(detailsTable);
    }

    private WorkflowJob createJob() {
        WorkflowJob job = jenkins.jobs.create(WorkflowJob.class);
        job.sandbox.check();
        job.script.set("node {\n"
                + "  checkout([$class: 'GitSCM', branches: [[name: '28af63def44286729e3b19b03464d100fd1d0587' ]],\n"
                + "     userRemoteConfigs: [[url: '" + REPOSITORY_URL + "']]])\n"
                + "  mineRepository() \n"
                + "} \n");
        job.save();
        return job;
    }

    private String getSummaryText(final Build referenceBuild, final int row) {
        return referenceBuild.getElement(
                By.xpath("/html/body/div[4]/div[2]/table/tbody/tr[" + row + "]/td[2]")).getText();
    }

    /**
     * asserts the headers of the table by their size and entries.
     *
     * @param detailsTable
     *         detailsTable object we want to assert the headers for.
     */
    private void assertTableHeaders(final DetailsTable detailsTable) {
        assertThat(detailsTable.getHeaderSize()).isEqualTo(7);

        List<String> tableHeaders = detailsTable.getHeaders();
        assertThat(tableHeaders.get(0)).isEqualTo(FILE_NAME);
        assertThat(tableHeaders.get(1)).isEqualTo(AUTHORS);
        assertThat(tableHeaders.get(2)).isEqualTo(COMMITS);
        assertThat(tableHeaders.get(3)).isEqualTo(LAST_COMMIT);
        assertThat(tableHeaders.get(4)).isEqualTo(ADDED);
        assertThat(tableHeaders.get(5)).isEqualTo(LOC);
        assertThat(tableHeaders.get(6)).isEqualTo(CHURN);
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

        detailsTable.sortColumn(FILE_NAME);
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
                "GitBlamer.java",
                3,
                46
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
        assertThat(detailsTable.getNumberOfTableEntries()).isEqualTo(1);

        detailsTable.sortColumn(AUTHORS);
        detailsTable.sortColumn(AUTHORS);

        assertRow(detailsTable,
                0,
                "pom.xml",
                2,
                2
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

    private void assertRow(final DetailsTable detailsTable,
            final int rowNum, final String fileName, final int numAuthors, final int numCommits) {
        DetailsTableRow secondRow = detailsTable.getTableRows().get(rowNum);

        assertThat(secondRow.getFileName()).isEqualTo(fileName);
        assertThat(secondRow.getNumberOfAuthors()).isEqualTo(numAuthors);
        assertThat(secondRow.getNumberOfCommits()).isEqualTo(numCommits);
    }

    protected Build buildSuccessfully(final Job job) {
        return job.startBuild().waitUntilFinished().shouldSucceed();
    }
}

