package io.jenkins.plugins.forensics.git.reference;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;

import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Test;
import org.jvnet.hudson.test.BuildWatcher;

import com.cloudbees.hudson.plugins.folder.computed.FolderComputation;

import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject;
import hudson.model.Run;
import hudson.model.queue.QueueTaskFuture;
import jenkins.branch.BranchProperty;
import jenkins.branch.BranchSource;
import jenkins.branch.DefaultBranchPropertyStrategy;
import jenkins.plugins.git.GitSCMSource;
import jenkins.scm.api.SCMSource;

import io.jenkins.plugins.forensics.git.util.GitITest;

import static io.jenkins.plugins.forensics.git.assertions.Assertions.*;

/**
 * Integration tests for finding the correct reference point for multibranch pipelines.
 *
 * @author Arne Schöntag
 * @see <a href="https://github.com/jenkinsci/workflow-multibranch-plugin/blob/master/src/test/java/org/jenkinsci/plugins/workflow/multibranch/WorkflowMultiBranchProjectTest.java">Most
 *         tests are based on the integration tests for the multi-branch pipeleine plugin</a>
 */
@SuppressWarnings("PMD.SignatureDeclareThrowsException")
public class ReferenceRecorderMultibranchITest extends GitITest {
    private static final String JENKINS_FILE = "Jenkinsfile";
    private static final String SOURCE_FILE = "file";
    private static final String FEATURE = "feature";
    private static final String MASTER = "master";
    private static final String ADDITIONAL_SOURCE_FILE = "test.txt";

    @ClassRule
    public static BuildWatcher buildWatcher = new BuildWatcher();

    /**
     * Builds a  multibranch-pipeline with a master and a feature branch, builds them and checks if the correct
     * reference build is found.
     *
     * @throws Exception
     *         in case of an unexpected error during the tests
     */
    @Test
    public void shouldFindCorrectBuildForMultibranchPipeline() throws Exception {
        initializeRepository();

        WorkflowMultiBranchProject project = createMultiBranchProject();
        WorkflowRun masterBuild = buildMaster(project, 1);
        verifyRecordSize(masterBuild, 2);

        createFeatureBranchAndAddCommits();

        WorkflowRun featureBuild = buildFeature(project, 1);
        verifyRecordSize(featureBuild, 3);

        GitBranchMasterIntersectionFinder finder = featureBuild.getAction(GitBranchMasterIntersectionFinder.class);
        assertThat(finder).isNotNull()
                .hasOwner(featureBuild)
                .hasBuildId(masterBuild.getExternalizableId())
                .hasReferenceBuild(Optional.of(masterBuild));
    }

    /**
     * Checks if the intersection point is found if the master is one commit and build ahead of the feature branch.
     *
     * @throws Exception
     *         in case of an unexpected error during the tests
     */
    @Test
    public void shouldHandleExtraCommitsAfterBranchPointOnMaster() throws Exception {
        initializeRepository();

        WorkflowMultiBranchProject project = createMultiBranchProject();
        WorkflowRun masterBuild = buildMaster(project, 1);
        verifyRecordSize(masterBuild, 2);

        createFeatureBranchAndAddCommits();

        // Add some new master commits
        sampleRepo.git("checkout", MASTER);
        sampleRepo.write(ADDITIONAL_SOURCE_FILE, "test");
        sampleRepo.git("add", ADDITIONAL_SOURCE_FILE);
        sampleRepo.git("commit", "--all", "--message=test");

        WorkflowRun nextMaster = buildMaster(project, 2);
        verifyRecordSize(nextMaster, 1);

        WorkflowRun featureBuild = buildFeature(project, 1);

        GitCommitsRecord featureRecord = featureBuild.getAction(GitCommitsRecord.class);
        assertThat(featureRecord).isNotNull();
        assertThat(featureRecord.getCommits()).hasSize(3);

        GitBranchMasterIntersectionFinder finder = featureBuild.getAction(GitBranchMasterIntersectionFinder.class);
        assertThat(finder).isNotNull()
                .hasOwner(featureBuild)
                .hasBuildId(masterBuild.getExternalizableId())
                .hasReferenceBuild(Optional.of(masterBuild));
    }

