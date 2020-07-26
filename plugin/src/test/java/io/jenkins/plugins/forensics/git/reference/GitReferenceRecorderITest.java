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
// TODO: add test case that merges with master
// TODO: add freestyle tests
public class GitReferenceRecorderITest extends GitITest {
    private static final String JENKINS_FILE = "Jenkinsfile";
    private static final String SOURCE_FILE = "file";
    private static final String FEATURE = "feature";
    private static final String MASTER = "master";
    private static final String ADDITIONAL_SOURCE_FILE = "test.txt";
    private static final String CHANGED_CONTENT = "changed content";

    @ClassRule
    public static BuildWatcher buildWatcher = new BuildWatcher();

    /**
     * Builds a  multibranch-pipeline with a master and a feature branch, builds them and checks if the correct
     * reference build will be found.
     * <pre>
     * {@code
     * M:  [M1]#1
     *       \
     *   F:  [F2]#1}
     * </pre>
     *
     * @throws Exception
     *         in case of an unexpected error during the tests
     */
    @Test
    public void shouldFindCorrectBuildForMultibranchPipeline() throws Exception {
        WorkflowMultiBranchProject project = initializeGitAndMultiBranchProject();

        buildProject(project);
        WorkflowRun masterBuild = verifyMasterBuild(project, 1);
        verifyRecordSize(masterBuild, 2);

        createFeatureBranchAndAddCommits();

        buildProject(project);
        WorkflowRun featureBuild = verifyFeatureBuild(project, 1);
        verifyRecordSize(featureBuild, 3);

        assertThat(featureBuild.getAction(GitReferenceBuild.class)).isNotNull()
                .hasOwner(featureBuild)
                .hasReferenceBuildId(masterBuild.getExternalizableId())
                .hasSummary(masterBuild.getExternalizableId())
                .hasReferenceBuild(Optional.of(masterBuild));
    }

    /**
     * Checks if the reference build will be found if the master is one commit and build ahead of the feature branch.
     *
     * <pre>
     * {@code
     * M:  [M1]#1 - [M2]#2
     *       \
     *   F:  [F1]#1}
     * </pre>
     *
     * @throws Exception
     *         in case of an unexpected error during the tests
     */
    @Test
    public void shouldHandleExtraCommitsAfterBranchPointOnMaster() throws Exception {
        WorkflowMultiBranchProject project = initializeGitAndMultiBranchProject();

        buildProject(project);
        WorkflowRun masterBuild = verifyMasterBuild(project, 1);
        verifyRecordSize(masterBuild, 2);

        createFeatureBranchAndAddCommits();

        addAdditionalFileTo(MASTER);

        buildProject(project);
        WorkflowRun nextMaster = verifyMasterBuild(project, 2);
        verifyRecordSize(nextMaster, 1);

        buildProject(project);
        WorkflowRun featureBuild = verifyFeatureBuild(project, 1);
        verifyRecordSize(featureBuild, 3);

        assertThat(featureBuild.getAction(GitReferenceBuild.class)).isNotNull()
                .hasOwner(featureBuild)
                .hasReferenceBuildId(masterBuild.getExternalizableId())
                .hasSummary(masterBuild.getExternalizableId())
                .hasReferenceBuild(Optional.of(masterBuild));
    }

    /**
     * Checks if the reference build is found even if the reference build has commits that the feature build does not.
     * This also checks the algorithm if the config {@code skipUnknownCommits} is disabled.
     *
     * <pre>
     * {@code
     * M:  [M1]#1 - [M2, M3]#2
     *                \
     *           F:  [F1]#1 - [F2]#2}
     * </pre>
     *
     * @throws Exception
     *         in case of an unexpected error during the tests
     */
    @Test
    public void shouldFindBuildWithMultipleCommitsInReferenceBuild() throws Exception {
        WorkflowMultiBranchProject project = initializeGitAndMultiBranchProject();
        buildProject(project);

        WorkflowRun masterBuild = verifyMasterBuild(project, 1);
        verifyRecordSize(masterBuild, 2);

        addAdditionalFileTo(MASTER);

        createFeatureBranchAndAddCommits("latestBuildIfNotFound: false");

        changeContentOfAdditionalFile(MASTER, CHANGED_CONTENT);

        buildProject(project);
        WorkflowRun nextMaster = verifyMasterBuild(project, 2);
        verifyRecordSize(nextMaster, 2);

        WorkflowRun firstFeature = verifyFeatureBuild(project, 1);
        verifyRecordSize(firstFeature, 4);

        assertThat(firstFeature.getAction(GitReferenceBuild.class)).isNotNull()
                .hasOwner(firstFeature)
                .hasReferenceBuildId(nextMaster.getExternalizableId())
                .hasSummary(nextMaster.getExternalizableId())
                .hasReferenceBuild(Optional.of(nextMaster));

        changeContentOfAdditionalFile(FEATURE, "feature content");

        buildProject(project);
        WorkflowRun featureBuild = verifyFeatureBuild(project, 2);
        verifyRecordSize(featureBuild, 1);

        assertThat(featureBuild.getAction(GitReferenceBuild.class)).isNotNull()
                .hasOwner(featureBuild)
                .hasReferenceBuildId(nextMaster.getExternalizableId())
                .hasSummary(nextMaster.getExternalizableId())
                .hasReferenceBuild(Optional.of(nextMaster));
    }

