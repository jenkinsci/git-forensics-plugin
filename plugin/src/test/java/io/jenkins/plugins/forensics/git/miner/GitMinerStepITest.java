package io.jenkins.plugins.forensics.git.miner;

import java.io.IOException;

import org.junit.Test;

import com.gargoylesoftware.htmlunit.html.HtmlPage;

import hudson.model.FreeStyleProject;
import hudson.model.Run;
import hudson.plugins.git.GitSCM;

import io.jenkins.plugins.datatables.TablePageObject;
import io.jenkins.plugins.forensics.git.util.GitITest;
import io.jenkins.plugins.forensics.miner.RepositoryMinerStep;

import static org.assertj.core.api.Assertions.*;

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

        HtmlPage forensicsPage = getWebPage(JavaScriptSupport.JS_ENABLED, build, "forensics");

        TablePageObject table = new TablePageObject(forensicsPage, "forensics");
        assertThat(table.getRows()).hasSize(2);

        assertThat(table.getRow(0).getValuesByColumnLabel())
                .contains(entry(FILE, "file"), entry(AUTHORS, "1"), entry(COMMITS, "1"))
                .containsKeys(LAST_COMMIT, ADDED); // value depends on the runtime and cannot be verified
        assertThat(table.getRow(1).getValuesByColumnLabel())
                .contains(entry(FILE, "source.txt"), entry(AUTHORS, "2"), entry(COMMITS, "4"))
                .containsKeys(LAST_COMMIT, ADDED); // value depends on the runtime and cannot be verified
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
