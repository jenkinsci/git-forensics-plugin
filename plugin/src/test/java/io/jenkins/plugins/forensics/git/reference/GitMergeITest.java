package io.jenkins.plugins.forensics.git.reference;

import org.eclipse.jgit.lib.ObjectId;
import org.junit.jupiter.api.Test;

import edu.hm.hafner.util.FilteredLog;

import java.io.IOException;
import java.util.Collections;

import hudson.model.FreeStyleProject;
import hudson.model.Run;
import hudson.plugins.git.BranchSpec;
import hudson.plugins.git.GitSCM;

import io.jenkins.plugins.forensics.git.util.GitITest;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration test which verifies the recorded commits and found references in case of Git merges.
 *
 * @author Florian Orendi
 */
class GitMergeITest extends GitITest {
    private static final String EMPTY_COMMIT = ObjectId.zeroId().name();

    private static final String MAIN_JOB_NAME = "Main_Job";
    private static final String PR_JOB_NAME = "PR_Job";
    private static final String PR_BRANCH_NAME = "pull-request-branch";
    private static final String FEATURE_FILE = "feature_file";
    private static final String MAIN_FILE = "new_main_file";

    /**
     * Checks the case when the main branch is merged into the feature branch. {@code [M1]#3} is merged into
     * {@code [F1]#4} - see below.
     *
     * <pre>
     * {@code
     * M:  [M1]#1 --------------- [M1]#2 - [M1]#3 ----------
     *         \                                            \
     * F:       [F1]#1 - [F1]#2 ------------------ [F1]#3 - [F1]#4
     * }
     * </pre>
     */
    @Test
    void shouldHandleMergeFromMainIntoPullRequest() throws IOException {
        var mainJob = createFreeStyleProject(
                MAIN_JOB_NAME, MAIN_JOB_NAME, "origin/" + INITIAL_BRANCH);
        var pullRequestJob = createFreeStyleProject(
                PR_JOB_NAME, MAIN_JOB_NAME, "origin/" + PR_BRANCH_NAME);

        buildSuccessfully(mainJob);

        checkoutNewBranch(PR_BRANCH_NAME);
        writeFileWithNameAsAuthorFoo(FEATURE_FILE, "Commit 1 in PR");
        buildSuccessfully(pullRequestJob);
        writeFileWithNameAsAuthorFoo(FEATURE_FILE, "Commit 2 in PR");
        buildSuccessfully(pullRequestJob);

        checkout(INITIAL_BRANCH);
        writeFileWithNameAsAuthorFoo(MAIN_FILE, "Commit 1 in main");
        buildSuccessfully(mainJob);
        writeFileWithNameAsAuthorFoo(MAIN_FILE, "Commit 2 in main");
        Run<?, ?> latestMainBuild = buildSuccessfully(mainJob);
        var latestMainRecord = latestMainBuild.getAction(GitCommitsRecord.class);
        assertThat(latestMainRecord)
                .isNotNull()
                .satisfies(commit -> {
                    assertThat(commit.getTargetParentCommit()).isEqualTo(ObjectId.zeroId().name());
                    assertThat(commit.getLatestCommit()).isEqualTo(getHead());
                });

        checkout(PR_BRANCH_NAME);
        writeFileWithNameAsAuthorFoo("anotherFile", "Commit 3 in PR");
        Run<?, ?> latestPullRequestBuild = buildSuccessfully(pullRequestJob);
        var latestPullRequestRecord = latestPullRequestBuild.getAction(GitCommitsRecord.class);
        assertThat(latestPullRequestRecord)
                .isNotNull()
                .satisfies(commit -> {
                    assertThat(commit.getTargetParentCommit()).isEqualTo(ObjectId.zeroId().name());
                    assertThat(commit.getLatestCommit()).isEqualTo(getHead());
                });

        // merge main into PR and create a merge commit
        mergeWithoutFastForwarding(INITIAL_BRANCH);
        Run<?, ?> mergeBuild = buildSuccessfully(pullRequestJob);

        verifyRecordedCommits(mergeBuild, latestMainRecord.getLatestCommit(), getHead());
        verifyReference(mainJob, mergeBuild, latestMainBuild);
    }

