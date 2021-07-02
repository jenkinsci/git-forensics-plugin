package io.jenkins.plugins.forensics.git.reference;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.Issue;

import com.cloudbees.hudson.plugins.folder.computed.FolderComputation;

import edu.hm.hafner.util.PathUtil;

import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
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
import io.jenkins.plugins.forensics.miner.CommitStatistics;
import io.jenkins.plugins.forensics.miner.CommitStatisticsBuildAction;
import io.jenkins.plugins.forensics.reference.ReferenceBuild;

import static io.jenkins.plugins.forensics.assertions.Assertions.*;
import static org.jvnet.hudson.test.JenkinsRule.*;

/**
 * Integration tests for finding the correct reference point for multibranch pipelines.
 *
 * @author Arne Schöntag
 * @author Ullrich Hafner
 * @see <a href="https://github.com/jenkinsci/workflow-multibranch-plugin/blob/master/src/test/java/org/jenkinsci/plugins/workflow/multibranch/WorkflowMultiBranchProjectTest.java">Most
 *         tests are based on the integration tests for the multi-branch pipeleine plugin</a>
 */
// TODO: add test case that merges with master
// TODO: add freestyle tests
// TODO: add test if a recorded reference build is deleted afterwards
@SuppressWarnings({"checkstyle:IllegalCatch", "PMD.GodClass"})
public class GitReferenceRecorderITest extends GitITest {
    private static final String FORENSICS_API_URL = "https://github.com/jenkinsci/forensics-api-plugin.git";

    private static final String JENKINS_FILE = "Jenkinsfile";
    private static final String SOURCE_FILE = "file";
    private static final String FEATURE = "feature";
    private static final String MASTER = "master";
    private static final String ADDITIONAL_SOURCE_FILE = "test.txt";
    private static final String CHANGED_CONTENT = "changed content";

    /** Watches for build results. */
    @ClassRule
    public static final BuildWatcher BUILD_WATCHER = new BuildWatcher();

    /**
     * Runs a pipeline and verifies that the recorder does not break the build if Git is not configured.
     *
     * @see <a href="https://issues.jenkins-ci.org/browse/JENKINS-64545">Issue 64545</a>
     */
    @Test
    @Issue("JENKINS-64545")
    public void shouldHandleJobsWithoutGitGracefully() {
        WorkflowJob job = createPipeline("no-git");

        job.setDefinition(new CpsFlowDefinition("node {}", true));
        buildSuccessfully(job);

        job.setDefinition(new CpsFlowDefinition("node {discoverGitReferenceBuild(referenceJob: 'no-git')}", true));
        buildSuccessfully(job);

        assertThat(getConsoleLog(job.getLastBuild())).contains(
                "No reference build found that contains matching commits");
    }

    /**
     * Creates a pipeline for the master branch and another pipeline for the feature branch, builds them and checks if
     * the correct reference build will be found.
     * <pre>
     * {@code
     * M:  [M1]#1
     *       \
     *   F:  [F2]#1}
     * </pre>
     */
    @Test
    public void shouldFindCorrectBuildInPipelines() {
        WorkflowJob mainBranch = createPipeline("main");
        mainBranch.setDefinition(asStage(createLocalGitCheckout("master")));

        Run<?, ?> masterBuild = buildSuccessfully(mainBranch);

        createFeatureBranchAndAddCommits();

        WorkflowJob featureBranch = createPipeline("feature");
        featureBranch.setDefinition(asStage(createLocalGitCheckout("feature"),
                "discoverGitReferenceBuild(referenceJob: 'main')",
                "gitDiffStat()"));

        verifyPipelineResult(masterBuild, featureBranch);

        CommitStatistics action = featureBranch.getLastBuild().getAction(CommitStatisticsBuildAction.class).getCommitStatistics();
        assertThat(action.getCommitCount()).isEqualTo(1);
        assertThat(action.getFilesCount()).isEqualTo(1);
        assertThat(action.getAddedLines()).isEqualTo(1);
        assertThat(action.getDeletedLines()).isEqualTo(0);
    }