    /**
     * Checks if the correct build is found even if the reference build has commits that the feature build does not.
     * This also checks the algorithm if the config skipUnknownCommits is disabled.
     */
    @Test
    public void shouldFindBuildWithMultipleCommitsInReferenceBuild() throws Exception {
        initializeRepository("latestBuildIfNotFound: false");

        WorkflowMultiBranchProject project = createMultiBranchProject();
        WorkflowRun masterBuild = buildMaster(project, 1);
        verifyRecordSize(masterBuild, 2);

        sampleRepo.write(ADDITIONAL_SOURCE_FILE, "test");
        sampleRepo.git("add", ADDITIONAL_SOURCE_FILE);
        sampleRepo.git("commit", "--all", "--message=test");

        // The Test will automatically build this new branch.
        // For this scenario a new commit will be added later and build a second time.
        sampleRepo.git("checkout", "-b", FEATURE);

        // new master commits
        sampleRepo.git("checkout", MASTER);
        sampleRepo.write(ADDITIONAL_SOURCE_FILE, "test edit");
        sampleRepo.git("commit", "--all", "--message=edit-test");

        WorkflowRun nextMaster = buildMaster(project, 2);
        verifyRecordSize(nextMaster, 2);

        // commits on feature branch
        sampleRepo.git("checkout", FEATURE);
        sampleRepo.write(JENKINS_FILE,
                "echo \"branch=${env.BRANCH_NAME}\"; node {checkout scm; echo readFile('file').toUpperCase(); echo \"GitForensics\"; gitForensics()}");
        sampleRepo.write(SOURCE_FILE, "subsequent content");
        sampleRepo.git("commit", "--all", "--message=tweaked");

        WorkflowRun featureBuild = buildFeature(project, 2);

        GitCommitsRecord gitCommit = featureBuild.getAction(GitCommitsRecord.class);
        assertThat(gitCommit).isNotNull();
        // Only 1 Commit since the checkout is build as well
        assertThat(gitCommit.getCommits()).hasSize(1);

        GitBranchMasterIntersectionFinder finder = featureBuild.getAction(GitBranchMasterIntersectionFinder.class);
        assertThat(finder).isNotNull()
                .hasOwner(featureBuild)
                .hasBuildId(nextMaster.getExternalizableId())
                .hasReferenceBuild(Optional.of(nextMaster));
    }

    /**
     * Check negative case if maxCommits is too low to find the reference Point Checks last 2 commits when 3 would be
     * needed.
     */
    @Test
    public void shouldNotFindBuildWithInsufficientMaxCommitsForMultibranchPipeline() throws Exception {
        initializeRepository("maxCommits: 2");

        WorkflowMultiBranchProject project = createMultiBranchProject();
        WorkflowRun masterBuild = buildMaster(project, 1);
        verifyRecordSize(masterBuild, 2);

        sampleRepo.git("checkout", "-b", FEATURE);
        sampleRepo.write(JENKINS_FILE,
                "echo \"branch=${env.BRANCH_NAME}\"; node {checkout scm; echo readFile('file').toUpperCase(); echo \"GitForensics\"; gitForensics maxCommits: 2}");
        sampleRepo.write(SOURCE_FILE, "subsequent content");
        sampleRepo.git("commit", "--all", "--message=tweaked");

        // Second Commit
        sampleRepo.write(ADDITIONAL_SOURCE_FILE, "Test");
        sampleRepo.git("add", ADDITIONAL_SOURCE_FILE);
        sampleRepo.git("commit", "--all", "--message=test");

        WorkflowRun featureBuild = buildFeature(project, 1);

        GitCommitsRecord gitCommit = featureBuild.getAction(GitCommitsRecord.class);
        assertThat(gitCommit).isNotNull();
        assertThat(gitCommit.getCommits()).hasSize(4);

        GitBranchMasterIntersectionFinder finder = featureBuild.getAction(GitBranchMasterIntersectionFinder.class);
        assertThat(finder).isNotNull();
        assertThat(finder).isNotNull()
                .hasOwner(featureBuild)
                .hasBuildId(GitBranchMasterIntersectionFinder.NO_INTERSECTION_FOUND)
                .hasReferenceBuild(Optional.empty());
    }

