package io.jenkins.plugins.forensics.git.miner;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import hudson.model.FreeStyleProject;
import hudson.model.Run;
import hudson.plugins.git.BranchSpec;
import hudson.plugins.git.GitSCM;

import io.jenkins.plugins.datatables.TableModel;
import io.jenkins.plugins.forensics.git.util.GitITest;
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
class GitMinerStepITest extends GitITest {
    private static final String GIT_FORENSICS_URL = "https://github.com/jenkinsci/git-forensics-plugin.git";
    private static final String FORENSICS_API_URL = "https://github.com/jenkinsci/forensics-api-plugin.git";
    private static final String FORENSICS_API_COMMIT = "a6d0ef09ab3c418e370449a884da99b8190ae950";
    private static final String GIT_FORENSICS_COMMIT = "86503e8bc0374e05e2cd32ed3bb8b4435d5fd757";
    private static final int EXPECTED_FILES_GIT_FORENSICS = 90;
    private static final int EXPECTED_FILES_FORENSICS_API = 121;

    /** Verifies that the table contains two rows with the correct statistics. */
    @Test
    void shouldFillTableDynamically() {
        buildJob();
    }

    private FreeStyleProject buildJob() {
        writeFileAsAuthorFoo("First");
        writeFileAsAuthorBar("Second");
        writeFileAsAuthorFoo("First");
        writeFileAsAuthorBar("Second");

        var job = createJobWithMiner();

        Run<?, ?> build = buildSuccessfully(job);

        var statistics = getStatistics(build);
        assertThat(statistics).hasFiles(INITIAL_FILE, ADDITIONAL_FILE);
        assertThat(statistics).hasLatestCommitId(getHead());

        verifyStatistics(statistics, INITIAL_FILE, 1, 1);
        verifyStatistics(statistics, ADDITIONAL_FILE, 2, 4);

        var forensics = getTableModel(build);
        assertThat(forensics.getRows()).hasSize(2);

        verifyInitialFile(forensics);
        verifyAdditionalFile(ADDITIONAL_FILE, forensics, 4);

        return job;
    }

    private void verifyInitialFile(final TableModel forensics) {
        var wrappedInitialFileName = "<a href=\"fileName." + INITIAL_FILE.hashCode() + "\" data-bs-toggle=\"tooltip\" data-bs-placement=\"left\" title=\"file\">" + INITIAL_FILE + "</a>";
        assertThat(getRow(forensics, 0))
                .hasFileName(wrappedInitialFileName)
                .hasAuthorsSize(1)
                .hasCommitsSize(1)
                .hasLinesOfCode(0)
                .hasChurn(0);
    }

    /** Verifies that the mining process is incremental and scans only new commits. */
    @Test
    void shouldMineRepositoryIncrementally() {
        writeFileAsAuthorFoo("1\n2\n3\n");
        var secondCommit = getHead();

        var job = createJobWithMiner();
        Run<?, ?> firstBuild = buildSuccessfully(job);

        var statistics = getStatistics(firstBuild);
        assertThat(statistics).hasFiles(INITIAL_FILE, ADDITIONAL_FILE);
        assertThat(statistics).hasLatestCommitId(getHead());

        assertThat(getConsoleLog(firstBuild)).contains(
                "2 commits with differences analyzed",
                "2 MODIFY commit diff items");
        verifyStatistics(statistics, ADDITIONAL_FILE, 1, 1);

        Run<?, ?> secondBuild = buildSuccessfully(job);

        assertThat(getConsoleLog(secondBuild)).contains(
                "No commits found since previous commit '%s'".formatted(secondCommit));
        verifyStatistics(getStatistics(secondBuild), ADDITIONAL_FILE, 1, 1);

        writeFileAsAuthorFoo("Third");

        Run<?, ?> thirdBuild = buildSuccessfully(job);

        assertThat(getConsoleLog(thirdBuild)).contains(
                "1 commits with differences analyzed",
                "1 MODIFY commit diff items");
        verifyStatistics(getStatistics(thirdBuild), ADDITIONAL_FILE, 1, 2);

        writeFileAsAuthorBar("Another content");
        Run<?, ?> build = buildSuccessfully(job);

        assertThat(getConsoleLog(thirdBuild)).contains("1 commits with differences analyzed", "1 MODIFY commit diff items");
        verifyStatistics(getStatistics(build), ADDITIONAL_FILE, 2, 3);
    }