    /**
     * Creates a pipeline for the master branch and another pipeline for the feature branch, builds them and checks if
     * the correct reference build will be found. The master branch contains an additional but unrelated SCM
     * repository.
     * <pre>
     * {@code
     * M:  [M1]#1
     *       \
     *   F:  [F2]#1}
     * </pre>
     *
     * @see <a href="https://issues.jenkins.io/browse/JENKINS-64578">Issue 64578</a>
     */
    @Test
    @Issue("JENKINS-64578")
    public void shouldFindCorrectBuildInPipelinesWithMultipleReposInReference() {
        WorkflowJob mainBranch = createPipeline("main");
        mainBranch.setDefinition(asStage(
                createForensicsCheckoutStep(),
                createLocalGitCheckout("master")));

        Run<?, ?> masterBuild = buildSuccessfully(mainBranch);

        createFeatureBranchAndAddCommits();

        WorkflowJob featureBranch = createPipeline("feature");
        featureBranch.setDefinition(asStage(
                createLocalGitCheckout("feature"),
                "discoverGitReferenceBuild(referenceJob: 'main', scm: 'git file')"));

        verifyPipelineResult(masterBuild, featureBranch);
    }

    /**
     * Creates a pipeline for the master branch and another pipeline for the feature branch, builds them and checks if
     * the correct reference build will be found. The master branch contains an additional but unrelated SCM
     * repository.
     * <pre>
     * {@code
     * M:  [M1]#1
     *       \
     *   F:  [F2]#1}
     * </pre>
     *
     * @see <a href="https://issues.jenkins.io/browse/JENKINS-64578">Issue 64578</a>
     */
    @Test
    @Issue("JENKINS-64578")
    public void shouldFindCorrectBuildInPipelinesWithMultipleReposInFeature() {
        WorkflowJob mainBranch = createPipeline("main");
        mainBranch.setDefinition(asStage(
                createLocalGitCheckout("master")));

        Run<?, ?> masterBuild = buildSuccessfully(mainBranch);

        createFeatureBranchAndAddCommits();

        WorkflowJob featureBranch = createPipeline("feature");
        featureBranch.setDefinition(asStage(
                createForensicsCheckoutStep(),
                createLocalGitCheckout("feature"),
                "discoverGitReferenceBuild(referenceJob: 'main', scm: 'git file')"));

        verifyPipelineResult(masterBuild, featureBranch);
    }

    private String createLocalGitCheckout(final String branch) {
        return "checkout([$class: 'GitSCM', "
                + "branches: [[name: '" + branch + "' ]],\n"
                + getUrl()
                + "extensions: [[$class: 'RelativeTargetDirectory', \n"
                + "            relativeTargetDir: 'forensics-api']]])";
    }

    private String createForensicsCheckoutStep() {
        return "checkout([$class: 'GitSCM', "
                + "branches: [[name: 'a6d0ef09ab3c418e370449a884da99b8190ae950' ]],\n"
                + "userRemoteConfigs: [[url: '" + FORENSICS_API_URL + "']],\n"
                + "extensions: [[$class: 'RelativeTargetDirectory', \n"
                + "            relativeTargetDir: 'forensics-api']]])";
    }

    private void verifyPipelineResult(final Run<?, ?> masterBuild, final WorkflowJob featureBranch) {
        Run<?, ?> featureBuild = buildSuccessfully(featureBranch);
        assertThat(featureBuild.getNumber()).isEqualTo(1);

        assertThat(featureBuild.getAction(ReferenceBuild.class)).isNotNull()
                .hasOwner(featureBuild)
                .hasReferenceBuildId(masterBuild.getExternalizableId())
                .hasReferenceBuild(Optional.of(masterBuild));
    }

    private String getUrl() {
        return "userRemoteConfigs: [[url: 'file://" + new PathUtil().getAbsolutePath(sampleRepo.toString()) + "']],\n";
    }

