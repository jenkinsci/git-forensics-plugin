package io.jenkins.plugins.forensics.git.reference;

import java.io.IOException;
import java.util.Collections;
import java.util.UUID;

import org.eclipse.jgit.lib.ObjectId;
import org.junit.jupiter.api.Test;

import hudson.model.FreeStyleProject;
import hudson.model.Run;
import hudson.plugins.git.BranchSpec;
import hudson.plugins.git.GitSCM;

import io.jenkins.plugins.forensics.git.util.GitITest;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration test which verifies the {@link GitCheckoutListener} in case of merges.
 *
 * @author Florian Orendi
 */
class GitCheckoutListenerMergeITest extends GitITest {

    private static final String MAIN_JOB_NAME = "Main_Job";
    private static final String PR_JOB_NAME = "PR_Job";
    private static final String PR_BRANCH_NAME = "pull-request-branch";
    private static final String FEATURE_FILE = "feature_file";
    private static final String MAIN_FILE = "new_main_file";

    @Test
    void shouldHandleMergeFromMainIntoPullRequest() throws IOException {
        FreeStyleProject mainJob = createFreeStyleProject(
                MAIN_JOB_NAME, MAIN_JOB_NAME, "origin/" + INITIAL_BRANCH);
        FreeStyleProject pullRequestJob = createFreeStyleProject(
                PR_JOB_NAME, MAIN_JOB_NAME, "origin/" + PR_BRANCH_NAME);

        buildSuccessfully(mainJob);

        checkoutNewBranch(PR_BRANCH_NAME);
        commitFile(FEATURE_FILE, "Commit 1 in PR");
        buildSuccessfully(pullRequestJob);
        commitFile(FEATURE_FILE, "Commit 2 in PR");
        buildSuccessfully(pullRequestJob);

        checkout(INITIAL_BRANCH);
        commitFile(MAIN_FILE, "Commit 1 in main");
        buildSuccessfully(mainJob);
        commitFile(MAIN_FILE, "Commit 2 in main");
        Run<?, ?> latestMainBuild = buildSuccessfully(mainJob);
        GitCommitsRecord latestMainRecord = latestMainBuild.getAction(GitCommitsRecord.class);
        assertThat(latestMainRecord)
                .isNotNull()
                .satisfies(commit -> {
                    assertThat(commit.getTargetParentCommit()).isEqualTo(ObjectId.zeroId().name());
                    assertThat(commit.getLatestCommit()).isEqualTo(getHead());
                });

        checkout(PR_BRANCH_NAME);
        commitFile("anotherFile", "Commit 3 in PR");
        Run<?, ?> latestPullRequestBuild = buildSuccessfully(pullRequestJob);
        GitCommitsRecord latestPullRequestRecord = latestPullRequestBuild.getAction(GitCommitsRecord.class);
        assertThat(latestPullRequestRecord)
                .isNotNull()
                .satisfies(commit -> {
                    assertThat(commit.getTargetParentCommit()).isEqualTo(ObjectId.zeroId().name());
                    assertThat(commit.getLatestCommit()).isEqualTo(getHead());
                });

        // merge main into PR and create a merge commit
        mergeWithoutFastForwarding(INITIAL_BRANCH);
        // verify commits
        Run<?, ?> mergeBuild = buildSuccessfully(pullRequestJob);
        assertThat(mergeBuild.getAction(GitCommitsRecord.class))
                .isNotNull()
                .satisfies(commit -> {
                    assertThat(commit.getLatestCommit()).isEqualTo(getHead());
                    assertThat(commit.getTargetParentCommit()).isEqualTo(latestMainRecord.getLatestCommit());
                });
    }

    @Test
    void shouldHandleMergeFromPullRequestIntoMain() throws IOException {
        FreeStyleProject mainJob = createFreeStyleProject(
                MAIN_JOB_NAME, MAIN_JOB_NAME, "origin/" + INITIAL_BRANCH);
        FreeStyleProject pullRequestJob = createFreeStyleProject(
                PR_JOB_NAME, MAIN_JOB_NAME, "origin/" + PR_BRANCH_NAME);

        checkout(INITIAL_BRANCH);
        commitFile(MAIN_FILE, "Commit 1 in main");
        buildSuccessfully(mainJob);

        checkoutNewBranch(PR_BRANCH_NAME);
        commitFile(FEATURE_FILE, "Commit 1 in PR");
        buildSuccessfully(pullRequestJob);
        commitFile(MAIN_FILE, "Commit 2 in PR");
        Run<?, ?> latestPullRequestBuild = buildSuccessfully(pullRequestJob);
        GitCommitsRecord latestPullRequestRecord = latestPullRequestBuild.getAction(GitCommitsRecord.class);

        checkout(INITIAL_BRANCH);
        commitFile("additionalFile", "Commit 2 in main");
        buildSuccessfully(mainJob);
        // merge PR into main and create a merge commit
        mergeWithoutFastForwarding(PR_BRANCH_NAME);
        // verify commits
        Run<?, ?> mergeBuild = buildSuccessfully(mainJob);
        assertThat(mergeBuild.getAction(GitCommitsRecord.class))
                .isNotNull()
                .satisfies(commit -> {
                    assertThat(commit.getLatestCommit()).isEqualTo(getHead());
                    assertThat(commit.getTargetParentCommit()).isEqualTo(latestPullRequestRecord.getLatestCommit());
                });

    }

