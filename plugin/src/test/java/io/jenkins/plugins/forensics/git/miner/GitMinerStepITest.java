package io.jenkins.plugins.forensics.git.miner;

import java.io.IOException;
import java.util.Objects;

import org.junit.Test;

import hudson.model.FreeStyleProject;
import hudson.model.Run;
import hudson.plugins.git.GitSCM;

import io.jenkins.plugins.datatables.TableModel;
import io.jenkins.plugins.forensics.git.util.GitITest;
import io.jenkins.plugins.forensics.miner.FileStatistics;
import io.jenkins.plugins.forensics.miner.ForensicsBuildAction;
import io.jenkins.plugins.forensics.miner.ForensicsTableModel.ForensicsRow;
import io.jenkins.plugins.forensics.miner.ForensicsViewModel;
import io.jenkins.plugins.forensics.miner.RepositoryMinerStep;
import io.jenkins.plugins.forensics.miner.RepositoryStatistics;

import static io.jenkins.plugins.forensics.assertions.Assertions.*;

/**
 * Integration tests for the {@link RepositoryMinerStep} using a Git repository.
 *
 * @author Ullrich Hafner
 */
public class GitMinerStepITest extends GitITest {
    /** Verifies that the table contains two rows with the correct statistics. */
    @Test
    public void shouldFillTableDynamically() {
        writeFileAsAuthorFoo("First");
        writeFileAsAuthorBar("Second");
        writeFileAsAuthorFoo("First");
        writeFileAsAuthorBar("Second");

        FreeStyleProject job = createJobWithMiner();

        Run<?, ?> build = buildSuccessfully(job);

        RepositoryStatistics statistics = getStatistics(build);
        assertThat(statistics).hasFiles(INITIAL_FILE, ADDITIONAL_FILE);
        assertThat(statistics).hasLatestCommitId(getHead());

        verifyStatistics(statistics, INITIAL_FILE, 1, 1);
        verifyStatistics(statistics, ADDITIONAL_FILE, 2, 4);

        TableModel forensics = getTableModel(build);
        assertThat(forensics.getRows()).hasSize(2);

        assertThat(getRow(forensics, 0))
                .hasFileName(ADDITIONAL_FILE).hasAuthorsSize(2).hasCommitsSize(4);
        assertThat(getRow(forensics, 1))
                .hasFileName(INITIAL_FILE).hasAuthorsSize(1).hasCommitsSize(1);
    }

    /** Verifies that the mining process is incremental. */
    @Test
    public void shouldMineRepositoryIncrementally() throws IOException {
        writeFileAsAuthorFoo("First");

        FreeStyleProject job = createJobWithMiner();
        Run<?, ?> build = buildSuccessfully(job);

        getJenkins().assertLogContains("created report for 2 files", build);

        RepositoryStatistics statistics = getStatistics(build);
        assertThat(statistics).hasFiles(INITIAL_FILE, ADDITIONAL_FILE);
        assertThat(statistics).hasLatestCommitId(getHead());

        getJenkins().assertLogContains("Analyzed 2 new commits", build);
        verifyStatistics(statistics, ADDITIONAL_FILE, 1, 1);
        build = buildSuccessfully(job);

        getJenkins().assertLogContains("created report for 2 files", build);
        getJenkins().assertLogContains("Analyzed 0 new commits", build);
        verifyStatistics(statistics, ADDITIONAL_FILE, 1, 1);

        writeFileAsAuthorFoo("Second");

        build = buildSuccessfully(job);

        getJenkins().assertLogContains("created report for 2 files", build);
        getJenkins().assertLogContains("Analyzed 1 new commits", build);
        verifyStatistics(statistics, ADDITIONAL_FILE, 1, 2);

        writeFileAsAuthorBar("Another content");
        build = buildSuccessfully(job);

        getJenkins().assertLogContains("created report for 2 files", build);
        verifyStatistics(statistics, ADDITIONAL_FILE, 2, 3);

    }

    /** Verifies the calculation of the #LOC and churn. */
    @Test
    public void shouldCalculateLocAndChurn() {
        writeFileAsAuthorFoo("First");

        FreeStyleProject job = createJobWithMiner();
        Run<?, ?> build = buildSuccessfully(job);

        verifyLocAndChurn(build, ADDITIONAL_FILE, 1, 1);

        build = buildSuccessfully(job);

        verifyLocAndChurn(build, ADDITIONAL_FILE, 0, 1);
        writeFileAsAuthorFoo("\nSecond");

        build = buildSuccessfully(job);
        verifyLocAndChurn(build, ADDITIONAL_FILE, 3, 2);
    }

    private void verifyStatistics(final RepositoryStatistics statistics, final String fileName,
            final int authorsSize, final int commitsSize) {
        FileStatistics additionalFileStatistics = statistics.get(fileName);
        assertThat(additionalFileStatistics).hasFileName(fileName);
        assertThat(additionalFileStatistics).hasNumberOfAuthors(authorsSize);
        assertThat(additionalFileStatistics).hasNumberOfCommits(commitsSize);
    }

    private void verifyLocAndChurn(final Run<?, ?> build, final String fileName, final int churn, final int linesOfCode) {
        RepositoryStatistics statistics = getStatistics(build);
        FileStatistics fileStatistics = statistics.get(fileName);

        assertThat(fileStatistics.getChurn()).isEqualTo(churn);
        assertThat(fileStatistics.getLinesOfCode()).isEqualTo(linesOfCode);
    }

    private RepositoryStatistics getStatistics(final Run<?, ?> build) {
        return getAction(build).getResult();
    }

    private TableModel getTableModel(final Run<?, ?> build) {
        ForensicsBuildAction forensicsBuildAction = getAction(build);

        return ((ForensicsViewModel) forensicsBuildAction.getTarget()).getTableModel("forensics");
    }

    private ForensicsBuildAction getAction(final Run<?, ?> build) {
        return Objects.requireNonNull(build.getAction(ForensicsBuildAction.class),
                "Build does not contain a ForensicsBuildAction: " + build);
    }

    private ForensicsRow getRow(final TableModel forensics, final int rowIndex) {
        Object actual = forensics.getRows().get(rowIndex);
        assertThat(actual).isInstanceOf(ForensicsRow.class);
        return (ForensicsRow) actual;
    }

    private FreeStyleProject createJobWithMiner() {
        try {
            FreeStyleProject job = createFreeStyleProject();
            job.setScm(new GitSCM(getRepositoryRoot()));
            job.getPublishersList().add(new RepositoryMinerStep());
            return job;
        }
        catch (IOException exception) {
            throw new AssertionError(exception);
        }
    }
}