    /**
     * Creates two jobs with pipelines.
     * <pre>
     * {@code
     * M:  [M1]#1
     *       \
     *   F:  [F2]#1}
     * </pre>
     */
    @Test
    public void shouldFindCorrectBuildForMultibranchPipeline() {
        WorkflowMultiBranchProject project = initializeGitAndMultiBranchProject();

        buildProject(project);
        WorkflowRun masterBuild = verifyMasterBuild(project, 1);
        verifyRecordSize(masterBuild, 2);

        createFeatureBranchAndAddCommits();

        buildProject(project);
        WorkflowRun featureBuild = verifyFeatureBuild(project, 1);
        verifyRecordSize(featureBuild, 3);

        assertThat(featureBuild.getAction(ReferenceBuild.class)).isNotNull()
                .hasOwner(featureBuild)
                .hasReferenceBuildId(masterBuild.getExternalizableId())
                .hasReferenceBuild(Optional.of(masterBuild));
    }

    /**
     * Creates two jobs with pipelines. Uses complex branch names that contain slashes.
     * <pre>
     * {@code
     * M:  [M1]#1
     *       \
     *   F:  [F2]#1}
     * </pre>
     */
    @Test @Issue("JENKINS-64544")
    public void shouldFindCorrectBuildForMultibranchPipelineWithComplexBranchNames() {
        String target = "releases/warnings-2021";

        checkoutNewBranch(target);
        WorkflowMultiBranchProject project = initializeGitAndMultiBranchProject();

        buildProject(project);
        WorkflowRun masterBuild = verifyBuild(project, 1, target, "master content");
        verifyRecordSize(masterBuild, 2);

        String feature = "bugfixes/hotfix-124";
        createBranchAndAddCommits(feature, "defaultBranch: '" + target + "'");

        buildProject(project);
        WorkflowRun featureBuild = verifyBuild(project, 1, feature, StringUtils.upperCase(feature));
        verifyRecordSize(featureBuild, 3);

        assertThat(featureBuild.getAction(ReferenceBuild.class)).isNotNull()
                .hasOwner(featureBuild)
                .hasReferenceBuildId(masterBuild.getExternalizableId())
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
     */
    @Test
    public void shouldHandleExtraCommitsAfterBranchPointOnMaster() {
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

        assertThat(featureBuild.getAction(ReferenceBuild.class)).isNotNull()
                .hasOwner(featureBuild)
                .hasReferenceBuildId(masterBuild.getExternalizableId())
                .hasReferenceBuild(Optional.of(masterBuild));

        CommitStatistics action = featureBuild.getAction(CommitStatisticsBuildAction.class).getCommitStatistics();
        assertThat(action.getCommitCount()).isEqualTo(1);
        assertThat(action.getFilesCount()).isEqualTo(2);
        assertThat(action.getDeletedLines()).isEqualTo(2);
        assertThat(action.getAddedLines()).isEqualTo(2);
    }

    /**
     * Checks if the reference build is found even if the reference build has commits that the feature build does not.
     * This also checks the algorithm if the config {@code skipUnknownCommits} is disabled.
     *
     * <pre>
     * {@code
     * M:  [M1]#1 - [M2, M3]#2
     *                \
     *    F:  [F1]#1 - [F2]#2}
     * </pre>
     */
    @Test
    public void shouldFindBuildWithMultipleCommitsInReferenceBuild() throws IOException {
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

        assertThat(firstFeature.getAction(ReferenceBuild.class)).as(getLog(firstFeature)).isNotNull()
                .hasOwner(firstFeature); // we do not care about the reference of the first feature build

        changeContentOfAdditionalFile(FEATURE, "feature content");

        buildProject(project);
        WorkflowRun featureBuild = verifyFeatureBuild(project, 2);
        verifyRecordSize(featureBuild, 1);

        assertThat(featureBuild.getAction(ReferenceBuild.class)).isNotNull()
                .hasOwner(featureBuild)
                .hasReferenceBuildId(nextMaster.getExternalizableId())
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
     */
    @Test
    public void shouldSkipBuildWithUnknownBuildsEnabled() {
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

        assertThat(featureBuild.getAction(ReferenceBuild.class)).isNotNull()
                .hasOwner(featureBuild)
                .hasReferenceBuildId(masterBuild.getExternalizableId())
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
     */
    @Test
    public void shouldNotFindBuildWithInsufficientMaxCommitsForMultibranchPipeline() {
        WorkflowMultiBranchProject project = initializeGitAndMultiBranchProject();

        buildProject(project);
        WorkflowRun masterBuild = verifyMasterBuild(project, 1);
        verifyRecordSize(masterBuild, 2);

        createFeatureBranchAndAddCommits("maxCommits: 2");

        addAdditionalFileTo(FEATURE);

        buildProject(project);
        WorkflowRun featureBuild = verifyFeatureBuild(project, 1);
        verifyRecordSize(featureBuild, 4);

        assertThat(featureBuild.getAction(ReferenceBuild.class)).isNotNull()
                .hasOwner(featureBuild)
                .hasReferenceBuildId(ReferenceBuild.NO_REFERENCE_BUILD)
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
     */
    @Test
    public void shouldUseNewestBuildIfNewestBuildIfNotFoundIsEnabled() {
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

        assertThat(featureBuild.getAction(ReferenceBuild.class)).isNotNull()
                .hasOwner(featureBuild)
                .hasReferenceBuildId(nextMaster.getExternalizableId())
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
     */
    @Test
    public void shouldFindMasterReferenceIfBranchIsCheckedOutFromAnotherFeatureBranch() {
        WorkflowMultiBranchProject project = initializeGitAndMultiBranchProject();

        buildProject(project);
        WorkflowRun masterBuild = verifyMasterBuild(project, 1);
        verifyRecordSize(masterBuild, 2);

        createFeatureBranchAndAddCommits();

        buildProject(project);
        WorkflowRun featureBuild = verifyFeatureBuild(project, 1);
        verifyRecordSize(featureBuild, 3);
        assertThat(featureBuild.getAction(ReferenceBuild.class)).isNotNull()
                .hasOwner(featureBuild)
                .hasReferenceBuildId(masterBuild.getExternalizableId())
                .hasReferenceBuild(Optional.of(masterBuild));

        checkoutNewBranch("feature2");
        addAdditionalFileTo("feature2");

        buildProject(project);
        WorkflowRun anotherBranch = findBranchProject(project, "feature2").getLastBuild();
        verifyRecordSize(anotherBranch, 4);

        assertThat(anotherBranch.getAction(ReferenceBuild.class)).isNotNull()
                .hasOwner(anotherBranch)
                .hasReferenceBuildId(masterBuild.getExternalizableId())
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
     */
    @Test
    public void shouldNotFindIntersectionIfBuildWasDeleted() {
        WorkflowMultiBranchProject project = initializeGitAndMultiBranchProject();

        buildProject(project);
        WorkflowRun toDelete = verifyMasterBuild(project, 1);
        verifyRecordSize(toDelete, 2);

        String toDeleteId = toDelete.getExternalizableId();
        assertThat(toDelete.getNumber()).isEqualTo(1);

        createFeatureBranchAndAddCommits();

        addAdditionalFileTo(MASTER);

        WorkflowRun nextMaster = buildAgain(toDelete.getParent());
        verifyMasterBuild(project, 2);
        verifyRecordSize(nextMaster, 1);

        // Now delete Build before the feature branch is build.
        delete(toDeleteId);

        buildProject(project);
        WorkflowRun featureBuild = verifyFeatureBuild(project, 1);
        verifyRecordSize(featureBuild, 3);

        assertThat(featureBuild.getAction(ReferenceBuild.class)).isNotNull()
                .hasOwner(featureBuild)
                .hasReferenceBuildId(ReferenceBuild.NO_REFERENCE_BUILD)
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
     */
    @Test
    public void shouldTakeNewestMasterBuildIfBuildWasDeletedAndNewestBuildIfNotFoundIsEnabled() {
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

        delete(toDeleteId);

        buildProject(project);
        WorkflowRun featureBuild = verifyFeatureBuild(project, 2);
        verifyRecordSize(nextMaster, 1);

        assertThat(featureBuild.getAction(ReferenceBuild.class)).isNotNull()
                .hasOwner(featureBuild)
                .hasReferenceBuildId(nextMaster.getExternalizableId())
                .hasReferenceBuild(Optional.of(nextMaster));
    }

    private WorkflowMultiBranchProject initializeGitAndMultiBranchProject() {
        try {
            writeFile(JENKINS_FILE, "echo \"branch=${env.BRANCH_NAME}\"; "
                    + "node {checkout scm; echo readFile('file'); "
                    + "echo \"GitForensics\"; "
                    + "discoverGitReferenceBuild();"
                    + "gitDiffStat()}");
            writeFile(SOURCE_FILE, "master content");
            addFile(JENKINS_FILE);
            commit("initial content");

            return createMultiBranchProject();
        }
        catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private void createFeatureBranchAndAddCommits(final String... parameters) {
        createBranchAndAddCommits(FEATURE, parameters);
    }

    private void createBranchAndAddCommits(final String branch, final String... parameters) {
        try {
            checkoutNewBranch(branch);
            writeFile(JENKINS_FILE,
                    String.format("echo \"branch=${env.BRANCH_NAME}\";"
                            + "node {checkout scm; echo readFile('file').toUpperCase(); "
                            + "echo \"GitForensics\"; "
                            + "discoverGitReferenceBuild(%s);"
                            + "gitDiffStat()}", String.join(",", parameters)));
            writeFile(SOURCE_FILE, branch + " content");
            commit(branch + " changes");
        }
        catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private void addAdditionalFileTo(final String branch) {
        changeContentOfAdditionalFile(branch, "test");
    }

    private void changeContentOfAdditionalFile(final String branch, final String content) {
        checkout(branch);
        writeFile(ADDITIONAL_SOURCE_FILE, content);
        addFile(ADDITIONAL_SOURCE_FILE);
        commit("Add additional file");
    }

    private void delete(final String toDeleteId) {
        try {
            Objects.requireNonNull(Run.fromExternalizableId(toDeleteId)).delete();
        }
        catch (IOException exception) {
            throw new AssertionError(exception);
        }
    }

    private WorkflowRun verifyMasterBuild(final WorkflowMultiBranchProject project, final int buildNumber) {
        return verifyBuild(project, buildNumber, MASTER, "master content");
    }

    private WorkflowRun verifyFeatureBuild(final WorkflowMultiBranchProject project, final int buildNumber) {
        return verifyBuild(project, buildNumber, FEATURE, "FEATURE CONTENT");
    }

    private WorkflowRun verifyBuild(final WorkflowMultiBranchProject project, final int buildNumber,
            final String branch, final String branchContent) {
        try {
            WorkflowJob p = findBranchProject(project, branch);

            WorkflowRun build = p.getLastBuild();
            assertThat(build.getNumber()).isEqualTo(buildNumber);
            assertThatLogContains(build, branchContent);
            assertThatLogContains(build, "branch=" + branch);

            return build;
        }
        catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private void assertThatLogContains(final WorkflowRun build, final String value) throws IOException {
        getJenkins().assertLogContains(value, build);
    }

    private WorkflowRun buildAgain(final WorkflowJob build) {
        try {
            return Objects.requireNonNull(build.scheduleBuild2(0)).get();
        }
        catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private void buildProject(final WorkflowMultiBranchProject project) {
        try {
            Objects.requireNonNull(project.scheduleBuild2(0)).getFuture().get();
            showIndexing(project);

            getJenkins().waitUntilNoActivity();
        }
        catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    @SuppressWarnings("deprecation")
    private WorkflowMultiBranchProject createMultiBranchProject() {
        try {
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
        catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private void verifyRecordSize(final WorkflowRun build, final int size) {
        GitCommitsRecord masterRecord = build.getAction(GitCommitsRecord.class);

        GitCommitsRecordAssert.assertThat(masterRecord).isNotNull().isNotEmpty().hasSize(size);
    }

    private WorkflowJob findBranchProject(final WorkflowMultiBranchProject project, final String name) {
        return Objects.requireNonNull(project.getItem(name));
    }

    @SuppressWarnings("PMD.SystemPrintln")
    private void showIndexing(final WorkflowMultiBranchProject project) throws IOException, InterruptedException {
        FolderComputation<?> indexing = project.getIndexing();
        System.out.println("---%<--- " + indexing.getUrl());
        indexing.writeWholeLogTo(System.out);
        System.out.println("---%<--- ");
    }
}