    /**
     * Checks the Configuration skipUnknownCommits. If there are unknown commits in the master build the algorithm
     * should skip the build in search for the reference point.
     */
    @Test
    public void shouldSkipBuildWithUnknownBuildsEnabled() throws Exception {
        initializeRepository("skipUnknownCommits: true");

        WorkflowMultiBranchProject project = createMultiBranchProject();
        WorkflowRun masterBuild = buildMaster(project, 1);
        verifyRecordSize(masterBuild, 2);

        sampleRepo.write(ADDITIONAL_SOURCE_FILE, "test");
        sampleRepo.git("add", ADDITIONAL_SOURCE_FILE);
        sampleRepo.git("commit", "--all", "--message=test");

        sampleRepo.git("checkout", "-b", FEATURE);
        sampleRepo.write(JENKINS_FILE,
                "echo \"branch=${env.BRANCH_NAME}\"; node {checkout scm; echo readFile('file').toUpperCase(); echo \"GitForensics\"; gitForensics skipUnknownCommits: true}");
        sampleRepo.write(SOURCE_FILE, "subsequent content");
        sampleRepo.git("commit", "--all", "--message=tweaked");

        // new master commits
        sampleRepo.git("checkout", MASTER);
        sampleRepo.write(ADDITIONAL_SOURCE_FILE, "test edit");
        sampleRepo.git("commit", "--all", "--message=edit-test");

        WorkflowRun nextMaster = buildMaster(project, 2);
        verifyRecordSize(nextMaster, 2);

        WorkflowRun featureBuild = buildFeature(project, 1);

        GitCommitsRecord gitCommit = featureBuild.getAction(GitCommitsRecord.class);
        assertThat(gitCommit).isNotNull();
        assertThat(gitCommit.getCommits()).hasSize(4);

        GitBranchMasterIntersectionFinder finder = featureBuild.getAction(GitBranchMasterIntersectionFinder.class);
        assertThat(finder).isNotNull()
                .hasOwner(featureBuild)
                .hasBuildId(masterBuild.getExternalizableId())
                .hasReferenceBuild(Optional.of(masterBuild));
    }

    /**
     * Checks the configuration latestBuildIfNotFound. If there is no intersection found (in this case due to
     * insufficient maxCommits) then the newest Build of the master job should be taken as reference point.
     */
    @Test
    public void shouldUseNewestBuildIfNewestBuildIfNotFoundIsEnabled() throws Exception {
        initializeRepository("maxCommits: 2", "latestBuildIfNotFound: true");

        WorkflowMultiBranchProject project = createMultiBranchProject();
        WorkflowRun masterBuild = buildMaster(project, 1);
        verifyRecordSize(masterBuild, 2);

        sampleRepo.write("testfile.txt", "testfile");
        sampleRepo.git("add", "testfile.txt");
        sampleRepo.git("commit", "--all", "--message=testfile");

        WorkflowRun nextMaster = buildMaster(project, 2);
        verifyRecordSize(nextMaster, 1);

        sampleRepo.git("checkout", "-b", FEATURE);
        sampleRepo.write(JENKINS_FILE,
                "echo \"branch=${env.BRANCH_NAME}\"; node {checkout scm; echo readFile('file').toUpperCase(); echo \"GitForensics\"; gitForensics maxCommits: 2, latestBuildIfNotFound: true}");
        sampleRepo.write(SOURCE_FILE, "subsequent content");
        sampleRepo.git("commit", "--all", "--message=tweaked");
        // Second Commit
        sampleRepo.write(ADDITIONAL_SOURCE_FILE, "Test");
        sampleRepo.git("add", ADDITIONAL_SOURCE_FILE);
        sampleRepo.git("commit", "--all", "--message=test");

        WorkflowRun featureBuild = buildFeature(project, 1);

        GitCommitsRecord gitCommit = featureBuild.getAction(GitCommitsRecord.class);
        assertThat(gitCommit).isNotNull();
        assertThat(gitCommit.getCommits()).hasSize(5);

        GitBranchMasterIntersectionFinder finder = featureBuild.getAction(GitBranchMasterIntersectionFinder.class);
        assertThat(finder).isNotNull()
                .hasOwner(featureBuild)
                .hasBuildId(nextMaster.getExternalizableId())
                .hasReferenceBuild(Optional.of(nextMaster));
    }

    /**
     * Checks if the correct intersection point is found when there are multiple feature branches and one is checked out
     * not from the master but from one of the other feature branches.
     */
    @Test
    public void shouldFindMasterReferenceIfBranchIsCheckedOutFromAnotherFeatureBranch() throws Exception {
        initializeRepository();

        WorkflowMultiBranchProject project = createMultiBranchProject();
        WorkflowRun masterBuild = buildMaster(project, 1);
        verifyRecordSize(masterBuild, 2);

        createFeatureBranchAndAddCommits();

        WorkflowRun featureBuild = buildFeature(project, 1);

        // Check this plugin
        GitCommitsRecord gitCommit = featureBuild.getAction(GitCommitsRecord.class);
        assertThat(gitCommit).isNotNull();
        assertThat(gitCommit.getCommits()).hasSize(3);

        // Now the second branch
        sampleRepo.git("checkout", "-b", "feature2");
        sampleRepo.write(ADDITIONAL_SOURCE_FILE, "my second feature");
        sampleRepo.git("add", ADDITIONAL_SOURCE_FILE);
        sampleRepo.git("commit", "--all", "--message=secondFeature");

        WorkflowRun anotherBranch = buildBranch(project, "feature2");

        // Found correct intersection? (master and not the first feature branch)
        GitBranchMasterIntersectionFinder finder = anotherBranch.getAction(GitBranchMasterIntersectionFinder.class);
        assertThat(finder).isNotNull()
                .hasOwner(anotherBranch)
                .hasBuildId(masterBuild.getExternalizableId())
                .hasReferenceBuild(Optional.of(masterBuild));
    }

