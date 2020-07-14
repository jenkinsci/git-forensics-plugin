package io.jenkins.plugins.forensics.git.miner;

import java.io.IOException;

import org.junit.Test;

import hudson.model.FreeStyleProject;
import hudson.model.Run;
import hudson.plugins.git.GitSCM;

import io.jenkins.plugins.datatables.TableModel;
import io.jenkins.plugins.forensics.git.util.GitITest;
import io.jenkins.plugins.forensics.miner.ForensicsBuildAction;
import io.jenkins.plugins.forensics.miner.ForensicsTableModel.ForensicsRow;
import io.jenkins.plugins.forensics.miner.ForensicsViewModel;
import io.jenkins.plugins.forensics.miner.RepositoryMinerStep;

import static io.jenkins.plugins.forensics.assertions.Assertions.*;

/**
 * Integration tests for the {@link RepositoryMinerStep} using a Git repository.
 *
 * @author Ullrich Hafner
 */
public class GitMinerStepITest extends GitITest {
    private static final String FILE = "File";
    private static final String AUTHORS = "#Authors";
    private static final String COMMITS = "#Commits";
    private static final String LAST_COMMIT = "Last Commit";
    private static final String ADDED = "Added";

    /** Verifies that the table contains two rows with the correct statistics. */
    @Test
    public void shouldFillTableDynamically() {
        writeFileAsAuthorFoo("First");
        writeFileAsAuthorBar("Second");
        writeFileAsAuthorFoo("First");
        writeFileAsAuthorBar("Second");

        FreeStyleProject job = createJobWithMiner();

        Run<?, ?> build = buildSuccessfully(job);
        ForensicsBuildAction action = build.getAction(ForensicsBuildAction.class);
        TableModel forensics = ((ForensicsViewModel) action.getTarget()).getTableModel("forensics");
        assertThat(forensics.getRows()).hasSize(2);

        assertThat(getRow(forensics, 0))
                .hasFileName("source.txt").hasAuthorsSize(2).hasCommitsSize(4);
        assertThat(getRow(forensics, 1))
                .hasFileName("file").hasAuthorsSize(1).hasCommitsSize(1);
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