    /** Verifies that the history of moved files will be preserved. */
    @Test
    void shouldPreserveHistoryOfMovedFiles() {
        var job = buildJob();

        var moved = "moved";
        git("mv", ADDITIONAL_FILE, moved);
        commit("Moved file");

        Run<?, ?> build = buildSuccessfully(job);

        var statistics = getStatistics(build);
        assertThat(statistics).hasFiles(INITIAL_FILE, moved);
        assertThat(statistics).hasLatestCommitId(getHead());

        assertThat(getConsoleLog(build)).contains("1 commits with differences analyzed", "1 RENAME commit diff items");
        verifyStatistics(statistics, moved, 2, 5);

        var forensics = getTableModel(build);
        assertThat(forensics.getRows()).hasSize(2);

        verifyInitialFile(forensics);
        verifyAdditionalFile(moved, forensics, 5);
    }

    private void verifyAdditionalFile(final String fileName, final TableModel forensics, final int commitsSize) {
        var wrappedFileName = "<a href=\"fileName." + fileName.hashCode() + "\" data-bs-toggle=\"tooltip\" data-bs-placement=\"left\" title=\""
                + fileName
                + "\">" + fileName + "</a>";
        assertThat(getRow(forensics, 1))
                .hasFileName(wrappedFileName)
                .hasAuthorsSize(2)
                .hasCommitsSize(commitsSize)
                .hasLinesOfCode(1)
                .hasChurn(7);
    }

    /** Verifies that deleted files are not shown anymore. */
    @Test
    void shouldNotShowDeletedFiles() {
        var job = buildJob();

        git("rm", ADDITIONAL_FILE);
        commit("Deleted file");

        Run<?, ?> build = buildSuccessfully(job);

        var statistics = getStatistics(build);
        assertThat(statistics).hasFiles(INITIAL_FILE);
        assertThat(statistics).hasLatestCommitId(getHead());

        assertThat(getConsoleLog(build)).contains("1 commits with differences analyzed", "1 DELETE commit diff items");

        var forensics = getTableModel(build);
        assertThat(forensics.getRows()).hasSize(1);

        verifyInitialFile(forensics);
    }