    /**
     * Checks the configuration option {@code skipUnknownCommits}. If there are unknown commits in the master build the
     * algorithm should skip the build in search for the reference point.
     *
     * <pre>
     * {@code
     * M:  [M1]#1 - [M2, M3]#2
     *                \
     *           F:  [F1]#1}
     * </pre>
     *
     * @throws Exception
     *         in case of an unexpected error during the tests
     */
    @Test
    public void shouldSkipBuildWithUnknownBuildsEnabled() throws Exception {
        WorkflowMultiBranchProject project = initializeGitAndMultiBranchProject();
        buildProject(project);

        WorkflowRun masterBuild = verifyMasterBuild(project, 1);
        verifyRecordSize(masterBuild, 2);

        addAdditionalFileTo(MASTER);

        createFeatureBranchAndAddCommits("skipUnknownCommits: true");

        changeContentOfAdditionalFile(MASTER, CHANGED_CONTENT);

        buildProject(project);
        WorkflowRun nextMaster = verifyMasterBuild(project, 2);
        verifyRecordSize(nextMaster, 2);

        buildProject(project);
        WorkflowRun featureBuild = verifyFeatureBuild(project, 1);
        verifyRecordSize(featureBuild, 4);

        assertThat(featureBuild.getAction(GitReferenceBuild.class)).isNotNull()
                .hasOwner(featureBuild)
                .hasReferenceBuildId(masterBuild.getExternalizableId())
                .hasSummary(masterBuild.getExternalizableId())
                .hasReferenceBuild(Optional.of(masterBuild));
    }

    /**
     * Check negative case if {@code maxCommits} is too low to find the reference point. Checks last 2 commits when 3
     * would be needed.
     *
     * <pre>
     * {@code
     * M:  [M1]#1
     *       \
     *  F:  [F1, F2]#1}
     * </pre>
     *
     * @throws Exception
     *         in case of an unexpected error during the tests
     */
    @Test
    public void shouldNotFindBuildWithInsufficientMaxCommitsForMultibranchPipeline() throws Exception {
        WorkflowMultiBranchProject project = initializeGitAndMultiBranchProject();

        buildProject(project);
        WorkflowRun masterBuild = verifyMasterBuild(project, 1);
        verifyRecordSize(masterBuild, 2);

        createFeatureBranchAndAddCommits("maxCommits: 2");

        addAdditionalFileTo(FEATURE);

        buildProject(project);
        WorkflowRun featureBuild = verifyFeatureBuild(project, 1);
        verifyRecordSize(featureBuild, 4);

        assertThat(featureBuild.getAction(GitReferenceBuild.class)).isNotNull()
                .hasOwner(featureBuild)
                .hasReferenceBuildId(GitReferenceBuild.NO_REFERENCE_BUILD)
                .hasSummary(Messages.No_Reference_Build())
                .hasReferenceBuild(Optional.empty());
    }

    /**
     * Checks the configuration {@code latestBuildIfNotFound}. If no reference has been found (in this case due to
     * insufficient {@code maxCommits}) then the lasted build of the master job should be taken as reference point.
     *
     * <pre>
     * {@code
     * M:  [M1]#1 - [M2]#2
     *                \
     *           F:  [F1, F2]#1}
     * </pre>
     *
     * @throws Exception
     *         in case of an unexpected error during the tests
     */
    @Test
    public void shouldUseNewestBuildIfNewestBuildIfNotFoundIsEnabled() throws Exception {
        WorkflowMultiBranchProject project = initializeGitAndMultiBranchProject();

        buildProject(project);
        WorkflowRun masterBuild = verifyMasterBuild(project, 1);
        verifyRecordSize(masterBuild, 2);

        addAdditionalFileTo(MASTER);

        buildProject(project);
        WorkflowRun nextMaster = verifyMasterBuild(project, 2);
        verifyRecordSize(nextMaster, 1);

        createFeatureBranchAndAddCommits("maxCommits: 2", "latestBuildIfNotFound: true");

        changeContentOfAdditionalFile(FEATURE, CHANGED_CONTENT);

        buildProject(project);
        WorkflowRun featureBuild = verifyFeatureBuild(project, 1);
        verifyRecordSize(featureBuild, 5);

        assertThat(featureBuild.getAction(GitReferenceBuild.class)).isNotNull()
                .hasOwner(featureBuild)
                .hasReferenceBuildId(nextMaster.getExternalizableId())
                .hasSummary(nextMaster.getExternalizableId())
                .hasReferenceBuild(Optional.of(nextMaster));
    }

