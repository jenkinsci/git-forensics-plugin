package io.jenkins.plugins.forensics.git.miner;

import java.io.IOException;

import org.eclipse.jgit.lib.ObjectId;
import org.junit.Test;

import hudson.model.FreeStyleProject;
import hudson.model.Run;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.util.BuildData;

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
    /** Verifies that the table contains two rows with the correct statistics. */
    @Test
    public void shouldFillTableDynamically() {
        writeFileAsAuthorFoo("First");
        writeFileAsAuthorBar("Second");
        writeFileAsAuthorFoo("First");
        writeFileAsAuthorBar("Second");

        FreeStyleProject job = createJobWithMiner();

        Run<?, ?> build = buildSuccessfully(job);
        TableModel forensics = getTableModel(build);
        assertThat(forensics.getRows()).hasSize(2);

        assertThat(getRow(forensics, 0))
                .hasFileName("source.txt").hasAuthorsSize(2).hasCommitsSize(4);
        assertThat(getRow(forensics, 1))
                .hasFileName("file").hasAuthorsSize(1).hasCommitsSize(1);
    }

    /** Verifies that the mining process is incremental. */
    @Test
    public void shouldMineRepositoryIncrementally() throws IOException {
        writeFileAsAuthorFoo("First");

        FreeStyleProject job = createJobWithMiner();
        Run<?, ?> build = buildSuccessfully(job);

        getJenkins().assertLogContains("created report for 2 files", build);

        build = buildSuccessfully(job);

        getJenkins().assertLogContains("created report for 0 files", build);
    }

    /** Verifies that the latest revision id is saved in the build result. */
    @Test
    public void shouldSaveLatestRevisionId() {
        FreeStyleProject job = createJobWithMiner();
        Run<?, ?> build = buildSuccessfully(job);

        BuildData buildData = (BuildData)build.getAllActions().get(0);
        String latestId = buildData.lastBuild.revision.getSha1().getName();
        String savedId = build.getAction(ForensicsBuildAction.class).getResult().getLatestCommitId();

        assertThat(savedId).isEqualTo(latestId);
    }

    private TableModel getTableModel(final Run<?, ?> build) {
        ForensicsBuildAction forensicsBuildAction = build.getAction(ForensicsBuildAction.class);
        return ((ForensicsViewModel) forensicsBuildAction.getTarget()).getTableModel("forensics");
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