    /** Verifies the calculation of the #LOC and churn. */
    @Test
    void shouldCalculateLocAndChurn() {
        writeFileAsAuthorFoo("First\n");

        var job = createJobWithMiner();
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
    void shouldRunOnExistingProject() throws IOException {
        var job = createFreeStyleProject();
        var scm = createGitScm("https://github.com/jenkinsci/git-forensics-plugin.git",
                Collections.singletonList(new BranchSpec(GIT_FORENSICS_COMMIT)));
        job.setScm(scm);
        job.getPublishersList().add(new RepositoryMinerStep());

        Run<?, ?> build = buildSuccessfully(job);
        var statistics = getStatistics(build);
        assertThat(statistics.getFiles()).hasSize(EXPECTED_FILES_GIT_FORENSICS);
    }

    /** Run on existing project. */
    @Test
    void shouldRunInPipelineOnExistingProject() {
        var job = createPipeline();
        job.setDefinition(asStage(checkout(GIT_FORENSICS_COMMIT, GIT_FORENSICS_URL), "mineRepository()"));

        assertThat(buildSuccessfully(job).getActions(ForensicsBuildAction.class))
                .hasSize(1)
                .element(0).satisfies(this::verifyGitForensics);
    }

    /**
     * Creates a pipeline that checks out two different repositories and verifies that both repositories will be mined.
     */
    @Test
    void shouldMineMultipleRepositories() {
        var job = createPipeline();
        job.setDefinition(asStage(
                checkout(GIT_FORENSICS_COMMIT, GIT_FORENSICS_URL),
                checkout(FORENSICS_API_COMMIT, FORENSICS_API_URL),
                "mineRepository()"));

        List<ForensicsBuildAction> actions = buildSuccessfully(job).getActions(ForensicsBuildAction.class);

        assertThat(actions).hasSize(2);
        verifyGitForensics(actions.get(0));
        verifyForensicsApi(actions.get(1), "forensics-1");
    }

    /**
     * Creates a pipeline that checks out the same repository twice and verifies that the repository is mined only once.
     */
    @Test
    void shouldSkipDuplicateRepository() {
        var job = createPipeline();
        job.setDefinition(asStage(
                checkout(GIT_FORENSICS_COMMIT, GIT_FORENSICS_URL),
                checkout(GIT_FORENSICS_COMMIT, GIT_FORENSICS_URL),
                "mineRepository()"));

        Run<?, ?> build = buildSuccessfully(job);
        List<ForensicsBuildAction> actions = build.getActions(ForensicsBuildAction.class);

        assertThat(actions).hasSize(1);
        verifyGitForensics(actions.get(0));

        assertThat(getConsoleLog(build)).contains(
                "Skipping recording, since SCM 'git https://github.com/jenkinsci/git-forensics-plugin.git' already has been processed");
    }

    /**
     * Creates a pipeline that checks out two different repositories and verifies that both repositories will be mined.
     */
    @Test
    void shouldMineSelectedRepository() {
        var job = createPipeline();
        job.setDefinition(asStage(
                checkout(GIT_FORENSICS_COMMIT, GIT_FORENSICS_URL),
                checkout(FORENSICS_API_COMMIT, FORENSICS_API_URL),
                "mineRepository(scm: 'forensics-api')"));

        List<ForensicsBuildAction> forensicsActions = buildSuccessfully(job).getActions(ForensicsBuildAction.class);

        assertThat(forensicsActions).hasSize(1);
        verifyForensicsApi(forensicsActions.get(0), "forensics");

        job.setDefinition(asStage(
                checkout(GIT_FORENSICS_COMMIT, GIT_FORENSICS_URL),
                checkout(FORENSICS_API_COMMIT, FORENSICS_API_URL),
                "mineRepository(scm: 'git-forensics')"));

        List<ForensicsBuildAction> gitForensicsActions = buildSuccessfully(job).getActions(ForensicsBuildAction.class);

        assertThat(gitForensicsActions).hasSize(1);
        verifyGitForensics(gitForensicsActions.get(0));
    }

    private void verifyForensicsApi(final ForensicsBuildAction forensicsApi, final String expectedUrl) {
        assertThat(forensicsApi.getUrlName()).isEqualTo(expectedUrl);
        assertThat(forensicsApi.getNumberOfFiles()).isEqualTo(EXPECTED_FILES_FORENSICS_API);
        assertThat(forensicsApi.getScmKey()).contains("forensics-api");
        assertThat(forensicsApi.getResult()).hasLatestCommitId(FORENSICS_API_COMMIT);
    }

    private void verifyGitForensics(final ForensicsBuildAction gitForensics) {
        assertThat(gitForensics.getUrlName()).isEqualTo("forensics");
        assertThat(gitForensics.getNumberOfFiles()).isEqualTo(EXPECTED_FILES_GIT_FORENSICS);
        assertThat(gitForensics.getScmKey()).contains("git-forensics");
        assertThat(gitForensics.getResult()).hasLatestCommitId(GIT_FORENSICS_COMMIT);
    }

    private String checkout(final String commitId, final String repositoryUrl) {
        return "checkout([$class: 'GitSCM', "
                + "branches: [[name: '" + commitId + "' ]],\n"
                + "userRemoteConfigs: [[url: '" + repositoryUrl + "']],\n"
                + "extensions: [[$class: 'RelativeTargetDirectory', \n"
                + "            relativeTargetDir: '" + commitId + "']]])";
    }

    private void verifyStatistics(final RepositoryStatistics statistics, final String fileName,
            final int authorsSize, final int commitsSize) {
        var additionalFileStatistics = statistics.get(fileName);
        assertThat(additionalFileStatistics).hasFileName(fileName);
        assertThat(additionalFileStatistics).hasNumberOfAuthors(authorsSize);
        assertThat(additionalFileStatistics).hasNumberOfCommits(commitsSize);
    }

    private void verifyLocAndChurn(final Run<?, ?> build, final String fileName, final int churn,
            final int linesOfCode) {
        var statistics = getStatistics(build);
        var fileStatistics = statistics.get(fileName);

        assertThat(fileStatistics.getAbsoluteChurn()).isEqualTo(churn);
        assertThat(fileStatistics.getLinesOfCode()).isEqualTo(linesOfCode);
    }

    private RepositoryStatistics getStatistics(final Run<?, ?> build) {
        return getAction(build).getResult();
    }

    private TableModel getTableModel(final Run<?, ?> build) {
        var forensicsBuildAction = getAction(build);

        return ((ForensicsViewModel) forensicsBuildAction.getTarget()).getTableModel("forensics");
    }

    private ForensicsBuildAction getAction(final Run<?, ?> build) {
        return Objects.requireNonNull(build.getAction(ForensicsBuildAction.class),
                "Build does not contain a ForensicsBuildAction: " + build);
    }

    private ForensicsRow getRow(final TableModel forensics, final int rowIndex) {
        List<Object> rows = forensics.getRows();
        rows.sort(this::sort);
        var actual = rows.get(rowIndex);
        assertThat(actual).isInstanceOf(ForensicsRow.class);
        return (ForensicsRow) actual;
    }

    private <T> int sort(final Object left, final Object right) {
        return ((ForensicsRow) right).getFileName().compareTo(((ForensicsRow) left).getFileName());
    }

    private FreeStyleProject createJobWithMiner() {
        try {
            var job = createFreeStyleProject();
            job.setScm(new GitSCM(getRepositoryRoot()));
            job.getPublishersList().add(new RepositoryMinerStep());
            return job;
        }
        catch (IOException exception) {
            throw new AssertionError(exception);
        }
    }
}