    /**
     * Checks if the correct reference point is found when there are multiple feature branches and one is checked out
     * not from the master but from one of the other feature branches.
     *
     * <pre>
     * {@code
     * M:  [M1]#1
     *       \
     *  FA: [FA1]#1
     *        \
     *   FB: [FB1]#1}
     * </pre>
     *
     * @throws Exception
     *         in case of an unexpected error during the tests
     */
    @Test
    public void shouldFindMasterReferenceIfBranchIsCheckedOutFromAnotherFeatureBranch() throws Exception {
        WorkflowMultiBranchProject project = initializeGitAndMultiBranchProject();

        buildProject(project);
        WorkflowRun masterBuild = verifyMasterBuild(project, 1);
        verifyRecordSize(masterBuild, 2);

        createFeatureBranchAndAddCommits();

        buildProject(project);
        WorkflowRun featureBuild = verifyFeatureBuild(project, 1);
        verifyRecordSize(featureBuild, 3);
        assertThat(featureBuild.getAction(GitReferenceBuild.class)).isNotNull()
                .hasOwner(featureBuild)
                .hasReferenceBuildId(masterBuild.getExternalizableId())
                .hasSummary(masterBuild.getExternalizableId())
                .hasReferenceBuild(Optional.of(masterBuild));

        checkoutNewBranch("feature2");
        addAdditionalFileTo("feature2");

        buildProject(project);
        WorkflowRun anotherBranch = findBranchProject(project, "feature2").getLastBuild();
        verifyRecordSize(anotherBranch, 4);

        assertThat(anotherBranch.getAction(GitReferenceBuild.class)).isNotNull()
                .hasOwner(anotherBranch)
                .hasReferenceBuildId(masterBuild.getExternalizableId())
                .hasSummary(masterBuild.getExternalizableId())
                .hasReferenceBuild(Optional.of(masterBuild));
    }

    /**
     * Checks if the reference point is not found if the build is deleted.
     *
     * <pre>
     * {@code
     * M:  [M1]#(1) - [M2]#2
     *       \
     *   F: [F1]#1}
     * </pre>
     *
     * @throws Exception
     *         in case of an unexpected error during the tests
     */
    @Test
    public void shouldNotFindIntersectionIfBuildWasDeleted() throws Exception {
        WorkflowMultiBranchProject project = initializeGitAndMultiBranchProject();

        buildProject(project);
        WorkflowRun toDelete = verifyMasterBuild(project, 1);
        verifyRecordSize(toDelete, 2);

        String toDeleteId = toDelete.getExternalizableId();
        assertThat(toDelete.getNumber()).isEqualTo(1);

        createFeatureBranchAndAddCommits();

        addAdditionalFileTo(MASTER);

        // Second master build TODO: method
        WorkflowRun nextMaster = buildAgain(toDelete.getParent());
        verifyMasterBuild(project, 2);
        verifyRecordSize(nextMaster, 1);

        // Now delete Build before the feature branch is build.
        Objects.requireNonNull(Run.fromExternalizableId(toDeleteId)).delete();

        buildProject(project);
        WorkflowRun featureBuild = verifyFeatureBuild(project, 1);
        verifyRecordSize(featureBuild, 3);

        assertThat(featureBuild.getAction(GitReferenceBuild.class)).isNotNull()
                .hasOwner(featureBuild)
                .hasReferenceBuildId(GitReferenceBuild.NO_REFERENCE_BUILD)
                .hasSummary(Messages.No_Reference_Build())
                .hasReferenceBuild(Optional.empty());
    }