    /**
     * Tests if the Intersection point is not found if the build is deleted.
     */
    @Test
    public void shouldNotFindIntersectionIfBuildWasDeleted2() throws Exception {
        initializeRepository();

        WorkflowMultiBranchProject project = createMultiBranchProject();

        WorkflowRun toDelete = buildMaster(project, 1);
        verifyRecordSize(toDelete, 2);

        String toDeleteId = toDelete.getExternalizableId();
        assertThat(toDelete.getNumber()).isEqualTo(1);

        createFeatureBranchAndAddCommits();

        // New master commits
        sampleRepo.git("checkout", MASTER);
        sampleRepo.write("testfile.txt", "testfile");
        sampleRepo.git("add", "testfile.txt");
        sampleRepo.git("commit", "--all", "--message=testfile");

        // Second master build
        QueueTaskFuture<WorkflowRun> workflowRunQueueTaskFuture = toDelete.getParent().scheduleBuild2(0);
        WorkflowRun nextMaster = workflowRunQueueTaskFuture.get();
        verifyRecordSize(nextMaster, 1);

        // Now delete Build before the feature branch is build.
        Objects.requireNonNull(Run.fromExternalizableId(toDeleteId)).delete();

        WorkflowRun featureBuild = buildFeature(project, 1);

        GitCommitsRecord gitCommit = featureBuild.getAction(GitCommitsRecord.class);
        assertThat(gitCommit).isNotNull();
        assertThat(gitCommit.getCommits()).hasSize(3);

        GitBranchMasterIntersectionFinder finder = featureBuild.getAction(GitBranchMasterIntersectionFinder.class);
        assertThat(finder).isNotNull()
                .hasOwner(featureBuild)
                .hasBuildId(GitBranchMasterIntersectionFinder.NO_INTERSECTION_FOUND)
                .hasReferenceBuild(Optional.empty());
    }

    private void initializeRepository(final String... parameters) throws Exception {
        initializeGitRepository(String.format("echo \"branch=${env.BRANCH_NAME}\"; "
                + "node {checkout scm; echo readFile('file'); "
                + "echo \"GitForensics\"; "
                + "gitForensics(%s)}", String.join(",", parameters)));
    }

    private WorkflowRun buildFeature(final WorkflowMultiBranchProject project, final int buildNumber) throws Exception {
        WorkflowRun featureBuild = buildBranch(project, FEATURE);

        assertThat(featureBuild.getNumber()).isEqualTo(buildNumber);
        assertThatLogContains(featureBuild, "SUBSEQUENT CONTENT");
        assertThatLogContains(featureBuild, "branch=feature");

        return featureBuild;
    }

    private void assertThatLogContains(final WorkflowRun featureBuild, final String value) throws IOException {
        getJenkins().assertLogContains(value, featureBuild);
    }

    private WorkflowRun buildMaster(final WorkflowMultiBranchProject project, final int buildNumber) throws Exception {
        WorkflowRun build = buildBranch(project, MASTER);

        assertThat(build.getNumber()).isEqualTo(buildNumber);
        assertThatLogContains(build, "master content");
        assertThatLogContains(build, "branch=master");

        return build;
    }

    private WorkflowRun buildBranch(final WorkflowMultiBranchProject project, final String feature) throws Exception {
        project.scheduleBuild2(0).getFuture().get();
        WorkflowJob p = findBranchProject(project, feature);
        waitUntilNoctivity();

        return p.getLastBuild();
    }

    private void waitUntilNoctivity() throws Exception {
        getJenkins().waitUntilNoActivity();
    }

    private void createFeatureBranchAndAddCommits() throws Exception {
        // Checkout a new feature branch and add a new commit
        sampleRepo.git("checkout", "-b", FEATURE);
        sampleRepo.write(JENKINS_FILE,
                "echo \"branch=${env.BRANCH_NAME}\"; node {checkout scm; echo readFile('file').toUpperCase(); echo \"GitForensics\"; gitForensics()}");
        sampleRepo.write(SOURCE_FILE, "subsequent content");
        sampleRepo.git("commit", "--all", "--message=tweaked");
    }