    /**
     * Checks the case when the feature branch is merged into the main branch. {@code [F1]#2} is merged into
     * {@code [M1]#3} - see below.
     *
     * <pre>
     * {@code
     * M:  [M1]#1 --------------- [M1]#2 - [M1]#3
     *         \                            /
     * F:       [F1]#1 - [F1]#2 ------------
     * }
     * </pre>
     */
    @Test
    void shouldHandleMergeFromPullRequestIntoMain() throws IOException {
        var mainJob = createFreeStyleProject(
                MAIN_JOB_NAME, MAIN_JOB_NAME, "origin/" + INITIAL_BRANCH);
        var pullRequestJob = createFreeStyleProject(
                PR_JOB_NAME, MAIN_JOB_NAME, "origin/" + PR_BRANCH_NAME);

        checkout(INITIAL_BRANCH);
        writeFileWithNameAsAuthorFoo(MAIN_FILE, "Commit 1 in main");
        buildSuccessfully(mainJob);

        checkoutNewBranch(PR_BRANCH_NAME);
        writeFileWithNameAsAuthorFoo(FEATURE_FILE, "Commit 1 in PR");
        buildSuccessfully(pullRequestJob);
        writeFileWithNameAsAuthorFoo("additionalFile", "Commit 2 in PR");
        Run<?, ?> latestPullRequestBuild = buildSuccessfully(pullRequestJob);
        var latestPullRequestRecord = latestPullRequestBuild.getAction(GitCommitsRecord.class);

        checkout(INITIAL_BRANCH);
        writeFileWithNameAsAuthorFoo(MAIN_FILE, "Commit 2 in main");
        Run<?, ?> latestMainBuild = buildSuccessfully(mainJob);
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
        // verify reference
        var recorder = createGitReferenceRecorder(mainJob, INITIAL_BRANCH);
        assertThat(recorder.find(mergeBuild, mainJob.getLastCompletedBuild(), createLog()))
                .isNotEmpty()
                .satisfies(reference -> assertThat(
                        reference.get().getExternalizableId()).isEqualTo(latestMainBuild.getExternalizableId()));

        verifyRecordedCommits(mergeBuild, latestPullRequestRecord.getLatestCommit(), getHead());
        verifyReference(mainJob, mergeBuild, latestMainBuild);
    }

    /**
     * Checks the case when the feature branch is merged into the main branch without creating an additional merge
     * commit. {@code [F1]#2} is merged into {@code [M1]#2} - see below.
     *
     * <pre>
     * {@code
     * M:  [M1]#1 ------------- [M1]#2
     *         \               /
     * F:       [F1]#1 - [F1]#2
     * }
     * </pre>
     */
    @Test
    void shouldHandleFastForwardFromPullRequestIntoMain() throws IOException {
        var mainJob = createFreeStyleProject(
                MAIN_JOB_NAME, MAIN_JOB_NAME, "origin/" + INITIAL_BRANCH);
        var pullRequestJob = createFreeStyleProject(
                PR_JOB_NAME, MAIN_JOB_NAME, "origin/" + PR_BRANCH_NAME);

        checkout(INITIAL_BRANCH);
        writeFileWithNameAsAuthorFoo(MAIN_FILE, "Commit 1 in main");
        Run<?, ?> latestMainBuild = buildSuccessfully(mainJob);

        checkoutNewBranch(PR_BRANCH_NAME);
        writeFileWithNameAsAuthorFoo(FEATURE_FILE, "Commit 1 in PR");
        buildSuccessfully(pullRequestJob);
        writeFileWithNameAsAuthorFoo("anotherFile", "Commit 2 in PR");
        Run<?, ?> latestPullRequestBuild = buildSuccessfully(pullRequestJob);
        var latestPullRequestRecord = latestPullRequestBuild.getAction(GitCommitsRecord.class);

        checkout(INITIAL_BRANCH);
        // merge PR into main without creating a merge commit
        mergeWithFastForwarding(PR_BRANCH_NAME);
        Run<?, ?> mergeBuild = buildSuccessfully(mainJob);

        verifyRecordedCommits(mergeBuild, EMPTY_COMMIT, latestPullRequestRecord.getLatestCommit());
        verifyReference(mainJob, mergeBuild, latestMainBuild);
    }