    @Test
    void shouldHandleFastForwardFromPullRequestIntoMain() throws IOException {
        FreeStyleProject mainJob = createFreeStyleProject(
                MAIN_JOB_NAME, MAIN_JOB_NAME, "origin/" + INITIAL_BRANCH);
        FreeStyleProject pullRequestJob = createFreeStyleProject(
                PR_JOB_NAME, MAIN_JOB_NAME, "origin/" + PR_BRANCH_NAME);

        checkout(INITIAL_BRANCH);
        commitFile(MAIN_FILE, "Commit 1 in main");
        buildSuccessfully(mainJob);

        checkoutNewBranch(PR_BRANCH_NAME);
        commitFile(FEATURE_FILE, "Commit 1 in PR");
        buildSuccessfully(pullRequestJob);
        commitFile("anotherFile", "Commit 2 in PR");
        Run<?, ?> latestPullRequestBuild = buildSuccessfully(pullRequestJob);
        GitCommitsRecord latestPullRequestRecord = latestPullRequestBuild.getAction(GitCommitsRecord.class);

        checkout(INITIAL_BRANCH);
        // merge PR into main without creating a merge commit
        mergeWithFastForwarding(PR_BRANCH_NAME);
        // verify commits
        Run<?, ?> mergeBuild = buildSuccessfully(mainJob);
        assertThat(mergeBuild.getAction(GitCommitsRecord.class))
                .isNotNull()
                .satisfies(commit -> {
                    assertThat(commit.getTargetParentCommit()).isEqualTo(ObjectId.zeroId().name());
                    assertThat(commit.getLatestCommit()).isEqualTo(latestPullRequestRecord.getLatestCommit());
                });

    }

    @Test
    void shouldHandleDescendantCommit() throws IOException {
        FreeStyleProject pullRequestJob = createFreeStyleProject(
                PR_JOB_NAME, MAIN_JOB_NAME, "origin/" + PR_BRANCH_NAME);

        checkoutNewBranch(PR_BRANCH_NAME);
        commitFile(FEATURE_FILE, "Commit 1 in PR");
        buildSuccessfully(pullRequestJob);
        commitFile(FEATURE_FILE, "Commit 2 in PR");
        Run<?, ?> latestPullRequestBuild = buildSuccessfully(pullRequestJob);
        // verify commits
        assertThat(latestPullRequestBuild.getAction(GitCommitsRecord.class))
                .isNotNull()
                .satisfies(commit -> {
                    assertThat(commit.getTargetParentCommit()).isEqualTo(ObjectId.zeroId().name());
                    assertThat(commit.getLatestCommit()).isEqualTo(getHead());
                });

    }

    /**
     * Creates a new Jenkins {@link FreeStyleProject freestyle project}.
     *
     * @param jobName
     *         The name of the project
     * @param referenceJobName
     *         The name of the reference job
     * @param branchSpec
     *         The branch used by the {@link GitSCM}
     *
     * @return the created project
     * @throws IOException
     *         if creating failed
     */
    private FreeStyleProject createFreeStyleProject(final String jobName, final String referenceJobName,
            final String branchSpec) throws IOException {
        FreeStyleProject project = createProject(FreeStyleProject.class, jobName);

        GitReferenceRecorder recorder = new GitReferenceRecorder();
        recorder.setReferenceJob(referenceJobName);
        recorder.setTargetBranch("origin/" + INITIAL_BRANCH);
        project.getPublishersList().add(recorder);

        GitSCM scm = createGitScm(getGitRepositoryPath(), Collections.singletonList(new BranchSpec(branchSpec)));
        project.setScm(scm);

        return project;
    }

    /**
     * Commits a file with a specific name and message.
     *
     * @param fileName
     *         The file name
     * @param message
     *         The message
     */
    protected void commitFile(final String fileName, final String message) {
        writeFile(fileName, UUID.randomUUID().toString());
        git("add", fileName);
        git("config", "user.name", FOO_NAME);
        git("config", "user.email", FOO_EMAIL);
        git("commit", String.format("--message=%s", message));
    }

    /**
     * Merges the passed branch into the current branch without fast-forwarding. This creates an additional merge
     * commit.
     *
     * @param branch
     *         The branch to be merged
     */
    protected void mergeWithoutFastForwarding(final String branch) {
        git("merge", "--no-ff", branch);
    }

    /**
     * Merges the passed branch into the current branch using fast-forwarding.
     *
     * @param branch
     *         The branch to be merged
     */
    protected void mergeWithFastForwarding(final String branch) {
        git("merge", "--ff", branch);
    }
}