    private void initializeGitRepository(final String jenkinsFileContent) throws Exception {
        sampleRepo.write(JENKINS_FILE, jenkinsFileContent);
        sampleRepo.write(SOURCE_FILE, "master content");
        sampleRepo.git("add", JENKINS_FILE);
        sampleRepo.git("commit", "--all", "--message=flow");
    }

    private WorkflowMultiBranchProject createMultiBranchProject() {
        WorkflowMultiBranchProject project = createProject(WorkflowMultiBranchProject.class);
        project.getSourcesList().add(
                new BranchSource(new GitSCMSource(null, sampleRepo.toString(), "", "*",
                        "", false),
                        new DefaultBranchPropertyStrategy(new BranchProperty[0])));
        for (SCMSource source : project.getSCMSources()) {
            assertThat(project).isEqualTo(source.getOwner());
        }
        return project;
    }

    private void verifyRecordSize(final WorkflowRun build, final int size) {
        GitCommitsRecord masterRecord = build.getAction(GitCommitsRecord.class);

        assertThat(masterRecord).isNotNull().isNotEmpty().hasSize(size);
    }

    /**
     * If the Intersection point is not found due to the build being deleted the newest master build should be taken
     * with latestBuildIfNotFound enabled.
     */
    @Test
    @Ignore
    public void shouldTakeNewestMasterBuildIfBuildWasDeletedAndNewestBuildIfNotFoundIsEnabled() throws Exception {
        initializeGitRepository("latestBuildIfNotFound: true");

        WorkflowMultiBranchProject project = createMultiBranchProject();

        WorkflowRun toDelete = buildMaster(project, 1);
        verifyRecordSize(toDelete, 2);

        String toDeleteId = toDelete.getExternalizableId();
        assertThat(toDelete.getNumber()).isEqualTo(1);

        GitCommitsRecord gitCommit = toDelete.getAction(GitCommitsRecord.class);
        assertThat(gitCommit).isNotNull();
        assertThat(gitCommit.getCommits()).hasSize(2);

        sampleRepo.git("checkout", "-b", FEATURE);
        sampleRepo.write(JENKINS_FILE,
                "echo \"branch=${env.BRANCH_NAME}\"; node {checkout scm; echo readFile('file').toUpperCase(); echo \"GitForensics\"; gitForensics latestBuildIfNotFound: true}");
        sampleRepo.write(SOURCE_FILE, "subsequent content");
        sampleRepo.git("commit", "--all", "--message=tweaked");

        // New master commits
        sampleRepo.git("checkout", MASTER);
        sampleRepo.write("testfile.txt", "testfile");
        sampleRepo.git("add", "testfile.txt");
        sampleRepo.git("commit", "--all", "--message=testfile");

        WorkflowRun nextMaster = buildMaster(project, 2);
        verifyRecordSize(nextMaster, 1);

        // Check this plugin
        gitCommit = nextMaster.getAction(GitCommitsRecord.class);
        assertThat(gitCommit).isNotNull();
        assertThat(gitCommit.getCommits()).hasSize(1);

        // Now delete Build before the feature branch is build.
        Objects.requireNonNull(Run.fromExternalizableId(toDeleteId)).delete();

        WorkflowRun featureBuild = buildFeature(project, 1);

        // Check this plugin
        gitCommit = featureBuild.getAction(GitCommitsRecord.class);
        assertThat(gitCommit).isNotNull();
        assertThat(gitCommit.getCommits()).hasSize(3);

        // Found correct intersection?
        GitBranchMasterIntersectionFinder finder = featureBuild.getAction(GitBranchMasterIntersectionFinder.class);
        assertThat(finder).isNotNull();
        assertThat(finder.getBuildId()).isEqualTo(nextMaster.getExternalizableId());
    }

    public static WorkflowJob findBranchProject(WorkflowMultiBranchProject project, String name) throws Exception {
        WorkflowJob p = project.getItem(name);
        showIndexing(project);
        if (p == null) {
            fail(name + " project not found");
        }
        return p;
    }

    static void showIndexing(WorkflowMultiBranchProject project) throws Exception {
        FolderComputation<?> indexing = project.getIndexing();
        System.out.println("---%<--- " + indexing.getUrl());
        indexing.writeWholeLogTo(System.out);
        System.out.println("---%<--- ");
    }
}