    /**
     * Checks the case when two feature branch commits are built successively. The build with the latest main commit
     * before creating the feature branch {@code [M1]#1} should be the reference, not {@code [F1]#1}.
     *
     * <pre>
     * {@code
     * M:  [M1]#1
     *         \
     * F:       [F1]#1 - [F1]#2
     * }
     * </pre>
     */
    @Test
    void shouldHandleDescendantCommit() throws IOException {
        var mainJob = createFreeStyleProject(
                MAIN_JOB_NAME, MAIN_JOB_NAME, "origin/" + INITIAL_BRANCH);
        var pullRequestJob = createFreeStyleProject(
                PR_JOB_NAME, MAIN_JOB_NAME, "origin/" + PR_BRANCH_NAME);

        checkout(INITIAL_BRANCH);
        writeFileWithNameAsAuthorFoo(MAIN_FILE, "Commit 1 in Main");
        Run<?, ?> latestMainBuild = buildSuccessfully(mainJob);

        checkoutNewBranch(PR_BRANCH_NAME);
        writeFileWithNameAsAuthorFoo(FEATURE_FILE, "Commit 1 in PR");
        buildSuccessfully(pullRequestJob);
        writeFileWithNameAsAuthorFoo(FEATURE_FILE, "Commit 2 in PR");
        Run<?, ?> latestPullRequestBuild = buildSuccessfully(pullRequestJob);

        verifyRecordedCommits(latestPullRequestBuild, EMPTY_COMMIT, getHead());
        verifyReference(mainJob, latestPullRequestBuild, latestMainBuild);
    }

    /**
     * Verifies the found commit records.
     *
     * @param build
     *         The currently investigated build
     * @param targetParentCommit
     *         The expected target parent commit
     * @param latestCommit
     *         The expected latest found commit
     */
    private void verifyRecordedCommits(final Run<?, ?> build, final String targetParentCommit,
            final String latestCommit) {
        assertThat(build.getAction(GitCommitsRecord.class))
                .isNotNull()
                .satisfies(commit -> {
                    assertThat(commit.getTargetParentCommit()).isEqualTo(targetParentCommit);
                    assertThat(commit.getLatestCommit()).isEqualTo(latestCommit);
                });
    }

    /**
     * Verifies the found reference build.
     *
     * @param targetJob
     *         The target job
     * @param currentBuild
     *         The currently investigated build
     * @param referenceBuild
     *         The potential reference build
     */
    private void verifyReference(final FreeStyleProject targetJob,
            final Run<?, ?> currentBuild, final Run<?, ?> referenceBuild) {
        var recorder = createGitReferenceRecorder(targetJob, INITIAL_BRANCH);
        assertThat(recorder.find(currentBuild, targetJob.getLastCompletedBuild(), createLog()))
                .isNotEmpty()
                .satisfies(reference -> assertThat(
                        reference.get().getExternalizableId()).isEqualTo(referenceBuild.getExternalizableId()));
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
        var project = createProject(FreeStyleProject.class, jobName);

        var recorder = new GitReferenceRecorder();
        recorder.setReferenceJob(referenceJobName);
        recorder.setTargetBranch("origin/" + INITIAL_BRANCH);
        project.getPublishersList().add(recorder);

        var scm = createGitScm(getGitRepositoryPath(), Collections.singletonList(new BranchSpec(branchSpec)));
        project.setScm(scm);

        return project;
    }

    /**
     * Creates a {@link GitReferenceRecorder}.
     *
     * @param reference
     *         The reference job
     * @param targetBranch
     *         The target branch
     *
     * @return the created recorder
     */
    private GitReferenceRecorder createGitReferenceRecorder(final FreeStyleProject reference,
            final String targetBranch) {
        var recorder = new GitReferenceRecorder();
        recorder.setTargetBranch(targetBranch);
        recorder.setMaxCommits(50);
        recorder.setReferenceJob(reference.getName());
        return recorder;
    }

    /**
     * Creates a {@link FilteredLog}.
     *
     * @return the created logger
     */
    private FilteredLog createLog() {
        return new FilteredLog("Merge commits log:");
    }
}
