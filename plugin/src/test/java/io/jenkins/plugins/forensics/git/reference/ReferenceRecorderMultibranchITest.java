package io.jenkins.plugins.forensics.git.reference;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;

import org.junit.ClassRule;
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
     * reference build will be found.
     * <pre>
     * {@code
     * M - 1
     *    \
     *   F - 1}
     * </pre>
     * @throws Exception
     *         in case of an unexpected error during the tests
     */
    @Test
    public void shouldFindCorrectBuildForMultibranchPipeline() throws Exception {
        WorkflowMultiBranchProject project = initializeGitAndMultiBranchProject();

        WorkflowRun masterBuild = buildMaster(project, 1);
        verifyRecordSize(masterBuild, 2);

        createFeatureBranchAndAddCommits();

        WorkflowRun featureBuild = buildFeature(project, 1);
        verifyRecordSize(featureBuild, 3);

        assertThat(featureBuild.getAction(GitBranchMasterIntersectionFinder.class)).isNotNull()
                .hasOwner(featureBuild)
                .hasBuildId(masterBuild.getExternalizableId())
                .hasReferenceBuild(Optional.of(masterBuild));
    }

    /**
     * Checks if the reference build will be found if the master is one commit and build ahead of the feature branch.
     *
     * <pre>
     * {@code
     * M - 1 - 2
     *    \
     *   F - 1}
     * </pre>
     * @throws Exception
     *         in case of an unexpected error during the tests
     */
    @Test
    public void shouldHandleExtraCommitsAfterBranchPointOnMaster() throws Exception {
        WorkflowMultiBranchProject project = initializeGitAndMultiBranchProject();

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
        verifyRecordSize(featureBuild, 3);

        assertThat(featureBuild.getAction(GitBranchMasterIntersectionFinder.class)).isNotNull()
                .hasOwner(featureBuild)
                .hasBuildId(masterBuild.getExternalizableId())
                .hasReferenceBuild(Optional.of(masterBuild));
    }

    /**
     * Checks if the correct build is found even if the reference build has commits that the feature build does not.
     * This also checks the algorithm if the config {@code skipUnknownCommits} is disabled.
     *
     * @throws Exception
     *         in case of an unexpected error during the tests
     */
    @Test
    public void shouldFindBuildWithMultipleCommitsInReferenceBuild() throws Exception {
        WorkflowMultiBranchProject project = initializeGitAndMultiBranchProject();

        WorkflowRun masterBuild = buildMaster(project, 1);
        verifyRecordSize(masterBuild, 2);

        sampleRepo.write(ADDITIONAL_SOURCE_FILE, "test");
        sampleRepo.git("add", ADDITIONAL_SOURCE_FILE);
        sampleRepo.git("commit", "--all", "--message=test");

        // The Test will automatically build this new branch.
        // For this scenario a new commit will be added later and build a second time.
        createFeatureBranchAndAddCommits("latestBuildIfNotFound: false");

        // new master commits
        sampleRepo.git("checkout", MASTER);
        sampleRepo.write(ADDITIONAL_SOURCE_FILE, "test edit");
        sampleRepo.git("commit", "--all", "--message=edit-test");

        WorkflowRun nextMaster = buildMaster(project, 2);
        verifyRecordSize(nextMaster, 2);

        // New feature commits
        sampleRepo.git("checkout", FEATURE);
        sampleRepo.write("testfile.txt", "testfile");
        sampleRepo.git("add", "testfile.txt");
        sampleRepo.git("commit", "--all", "--message=testfile");

        WorkflowRun featureBuild = buildFeature(project, 2);
        verifyRecordSize(featureBuild, 1);

        assertThat(featureBuild.getAction(GitBranchMasterIntersectionFinder.class)).isNotNull()
                .hasOwner(featureBuild)
                .hasBuildId(nextMaster.getExternalizableId())
                .hasReferenceBuild(Optional.of(nextMaster));
    }

    /**
     * Check negative case if {@code maxCommits} is too low to find the reference point. Checks last 2 commits when 3
     * would be needed.
     */
    @Test
    public void shouldNotFindBuildWithInsufficientMaxCommitsForMultibranchPipeline() throws Exception {
        WorkflowMultiBranchProject project = initializeGitAndMultiBranchProject();

        WorkflowRun masterBuild = buildMaster(project, 1);
        verifyRecordSize(masterBuild, 2);

        createFeatureBranchAndAddCommits("maxCommits: 2");

        // Second Commit
        sampleRepo.write(ADDITIONAL_SOURCE_FILE, "Test");
        sampleRepo.git("add", ADDITIONAL_SOURCE_FILE);
        sampleRepo.git("commit", "--all", "--message=test");

        WorkflowRun featureBuild = buildFeature(project, 1);
        verifyRecordSize(featureBuild, 4);

        assertThat(featureBuild.getAction(GitBranchMasterIntersectionFinder.class)).isNotNull()
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
        WorkflowMultiBranchProject project = initializeGitAndMultiBranchProject();

        WorkflowRun masterBuild = buildMaster(project, 1);
        verifyRecordSize(masterBuild, 2);

        sampleRepo.write(ADDITIONAL_SOURCE_FILE, "test");
        sampleRepo.git("add", ADDITIONAL_SOURCE_FILE);
        sampleRepo.git("commit", "--all", "--message=test");

        createFeatureBranchAndAddCommits("skipUnknownCommits: true");

        // new master commits
        sampleRepo.git("checkout", MASTER);
        sampleRepo.write(ADDITIONAL_SOURCE_FILE, "test edit");
        sampleRepo.git("commit", "--all", "--message=edit-test");

        WorkflowRun nextMaster = buildMaster(project, 2);
        verifyRecordSize(nextMaster, 2);

        WorkflowRun featureBuild = buildFeature(project, 1);
        verifyRecordSize(featureBuild, 4);

        assertThat(featureBuild.getAction(GitBranchMasterIntersectionFinder.class)).isNotNull()
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
        WorkflowMultiBranchProject project = initializeGitAndMultiBranchProject();

        WorkflowRun masterBuild = buildMaster(project, 1);
        verifyRecordSize(masterBuild, 2);

        sampleRepo.write("testfile.txt", "testfile");
        sampleRepo.git("add", "testfile.txt");
        sampleRepo.git("commit", "--all", "--message=testfile");

        WorkflowRun nextMaster = buildMaster(project, 2);
        verifyRecordSize(nextMaster, 1);

        createFeatureBranchAndAddCommits("maxCommits: 2", "latestBuildIfNotFound: true");

        // Second Commit
        sampleRepo.write(ADDITIONAL_SOURCE_FILE, "Test");
        sampleRepo.git("add", ADDITIONAL_SOURCE_FILE);
        sampleRepo.git("commit", "--all", "--message=test");

        WorkflowRun featureBuild = buildFeature(project, 1);
        verifyRecordSize(featureBuild, 5);

        assertThat(featureBuild.getAction(GitBranchMasterIntersectionFinder.class)).isNotNull()
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
        WorkflowMultiBranchProject project = initializeGitAndMultiBranchProject();

        WorkflowRun masterBuild = buildMaster(project, 1);
        verifyRecordSize(masterBuild, 2);

        createFeatureBranchAndAddCommits();

        WorkflowRun featureBuild = buildFeature(project, 1);
        verifyRecordSize(featureBuild, 3);

        // Now the second branch
        sampleRepo.git("checkout", "-b", "feature2");
        sampleRepo.write(ADDITIONAL_SOURCE_FILE, "my second feature");
        sampleRepo.git("add", ADDITIONAL_SOURCE_FILE);
        sampleRepo.git("commit", "--all", "--message=secondFeature");

        WorkflowRun anotherBranch = buildBranch(project, "feature2");
        verifyRecordSize(anotherBranch, 4);

        assertThat(anotherBranch.getAction(GitBranchMasterIntersectionFinder.class)).isNotNull()
                .hasOwner(anotherBranch)
                .hasBuildId(masterBuild.getExternalizableId())
                .hasReferenceBuild(Optional.of(masterBuild));
    }

    /**
     * Tests if the Intersection point is not found if the build is deleted.
     */
    @Test
    public void shouldNotFindIntersectionIfBuildWasDeleted() throws Exception {
        WorkflowMultiBranchProject project = initializeGitAndMultiBranchProject();

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
        verifyRecordSize(featureBuild, 3);

        assertThat(featureBuild.getAction(GitBranchMasterIntersectionFinder.class)).isNotNull()
                .hasOwner(featureBuild)
                .hasBuildId(GitBranchMasterIntersectionFinder.NO_INTERSECTION_FOUND)
                .hasReferenceBuild(Optional.empty());
    }

    /**
     * If the Intersection point is not found due to the build being deleted the newest master build should be taken
     * with latestBuildIfNotFound enabled.
     */
    @Test
    public void shouldTakeNewestMasterBuildIfBuildWasDeletedAndNewestBuildIfNotFoundIsEnabled() throws Exception {
        WorkflowMultiBranchProject project = initializeGitAndMultiBranchProject();

        WorkflowRun toDelete = buildMaster(project, 1);
        verifyRecordSize(toDelete, 2);

        String toDeleteId = toDelete.getExternalizableId();
        assertThat(toDelete.getNumber()).isEqualTo(1);

        createFeatureBranchAndAddCommits("latestBuildIfNotFound: true");

        WorkflowRun firstFeature = buildFeature(project, 1);
        verifyRecordSize(firstFeature, 3);

        // New master commits
        sampleRepo.git("checkout", MASTER);
        sampleRepo.write("testfile.txt", "testfile");
        sampleRepo.git("add", "testfile.txt");
        sampleRepo.git("commit", "--all", "--message=testfile");

        WorkflowRun nextMaster = buildMaster(project, 2);
        verifyRecordSize(nextMaster, 1);

        // New feature commits
        sampleRepo.git("checkout", FEATURE);
        sampleRepo.write("testfile.txt", "testfile");
        sampleRepo.git("add", "testfile.txt");
        sampleRepo.git("commit", "--all", "--message=testfile");

        // Now delete build before the feature branch is build again
        Objects.requireNonNull(Run.fromExternalizableId(toDeleteId)).delete();

        WorkflowRun featureBuild = buildFeature(project, 2);
        verifyRecordSize(nextMaster, 1);

        GitBranchMasterIntersectionFinder finder = featureBuild.getAction(GitBranchMasterIntersectionFinder.class);
        assertThat(finder).isNotNull();
        assertThat(finder.getBuildId()).isEqualTo(nextMaster.getExternalizableId());
    }

    private WorkflowMultiBranchProject initializeGitAndMultiBranchProject() throws Exception {
        initializeRepository();

        return createMultiBranchProject();
    }

    private void initializeRepository() throws Exception {
        initializeGitRepository("echo \"branch=${env.BRANCH_NAME}\"; "
                + "node {checkout scm; echo readFile('file'); "
                + "echo \"GitForensics\"; "
                + "gitForensics()}");
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

    private void createFeatureBranchAndAddCommits(final String... parameters) throws Exception {
        sampleRepo.git("checkout", "-b", FEATURE);
        sampleRepo.write(JENKINS_FILE,
                String.format("echo \"branch=${env.BRANCH_NAME}\";"
                        + "node {checkout scm; echo readFile('file').toUpperCase(); "
                        + "echo \"GitForensics\"; "
                        + "gitForensics(%s)}", String.join(",", parameters)));
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