    /**
     * If the Intersection point is not found due to the build being deleted the newest master build should be taken
     * with {@code latestBuildIfNotFound} enabled.
     * <pre>
     * {@code
     * M:  [M1]#(1) - [M2]#2
     *       \
     *  F:  [F1]#1 - [F2]#2}
     * </pre>
     *
     * @throws Exception
     *         in case of an unexpected error during the tests
     */
    @Test
    public void shouldTakeNewestMasterBuildIfBuildWasDeletedAndNewestBuildIfNotFoundIsEnabled() throws Exception {
        WorkflowMultiBranchProject project = initializeGitAndMultiBranchProject();

        buildProject(project);
        WorkflowRun toDelete = verifyMasterBuild(project, 1);
        verifyRecordSize(toDelete, 2);

        String toDeleteId = toDelete.getExternalizableId();
        assertThat(toDelete.getNumber()).isEqualTo(1);

        createFeatureBranchAndAddCommits("latestBuildIfNotFound: true");

        buildProject(project);
        WorkflowRun firstFeature = verifyFeatureBuild(project, 1);
        verifyRecordSize(firstFeature, 3);

        addAdditionalFileTo(MASTER);

        buildProject(project);
        WorkflowRun nextMaster = verifyMasterBuild(project, 2);
        verifyRecordSize(nextMaster, 1);

        changeContentOfAdditionalFile(FEATURE, CHANGED_CONTENT);

        // Now delete build before the feature branch is build again
        Objects.requireNonNull(Run.fromExternalizableId(toDeleteId)).delete();

        buildProject(project);
        WorkflowRun featureBuild = verifyFeatureBuild(project, 2);
        verifyRecordSize(nextMaster, 1);

        assertThat(featureBuild.getAction(GitReferenceBuild.class)).isNotNull()
                .hasOwner(featureBuild)
                .hasReferenceBuildId(nextMaster.getExternalizableId())
                .hasReferenceBuild(Optional.of(nextMaster));
    }

    private WorkflowMultiBranchProject initializeGitAndMultiBranchProject() throws Exception {
        writeFile(JENKINS_FILE, "echo \"branch=${env.BRANCH_NAME}\"; "
                + "node {checkout scm; echo readFile('file'); "
                + "echo \"GitForensics\"; "
                + "gitForensics()}");
        writeFile(SOURCE_FILE, "master content");
        addFile(JENKINS_FILE);
        commit("initial content");

        return createMultiBranchProject();
    }

    private void createFeatureBranchAndAddCommits(final String... parameters) throws Exception {
        checkoutNewBranch(FEATURE);
        writeFile(JENKINS_FILE,
                String.format("echo \"branch=${env.BRANCH_NAME}\";"
                        + "node {checkout scm; echo readFile('file').toUpperCase(); "
                        + "echo \"GitForensics\"; "
                        + "gitForensics(%s)}", String.join(",", parameters)));
        writeFile(SOURCE_FILE, "feature content");
        commit("feature changes");
    }

    private void addAdditionalFileTo(final String branch) throws Exception {
        changeContentOfAdditionalFile(branch, "test");
    }

    private void changeContentOfAdditionalFile(final String branch, final String content) throws Exception {
        checkout(branch);
        writeFile(ADDITIONAL_SOURCE_FILE, content);
        addFile(ADDITIONAL_SOURCE_FILE);
        commit("Add additional file");
    }

    private WorkflowRun verifyMasterBuild(final WorkflowMultiBranchProject project, final int buildNumber)
            throws Exception {
        return verifyBuild(project, buildNumber, MASTER, "master content");
    }

    private WorkflowRun verifyFeatureBuild(final WorkflowMultiBranchProject project, final int buildNumber)
            throws Exception {
        return verifyBuild(project, buildNumber, FEATURE, FEATURE.toUpperCase() + " CONTENT");
    }

    private WorkflowRun verifyBuild(final WorkflowMultiBranchProject project, final int buildNumber,
            final String master, final String branch) throws Exception {
        WorkflowJob p = findBranchProject(project, master);

        WorkflowRun build = p.getLastBuild();
        assertThat(build.getNumber()).isEqualTo(buildNumber);
        assertThatLogContains(build, branch);
        assertThatLogContains(build, "branch=" + master);

        return build;
    }

    private void assertThatLogContains(final WorkflowRun featureBuild, final String value) throws IOException {
        getJenkins().assertLogContains(value, featureBuild);
    }

    private WorkflowRun buildAgain(final WorkflowJob build) throws Exception {
        return Objects.requireNonNull(build.scheduleBuild2(0)).get();
    }

    private void buildProject(final WorkflowMultiBranchProject project) throws Exception {
        Objects.requireNonNull(project.scheduleBuild2(0)).getFuture().get();

        getJenkins().waitUntilNoActivity();
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

    private WorkflowJob findBranchProject(final WorkflowMultiBranchProject project, final String name) throws Exception {
        WorkflowJob p = project.getItem(name);
        if (p == null) {
            fail(name + " project not found");
        }
        showIndexing(project);
        return p;
    }

    private void showIndexing(final WorkflowMultiBranchProject project) throws Exception {
        FolderComputation<?> indexing = project.getIndexing();
        System.out.println("---%<--- " + indexing.getUrl());
        indexing.writeWholeLogTo(System.out);
        System.out.println("---%<--- ");
    }
}
