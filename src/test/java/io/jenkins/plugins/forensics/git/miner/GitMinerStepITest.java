package io.jenkins.plugins.forensics.git.miner;

import java.io.IOException;

import org.junit.Test;

import com.gargoylesoftware.htmlunit.html.HtmlPage;

import hudson.model.FreeStyleProject;
import hudson.model.Run;
import hudson.plugins.git.GitSCM;

import io.jenkins.plugins.forensics.git.miner.ForensicsRow.ForensicsColumn;
import io.jenkins.plugins.forensics.git.util.GitITest;
import io.jenkins.plugins.forensics.miner.RepositoryMinerStep;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for the {@link RepositoryMinerStep} using a Git repository.
 *
 * @author Ullrich Hafner
 */
public class GitMinerStepITest extends GitITest {
    /** Verifies that the table contains two rows with the correct statistics. */
    @Test
    public void shouldFillTableDynamically() throws IOException {
        writeFileAsAuthorFoo("First");
        writeFileAsAuthorBar("Second");
        writeFileAsAuthorFoo("First");
        writeFileAsAuthorBar("Second");

        FreeStyleProject job = createFreeStyleProject();
        job.setScm(new GitSCM(getRepositoryRoot()));
        job.getPublishersList().add(new RepositoryMinerStep());

        Run<?, ?> build = buildSuccessfully(job);

        HtmlPage forensicsPage = getWebPage(JavaScriptSupport.JS_ENABLED, build, "forensics");
        ForensicsTablePageObject table = new ForensicsTablePageObject(forensicsPage);

        assertThat(table.getRows()).hasSize(2);

        assertThat(table.getRow(0).getValuesByColumn()).contains(
                entry(ForensicsColumn.FILE, "file"),
                entry(ForensicsColumn.AUTHORS, "1"),
                entry(ForensicsColumn.COMMITS, "1"))
                .containsKey(ForensicsColumn.LAST_COMMIT)
                .containsKey(ForensicsColumn.ADDED);
        assertThat(table.getRow(1).getValuesByColumn())
                .contains(entry(ForensicsColumn.FILE, "source.txt"),
                        entry(ForensicsColumn.AUTHORS, "2"),
                        entry(ForensicsColumn.COMMITS, "4"))
                .containsKeys(ForensicsColumn.LAST_COMMIT, ForensicsColumn.ADDED);
    }
}
