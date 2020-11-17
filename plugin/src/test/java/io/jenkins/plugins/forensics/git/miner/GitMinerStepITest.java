package io.jenkins.plugins.forensics.git.miner;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.junit.Test;

import hudson.model.FreeStyleProject;
import hudson.model.Run;
import hudson.plugins.git.BranchSpec;
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
        buildJob();
    }

    private FreeStyleProject buildJob() {
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

        verifyInitialFile(forensics);
        verifyAdditionalFile(ADDITIONAL_FILE, forensics, 4);

        return job;
    }

    private void verifyInitialFile(final TableModel forensics) {
        String wrappedInitialFileName = "<a href=\"fileName." + INITIAL_FILE.hashCode() + "\">" + INITIAL_FILE + "</a>";
        assertThat(getRow(forensics, 0))
                .hasFileName(wrappedInitialFileName)
                .hasAuthorsSize(1)
                .hasCommitsSize(1)
                .hasLinesOfCode(0)
                .hasChurn(0);
    }

    /** Verifies that the mining process is incremental and scans only new commits. */
    @Test
    public void shouldMineRepositoryIncrementally() {
        String firstCommit = getHead();
        writeFileAsAuthorFoo("1\n2\n3\n");
        String secondCommit = getHead();

        FreeStyleProject job = createJobWithMiner();
        Run<?, ?> firstBuild = buildSuccessfully(job);

        RepositoryStatistics statistics = getStatistics(firstBuild);
        assertThat(statistics).hasFiles(INITIAL_FILE, ADDITIONAL_FILE);
        assertThat(statistics).hasLatestCommitId(getHead());

        assertThat(getConsoleLog(firstBuild)).contains(
                "2 commits analyzed",
                "2 MODIFY commits",
                String.format("Analyzed commit '%s'", firstCommit),
                String.format("Analyzed commit '%s'", secondCommit)
        );
        verifyStatistics(statistics, ADDITIONAL_FILE, 1, 1);

        Run<?, ?> secondBuild = buildSuccessfully(job);

        assertThat(getConsoleLog(secondBuild)).contains(
                "0 commits analyzed",
                String.format("No commits found since previous commit '%s'", secondCommit));
        verifyStatistics(getStatistics(secondBuild), ADDITIONAL_FILE, 1, 1);

        writeFileAsAuthorFoo("Third");
        String thirdCommit = getHead();

        Run<?, ?> thirdBuild = buildSuccessfully(job);

        assertThat(getConsoleLog(thirdBuild)).contains(
                "1 commits analyzed",
                "1 MODIFY commits",
                String.format("Analyzed commit '%s'", thirdCommit));
        verifyStatistics(getStatistics(thirdBuild), ADDITIONAL_FILE, 1, 2);

        writeFileAsAuthorBar("Another content");
        Run<?, ?> build = buildSuccessfully(job);

        assertThat(getConsoleLog(thirdBuild)).contains(
                "1 commits analyzed",
                "1 MODIFY commits",
                String.format("Analyzed commit '%s'", thirdCommit));
        verifyStatistics(getStatistics(build), ADDITIONAL_FILE, 2, 3);
    }

    /** Verifies that the history of moved files will be preserved. */
    @Test
    public void shouldPreserveHistoryOfMovedFiles() {
        FreeStyleProject job = buildJob();

        String moved = "moved";
        git("mv", ADDITIONAL_FILE, moved);
        commit("Moved file");

        Run<?, ?> build = buildSuccessfully(job);

        RepositoryStatistics statistics = getStatistics(build);
        assertThat(statistics).hasFiles(INITIAL_FILE, moved);
        assertThat(statistics).hasLatestCommitId(getHead());

        assertThat(getConsoleLog(build)).contains(
                "1 commits analyzed",
                "1 RENAME commits",
                String.format("Analyzed commit '%s'", getHead())
        );
        verifyStatistics(statistics, moved, 2, 5);

        TableModel forensics = getTableModel(build);
        assertThat(forensics.getRows()).hasSize(2);

        verifyInitialFile(forensics);
        verifyAdditionalFile(moved, forensics, 5);
    }

    private void verifyAdditionalFile(final String fileName, final TableModel forensics, final int commitsSize) {
        String wrappedFileName = "<a href=\"fileName." + fileName.hashCode() + "\">" + fileName + "</a>";
        assertThat(getRow(forensics, 1))
                .hasFileName(wrappedFileName)
                .hasAuthorsSize(2)
                .hasCommitsSize(commitsSize)
                .hasLinesOfCode(1)
                .hasChurn(7);
    }

    /** Verifies that deleted files are not shown anymore. */
    @Test
    public void shouldNotShowDeletedFiles() {
        FreeStyleProject job = buildJob();

        git("rm", ADDITIONAL_FILE);
        commit("Deleted file");

        Run<?, ?> build = buildSuccessfully(job);

        RepositoryStatistics statistics = getStatistics(build);
        assertThat(statistics).hasFiles(INITIAL_FILE);
        assertThat(statistics).hasLatestCommitId(getHead());

        assertThat(getConsoleLog(build)).contains(
                "1 commits analyzed",
                "1 DELETE commits",
                String.format("Analyzed commit '%s'", getHead())
        );

        TableModel forensics = getTableModel(build);
        assertThat(forensics.getRows()).hasSize(1);

        verifyInitialFile(forensics);
    }

    /** Verifies the calculation of the #LOC and churn. */
    @Test
    public void shouldCalculateLocAndChurn() {
        writeFileAsAuthorFoo("First\n");

        FreeStyleProject job = createJobWithMiner();
        Run<?, ?> build = buildSuccessfully(job);

        verifyLocAndChurn(build, ADDITIONAL_FILE, 1, 1);

        build = buildSuccessfully(job);

        verifyLocAndChurn(build, ADDITIONAL_FILE, 1, 1);
        writeFileAsAuthorFoo("Second\n");

        build = buildSuccessfully(job);
        verifyLocAndChurn(build, ADDITIONAL_FILE, 3, 1);
    }

    /** Run on existing project. */
    @Test
    public void shouldRunOnExistingProject() throws IOException {
        FreeStyleProject job = createFreeStyleProject();
        GitSCM scm = new GitSCM(GitSCM.createRepoList("https://github.com/jenkinsci/git-forensics-plugin.git", null),
                Collections.singletonList(new BranchSpec("28af63def44286729e3b19b03464d100fd1d0587")),
                false, Collections.emptyList(), null, null, Collections.emptyList());
        job.setScm(scm);
        job.getPublishersList().add(new RepositoryMinerStep());

        Run<?, ?> build = buildSuccessfully(job);
        RepositoryStatistics statistics = getStatistics(build);
        assertThat(statistics.getFiles()).hasSize(51);
    }

    private void verifyStatistics(final RepositoryStatistics statistics, final String fileName,
            final int authorsSize, final int commitsSize) {
        FileStatistics additionalFileStatistics = statistics.get(fileName);
        assertThat(additionalFileStatistics).hasFileName(fileName);
        assertThat(additionalFileStatistics).hasNumberOfAuthors(authorsSize);
        assertThat(additionalFileStatistics).hasNumberOfCommits(commitsSize);
    }

    private void verifyLocAndChurn(final Run<?, ?> build, final String fileName, final int churn,
            final int linesOfCode) {
        RepositoryStatistics statistics = getStatistics(build);
        FileStatistics fileStatistics = statistics.get(fileName);

        assertThat(fileStatistics.getAbsoluteChurn()).isEqualTo(churn);
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
        List<Object> rows = forensics.getRows();
        rows.sort(this::sort);
        Object actual = rows.get(rowIndex);
        assertThat(actual).isInstanceOf(ForensicsRow.class);
        return (ForensicsRow) actual;
    }

    private <T> int sort(final Object left, final Object right) {
        return ((ForensicsRow)right).getFileName().compareTo(((ForensicsRow)left).getFileName());
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
