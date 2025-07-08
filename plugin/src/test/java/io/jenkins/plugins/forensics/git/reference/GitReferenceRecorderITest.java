package io.jenkins.plugins.forensics.git.reference;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junitpioneer.jupiter.Issue;

import com.cloudbees.hudson.plugins.folder.computed.FolderComputation;

import edu.hm.hafner.util.PathUtil;

import java.io.IOException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject;
import hudson.model.Descriptor.FormException;
import hudson.model.Result;
import hudson.model.Run;
import jenkins.branch.BranchProperty;
import jenkins.branch.BranchSource;
import jenkins.branch.DefaultBranchPropertyStrategy;
import jenkins.plugins.git.GitSCMSource;
import jenkins.scm.api.SCMSource;

import io.jenkins.plugins.forensics.git.util.GitCommitTextDecorator;
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
@SuppressWarnings("checkstyle:IllegalCatch")
class GitReferenceRecorderITest extends GitITest {
    private static final String FORENSICS_API_URL = "https://github.com/jenkinsci/forensics-api-plugin.git";

    private static final String JENKINS_FILE = "Jenkinsfile";
    private static final String SOURCE_FILE = "file";
    private static final String FEATURE = "feature";
    private static final String MAIN = "main";
    private static final String ADDITIONAL_SOURCE_FILE = "test.txt";
    private static final String CHANGED_CONTENT = "changed content";
    private static final GitCommitTextDecorator DECORATOR = new GitCommitTextDecorator();

    private static final String MULTI_BRANCH_PROJECT = "Found a `MultiBranchProject`, trying to resolve the target branch from the configuration";
    private static final String MAIN_IS_TARGET = "-> using target branch 'main' as configured in step";
    private static final String NOT_FOUND_MESSAGE = "No reference build with required status found that contains matching commits";

    /**
     * Runs a pipeline and verifies that the recorder does not break the build if Git is not configured.
     *
     * @see <a href="https://issues.jenkins-ci.org/browse/JENKINS-64545">Issue 64545</a>
     */
    @Test
    @Issue("JENKINS-64545")
    void shouldHandleJobsWithoutGitGracefully() throws FormException {
        var job = createPipeline("no-git");

        job.setDefinition(createPipelineScript("node {}"));
        buildSuccessfully(job);

        job.setDefinition(createPipelineScript("node {discoverGitReferenceBuild(referenceJob: 'no-git')}"));
        buildSuccessfully(job);

        assertThat(getConsoleLog(job.getLastBuild())).contains(NOT_FOUND_MESSAGE);
    }

    /**
     * Creates a pipeline for the main branch and another pipeline for the feature branch, builds them and checks if
     * the correct reference build is found.
     * <pre>
     * {@code
     * M:  [M1]#1
     *       \
     *   F:  [F2]#1}
     * </pre>
     */
    @Test
    void shouldFindCorrectBuildInPipelines() {
        var mainBranch = createPipeline(MAIN);
        mainBranch.setDefinition(asStage(createLocalGitCheckout(MAIN)));

        Run<?, ?> mainBuild = buildSuccessfully(mainBranch);

        createFeatureBranchAndAddCommits();

        var featureBranch = createPipeline(FEATURE);
        featureBranch.setDefinition(asStage(createLocalGitCheckout(FEATURE),
                "discoverGitReferenceBuild(referenceJob: '" + MAIN + "')",
                "gitDiffStat()"));

        verifyPipelineResult(mainBuild, featureBranch);

        assertThat(getCommitStatisticsOf(featureBranch.getLastBuild()))
                .hasCommitCount(1)
                .hasFilesCount(1)
                .hasAddedLines(1)
                .hasDeletedLines(0);
    }

    /**
     * Creates a pipeline that checks if the reference build is found if the main is one commit and build ahead of the
     * feature branch.
     *
     * <pre>
     * {@code
     * M:  [M1]#1 - [M2]#2
     *       \
     *   F:  [F1]#1}
     * </pre>
     * @see #shouldHandleExtraCommitsAfterBranchPointOnMain()
     */
    @Test
    void shouldFindCorrectBuildInPipelinesAfterBranchPointOnMain() {
        var mainBranch = createPipeline(MAIN);
        mainBranch.setDefinition(asStage(createLocalGitCheckout(MAIN)));

        Run<?, ?> mainBuild = buildSuccessfully(mainBranch);

        createFeatureBranchAndAddCommits();

        addAdditionalFileTo(MAIN);

        buildAgain(mainBranch);

        var featureBranch = createPipeline(FEATURE);
        featureBranch.setDefinition(asStage(createLocalGitCheckout(FEATURE),
                "discoverGitReferenceBuild(referenceJob: '" + MAIN + "')"));

        Run<?, ?> featureBuild = buildSuccessfully(featureBranch);
        assertThat(featureBuild.getNumber()).isEqualTo(1);

        assertThat(featureBuild.getAction(ReferenceBuild.class)).isNotNull()
                .hasOwner(featureBuild)
                .hasReferenceBuildId(mainBuild.getExternalizableId())
                .hasReferenceBuild(Optional.of(mainBuild))
                .hasMessages("Configured reference job: 'main'",
                        "Found reference build '#1' for target branch");
    }

    /**
     * Creates a pipeline that ignores failed builds.
     *
     * <pre>
     * {@code
     * M:  [M1]#1 - [M2]#2
     *       \
     *   F:  [F1]#1}
     * </pre>
     *
     * @param requiredResult
     *         the required result
     * @param latestBuildIfNotFound
     *         determines whether the latest build should be used if the required result is not found
     *
     * @see #shouldHandleExtraCommitsAfterBranchPointOnMain()
     */
    @Issue("JENKINS-72015")
    @ParameterizedTest(name = "should skip failed builds: status: {0} - latestBuildIfNotFound: {1}")
    @CsvSource({
            "SUCCESS, false", "UNSTABLE, false", ", false",
            "SUCCESS, true ", "UNSTABLE, true ", ", true "})
    void shouldSkipFailedBuildsIfStatusIsWorseThanRequired(final String requiredResult, final boolean latestBuildIfNotFound) {
        var mainBranch = createPipeline(MAIN);
        mainBranch.setDefinition(asStage(createLocalGitCheckout(MAIN)
                + "error('FAILURE')\n"));

        buildWithResult(mainBranch, Result.FAILURE);

        createFeatureBranchAndAddCommits();

        addAdditionalFileTo(MAIN);

        var latestBuild = buildAgain(mainBranch);

        var featureBranch = createPipeline(FEATURE);
        var requiredParameter = StringUtils.isBlank(requiredResult) ? StringUtils.EMPTY : ", requiredResult: '" + requiredResult + "'";
        featureBranch.setDefinition(asStage(createLocalGitCheckout(FEATURE),
                "discoverGitReferenceBuild("
                        + "referenceJob: '" + MAIN + "'"
                        + requiredParameter
                        + ", latestBuildIfNotFound: " + latestBuildIfNotFound + ")"));

        Run<?, ?> featureBuild = buildSuccessfully(featureBranch);
        assertThat(featureBuild.getNumber()).isEqualTo(1);

        var expectedResult = StringUtils.isBlank(requiredResult) ? "UNSTABLE" : requiredResult;

        var referenceBuildAssert = assertThat(featureBuild.getAction(ReferenceBuild.class))
                .isNotNull()
                .hasOwner(featureBuild)
                .hasMessages("Configured reference job: 'main'",
                        "-> found build '#1' in reference job with matching commits",
                        "-> ignoring reference build '#1' or one of its predecessors since none have a result of "
                                + expectedResult + " or better",
                        NOT_FOUND_MESSAGE);

        if (latestBuildIfNotFound) {
            referenceBuildAssert
                    .hasReferenceBuild(Optional.of(latestBuild))
                    .hasReferenceBuildId(latestBuild.getExternalizableId())
                    .hasMessages("Falling back to latest completed build of reference job: '#2'");
        }
        else {
            referenceBuildAssert
                    .hasReferenceBuild(Optional.empty());
        }
    }

    /**
     * Creates a pipeline that uses a previous build of the actual reference build, because the reference build failed.
     *
     * <pre>
     * {@code
     * M:  [M1]#1 - [M2]#2
     *               \
     *            F:  [F1]#1}
     * </pre>
     *
     * @param requiredResult
     *         the required result
     *
     * @see #shouldHandleExtraCommitsAfterBranchPointOnMain()
     */
    @Issue("JENKINS-72015")
    @ParameterizedTest(name = "should skip failed builds: status: {0}")
    @ValueSource(strings = {"SUCCESS", "UNSTABLE", ""})
    void shouldUseSkipFailedBuildsIfStatusIsWorseThanRequired(final String requiredResult) {
        var mainBranch = createPipeline(MAIN);

        mainBranch.setDefinition(asStage(createLocalGitCheckout(MAIN)));

        Run<?, ?> successful = buildSuccessfully(mainBranch);

        addAdditionalFileTo(MAIN);
        mainBranch.setDefinition(asStage(createLocalGitCheckout(MAIN)
                + "error('FAILURE')\n"));

        buildWithResult(mainBranch, Result.FAILURE);

        createFeatureBranchAndAddCommits();

        var featureBranch = createPipeline(FEATURE);
        var requiredParameter = StringUtils.isBlank(requiredResult) ? StringUtils.EMPTY : ", requiredResult: '" + requiredResult + "'";
        featureBranch.setDefinition(asStage(createLocalGitCheckout(FEATURE),
                "discoverGitReferenceBuild("
                        + "referenceJob: '" + MAIN + "'"
                        + requiredParameter + ")"));

        Run<?, ?> featureBuild = buildSuccessfully(featureBranch);
        assertThat(featureBuild.getNumber()).isEqualTo(1);

        assertThat(featureBuild.getAction(ReferenceBuild.class))
                .isNotNull()
                .hasOwner(featureBuild)
                .hasMessages("Configured reference job: 'main'",
                        "-> found build '#2' in reference job with matching commits",
                        "Found reference build '#2' for target branch",
                        "-> Previous build '#1' has a result SUCCESS")
                    .hasReferenceBuild(Optional.of(successful))
                    .hasReferenceBuildId(successful.getExternalizableId());
    }

    /**
     * Creates a pipeline for the main branch and another pipeline for the feature branch, builds them and removes the
     * commits action from the reference job. This simulates a setup where the last main build was finished before
     * the Git forensics plugin has been added.
     */
    @Test
    void shouldFindNoReferenceBuildIfReferenceJobHasNoCommitsAction() {
        var mainBranch = createPipeline(MAIN);
        mainBranch.setDefinition(asStage(createLocalGitCheckout(MAIN)));

        Run<?, ?> mainBuild = buildSuccessfully(mainBranch);
        mainBuild.removeAction(mainBuild.getAction(GitCommitsRecord.class)); // simulate a main build without records
        createFeatureBranchAndAddCommits();

        var featureBranch = createPipeline(FEATURE);
        featureBranch.setDefinition(asStage(createLocalGitCheckout(FEATURE),
                "discoverGitReferenceBuild(referenceJob: '" + MAIN + "')",
                "gitDiffStat()"));

        Run<?, ?> featureBuild = buildSuccessfully(featureBranch);
        assertThat(featureBuild.getNumber()).isEqualTo(1);
        assertThat(featureBuild.getAction(ReferenceBuild.class)).isNotNull()
                .hasOwner(featureBuild)
                .hasReferenceBuild(Optional.empty())
                .hasMessages("Configured reference job: 'main'",
                        "-> selected build '#1' of reference job does not yet contain a `GitCommitsRecord`",
                        "-> no reference build found");
    }

    private CommitStatistics getCommitStatisticsOf(final WorkflowRun lastBuild) {
        return lastBuild.getAction(CommitStatisticsBuildAction.class).getCommitStatistics();
    }

    /**
     * Creates a pipeline for the main branch and another pipeline for the feature branch, builds them and checks if
     * the correct reference build is found. The main branch contains an additional but unrelated SCM
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
    void shouldFindCorrectBuildInPipelinesWithMultipleReposInReference() {
        var mainBranch = createPipeline(MAIN);
        mainBranch.setDefinition(asStage(
                createForensicsCheckoutStep(),
                createLocalGitCheckout(MAIN)));

        Run<?, ?> mainBuild = buildSuccessfully(mainBranch);

        createFeatureBranchAndAddCommits();

        var featureBranch = createPipeline(FEATURE);
        featureBranch.setDefinition(asStage(
                createLocalGitCheckout(FEATURE),
                "discoverGitReferenceBuild(referenceJob: '" + MAIN + "', scm: 'git file')"));

        verifyPipelineResult(mainBuild, featureBranch);
    }

    @Test
    @Issue("JENKINS-75429")
    void shouldMatchCaseInsensitive() {
        var mainBranch = createPipeline(MAIN);
        mainBranch.setDefinition(asStage(
                createForensicsCheckoutStep(),
                createLocalGitCheckout(MAIN)));

        Run<?, ?> mainBuild = buildSuccessfully(mainBranch);

        createFeatureBranchAndAddCommits();

        var featureBranch = createPipeline(FEATURE);
        featureBranch.setDefinition(asStage(
                createLocalGitCheckout(FEATURE),
                "discoverGitReferenceBuild(referenceJob: '" + MAIN + "', scm: 'GIT FILE')"));

        verifyPipelineResult(mainBuild, featureBranch);
    }

    /**
     * Creates a pipeline for the main branch and another pipeline for the feature branch, builds them and checks if
     * the correct reference build is found. The main branch contains an additional but unrelated SCM
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
    void shouldFindCorrectBuildInPipelinesWithMultipleReposInFeature() {
        var mainBranch = createPipeline(MAIN);
        mainBranch.setDefinition(asStage(
                createLocalGitCheckout(MAIN)));

        Run<?, ?> mainBuild = buildSuccessfully(mainBranch);

        createFeatureBranchAndAddCommits();

        var featureBranch = createPipeline(FEATURE);
        featureBranch.setDefinition(asStage(
                createForensicsCheckoutStep(),
                createLocalGitCheckout(FEATURE),
                "discoverGitReferenceBuild(referenceJob: '" + MAIN + "', scm: 'git file')"));

        verifyPipelineResult(mainBuild, featureBranch);
    }

    private String createLocalGitCheckout(final String branch) {
        return "checkout([$class: 'GitSCM', "
                + "branches: [[name: '" + branch + "' ]],\n"
                + getUrl()
                + "extensions: [[$class: 'RelativeTargetDirectory', \n"
                + "            relativeTargetDir: 'forensics-api']]])\n";
    }

    private String createForensicsCheckoutStep() {
        return "checkout([$class: 'GitSCM', "
                + "branches: [[name: 'a6d0ef09ab3c418e370449a884da99b8190ae950' ]],\n"
                + "userRemoteConfigs: [[url: '" + FORENSICS_API_URL + "']],\n"
                + "extensions: [[$class: 'RelativeTargetDirectory', \n"
                + "            relativeTargetDir: 'forensics-api']]])";
    }

    private void verifyPipelineResult(final Run<?, ?> mainBuild, final WorkflowJob featureBranch) {
        Run<?, ?> featureBuild = buildSuccessfully(featureBranch);
        assertThat(featureBuild.getNumber()).isEqualTo(1);

        var featureCommit = getHead();
        checkout(MAIN);
        var mainCommit = getHead();

        assertThat(featureBuild.getAction(ReferenceBuild.class)).isNotNull()
                .hasOwner(featureBuild)
                .hasReferenceBuildId(mainBuild.getExternalizableId())
                .hasReferenceBuild(Optional.of(mainBuild))
                .hasMessages("Configured reference job: 'main'",
                "-> detected 2 commits in current branch (last one: '%s')".formatted(DECORATOR.asText(featureCommit)),
                "-> adding 1 commits from build '#1' of reference job (last one: '%s')".formatted(DECORATOR.asText(mainCommit)),
                "-> found a matching commit in current branch and target branch: '%s'".formatted(DECORATOR.asText(mainCommit)),
                        "Found reference build '#1' for target branch");
    }

    private String getUrl() {
        return "userRemoteConfigs: [[url: 'file://" + new PathUtil().getAbsolutePath(getGitRepositoryPath()) + "']],\n";
    }

    /**
     * Creates a multibranch pipelines (similar to the manual pipelines in {@link #shouldFindCorrectBuildInPipelines()}).
     * <pre>
     * {@code
     * M:  [M1]#1
     *       \
     *   F:  [F2]#1}
     * </pre>
     */
    @Test
    void shouldFindCorrectBuildForMultibranchPipeline() {
        var project = initializeGitAndMultiBranchProject();

        buildProject(project);
        var mainBuild = verifyMainBuild(project, 1);
        verifyRecordSize(mainBuild, 2);

        var mainCommit = getHead();
        var featureCommit = createFeatureBranchAndAddCommits();

        buildProject(project);
        var featureBuild = verifyFeatureBuild(project, 1);
        verifyRecordSize(featureBuild, 3);

        assertThat(featureBuild.getAction(ReferenceBuild.class)).isNotNull()
                .hasOwner(featureBuild)
                .hasReferenceBuildId(mainBuild.getExternalizableId())
                .hasReferenceBuild(Optional.of(mainBuild))
                .hasMessages(MULTI_BRANCH_PROJECT,
                        MAIN_IS_TARGET,
                "-> detected 3 commits in current branch (last one: '%s')".formatted(DECORATOR.asText(featureCommit)),
                "-> adding 2 commits from build '#1' of reference job (last one: '%s')".formatted(DECORATOR.asText(mainCommit)),
                "-> found a matching commit in current branch and target branch: '%s'".formatted(DECORATOR.asText(mainCommit)),
                        "-> found build '#1' in reference job with matching commits");
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
    void shouldFindCorrectBuildForMultibranchPipelineWithComplexBranchNames() {
        var target = "releases/warnings-2021";

        checkoutNewBranch(target);
        var project = initializeGitAndMultiBranchProject();

        buildProject(project);
        var mainBuild = verifyBuild(project, 1, target, "main content");
        verifyRecordSize(mainBuild, 2);

        var feature = "bugfixes/hotfix-124";
        createBranchAndAddCommits(feature, "targetBranch: '" + target + "'");

        buildProject(project);
        var featureBuild = verifyBuild(project, 1, feature, StringUtils.upperCase(feature));
        verifyRecordSize(featureBuild, 3);

        assertThat(featureBuild.getAction(ReferenceBuild.class)).isNotNull()
                .hasOwner(featureBuild)
                .hasReferenceBuildId(mainBuild.getExternalizableId())
                .hasReferenceBuild(Optional.of(mainBuild));
    }

    /**
     * Checks if the reference build will be found if the main is one commit and build ahead of the feature branch.
     *
     * <pre>
     * {@code
     * M:  [M1]#1 - [M2]#2
     *       \
     *   F:  [F1]#1}
     * </pre>
     */
    @Test
    void shouldHandleExtraCommitsAfterBranchPointOnMain() {
        var project = initializeGitAndMultiBranchProject();

        buildProject(project);
        var mainBuild = verifyMainBuild(project, 1);
        verifyRecordSize(mainBuild, 2);
        var initialMain = getHead();

        var featureCommit = createFeatureBranchAndAddCommits();

        var mainCommit = addAdditionalFileTo(MAIN);

        buildAgain(mainBuild.getParent());
        var nextMaster = verifyMainBuild(project, 2);
        verifyRecordSize(nextMaster, 1);

        buildProject(project);
        var featureBuild = verifyFeatureBuild(project, 1);
        verifyRecordSize(featureBuild, 3);

        assertThat(featureBuild.getAction(ReferenceBuild.class)).isNotNull()
                .hasOwner(featureBuild)
                .hasReferenceBuildId(mainBuild.getExternalizableId())
                .hasReferenceBuild(Optional.of(mainBuild))
                .hasMessages(MULTI_BRANCH_PROJECT,
                        MAIN_IS_TARGET,
                "-> detected 3 commits in current branch (last one: '%s')".formatted(DECORATOR.asText(featureCommit)),
                "-> adding 1 commits from build '#2' of reference job (last one: '%s')".formatted(DECORATOR.asText(mainCommit)),
                        "-> no matching commit found yet, continuing with commits of previous build of '#2'",
                "-> adding 2 commits from build '#1' of reference job (last one: '%s')".formatted(DECORATOR.asText(initialMain)),
                "-> found a matching commit in current branch and target branch: '%s'".formatted(DECORATOR.asText(initialMain)),
                        "-> found build '#1' in reference job with matching commits");

        assertThat(getCommitStatisticsOf(featureBuild))
                .hasCommitCount(1)
                .hasFilesCount(2)
                .hasAddedLines(2)
                .hasDeletedLines(2);
    }

    /**
     * Checks if the reference build is found even if the reference build contains commits that the feature build does not.
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
    void shouldFindBuildWithMultipleCommitsInReferenceBuild() throws IOException {
        var project = initializeGitAndMultiBranchProject();

        buildProject(project);
        var mainBuild = verifyMainBuild(project, 1);
        verifyRecordSize(mainBuild, 2);

        addAdditionalFileTo(MAIN);

        createFeatureBranchAndAddCommits("latestBuildIfNotFound: false");

        var mainCommit = changeContentOfAdditionalFile(MAIN, CHANGED_CONTENT);

        buildProject(project);
        var nextMaster = verifyMainBuild(project, 2);
        verifyRecordSize(nextMaster, 2);

        var firstFeature = verifyFeatureBuild(project, 1);
        verifyRecordSize(firstFeature, 4);

        assertThat(getCommitStatisticsOf(firstFeature))
                .hasCommitCount(2)
                .hasFilesCount(3)
                .hasAddedLines(3)
                .hasDeletedLines(2);

        assertThat(firstFeature.getAction(ReferenceBuild.class)).as(getLog(firstFeature)).isNotNull()
                .hasOwner(firstFeature); // we do not care about the reference of the first feature build

        var featureCommit = changeContentOfAdditionalFile(FEATURE, FEATURE + " content");

        buildProject(project);
        var featureBuild = verifyFeatureBuild(project, 2);
        verifyRecordSize(featureBuild, 1);

        assertThat(featureBuild.getAction(ReferenceBuild.class)).isNotNull()
                .hasOwner(featureBuild)
                .hasReferenceBuildId(nextMaster.getExternalizableId())
                .hasReferenceBuild(Optional.of(nextMaster))
                .hasMessages(MULTI_BRANCH_PROJECT,
                        MAIN_IS_TARGET,
                "-> detected 5 commits in current branch (last one: '%s')".formatted(DECORATOR.asText(featureCommit)),
                "-> adding 2 commits from build '#2' of reference job (last one: '%s')".formatted(DECORATOR.asText(mainCommit)),
                        "-> found build '#2' in reference job with matching commits");

        assertThat(getCommitStatisticsOf(featureBuild))
                .hasCommitCount(3)
                .hasFilesCount(3)
                .hasAddedLines(4)
                .hasDeletedLines(3);
    }

    /**
     * Checks the configuration option {@code skipUnknownCommits}. If there are unknown commits in the main build the
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
    void shouldSkipBuildWithUnknownBuildsEnabled() {
        var project = initializeGitAndMultiBranchProject();
        buildProject(project);

        var mainBuild = verifyMainBuild(project, 1);
        verifyRecordSize(mainBuild, 2);

        var firstMainCommit = getHead();

        addAdditionalFileTo(MAIN);

        var featureCommit = createFeatureBranchAndAddCommits("skipUnknownCommits: true");

        var mainCommit = changeContentOfAdditionalFile(MAIN, CHANGED_CONTENT);

        buildAgain(mainBuild.getParent());

        var nextMaster = verifyMainBuild(project, 2);
        verifyRecordSize(nextMaster, 2);

        buildProject(project);
        var featureBuild = verifyFeatureBuild(project, 1);
        verifyRecordSize(featureBuild, 4);

        assertThat(featureBuild.getAction(ReferenceBuild.class)).isNotNull()
                .hasOwner(featureBuild)
                .hasReferenceBuildId(mainBuild.getExternalizableId())
                .hasReferenceBuild(Optional.of(mainBuild))
                .hasMessages(MULTI_BRANCH_PROJECT,
                        MAIN_IS_TARGET,
                "-> detected 4 commits in current branch (last one: '%s')".formatted(DECORATOR.asText(featureCommit)),
                "-> adding 2 commits from build '#2' of reference job (last one: '%s')".formatted(DECORATOR.asText(mainCommit)),
                        "-> not all commits of target branch are part of the collected reference builds yet",
                "-> adding 2 commits from build '#1' of reference job (last one: '%s')".formatted(DECORATOR.asText(firstMainCommit)),
                        "-> found build '#1' in reference job with matching commits");
    }

    /**
     * Check negative case if {@code maxCommits} is too low to find the reference point. Checks last 2 commits when 3
     * would be needed.
     *
     * <pre>
     * {@code
     * M:  [M1]#1, [M2]#2, []#3
     *       \
     *  F:  [F1, F2]#1}
     * </pre>
     */
    @Test
    void shouldNotFindBuildWithInsufficientMaxCommitsForMultibranchPipeline() {
        var project = initializeGitAndMultiBranchProject();

        buildProject(project);
        var mainBuild = verifyMainBuild(project, 1);
        verifyRecordSize(mainBuild, 2);

        createFeatureBranchAndAddCommits("maxCommits: 1");

        var featureHead = addAdditionalFileTo(FEATURE);

        var mainHead = changeContentOfAdditionalFile(MAIN, CHANGED_CONTENT);

        buildAgain(mainBuild.getParent());
        var additionalMaster = verifyMainBuild(project, 2);
        verifyRecordSize(additionalMaster, 1);

        buildProject(project);
        var featureBuild = verifyFeatureBuild(project, 1);
        verifyRecordSize(featureBuild, 4);

        assertThat(featureBuild.getAction(ReferenceBuild.class)).isNotNull()
                .hasOwner(featureBuild)
                .hasReferenceBuildId(ReferenceBuild.NO_REFERENCE_BUILD)
                .hasReferenceBuild(Optional.empty())
                .hasMessages(MULTI_BRANCH_PROJECT,
                "-> detected 4 commits in current branch (last one: '%s')".formatted(DECORATOR.asText(featureHead)),
                "-> adding 1 commits from build '#2' of reference job (last one: '%s')".formatted(DECORATOR.asText(mainHead)),
                        "-> no matching commit found yet, continuing with commits of previous build of '#2'",
                        "-> stopping commit search since the #commits of the target builds is 1 and the limit `maxCommits` has been set to 1",
                        "-> no reference build found");
    }

    /**
     * Checks the configuration {@code latestBuildIfNotFound}. If no reference has been found (in this case due to
     * insufficient {@code maxCommits}) then the lasted build of the main job should be taken as reference point.
     *
     * <pre>
     * {@code
     * M:  [M1]#1 - [M2]#2
     *                \
     *           F:  [F1, F2]#1}
     * </pre>
     */
    @Test
    void shouldUseNewestBuildIfNewestBuildIfNotFoundIsEnabled() {
        var project = initializeGitAndMultiBranchProject();

        buildProject(project);
        var mainBuild = verifyMainBuild(project, 1);
        verifyRecordSize(mainBuild, 2);

        addAdditionalFileTo(MAIN);

        buildProject(project);
        var nextMaster = verifyMainBuild(project, 2);
        verifyRecordSize(nextMaster, 1);

        createFeatureBranchAndAddCommits("maxCommits: 2", "latestBuildIfNotFound: true");

        changeContentOfAdditionalFile(FEATURE, CHANGED_CONTENT);

        buildProject(project);
        var featureBuild = verifyFeatureBuild(project, 1);
        verifyRecordSize(featureBuild, 5);

        assertThat(featureBuild.getAction(ReferenceBuild.class)).isNotNull()
                .hasOwner(featureBuild)
                .hasReferenceBuildId(nextMaster.getExternalizableId())
                .hasReferenceBuild(Optional.of(nextMaster));

        assertThat(getCommitStatisticsOf(featureBuild))
                .hasCommitCount(2)
                .hasFilesCount(3)
                .hasAddedLines(3)
                .hasDeletedLines(3);
    }

    /**
     * Checks if the correct reference point is found when there are multiple feature branches and one is checked out
     * not from the main but from one of the other feature branches.
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
    void shouldFindMasterReferenceIfBranchIsCheckedOutFromAnotherFeatureBranch() {
        var project = initializeGitAndMultiBranchProject();

        buildProject(project);
        var mainBuild = verifyMainBuild(project, 1);
        verifyRecordSize(mainBuild, 2);

        createFeatureBranchAndAddCommits();

        buildProject(project);
        var featureBuild = verifyFeatureBuild(project, 1);
        verifyRecordSize(featureBuild, 3);
        assertThat(featureBuild.getAction(ReferenceBuild.class)).isNotNull()
                .hasOwner(featureBuild)
                .hasReferenceBuildId(mainBuild.getExternalizableId())
                .hasReferenceBuild(Optional.of(mainBuild));

        checkoutNewBranch("feature2");
        addAdditionalFileTo("feature2");

        buildProject(project);
        var anotherBranch = findBranchProject(project, "feature2").getLastBuild();
        verifyRecordSize(anotherBranch, 4);

        assertThat(anotherBranch.getAction(ReferenceBuild.class)).isNotNull()
                .hasOwner(anotherBranch)
                .hasReferenceBuildId(mainBuild.getExternalizableId())
                .hasReferenceBuild(Optional.of(mainBuild));
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
    void shouldNotFindIntersectionIfBuildWasDeleted() {
        var project = initializeGitAndMultiBranchProject();

        buildProject(project);
        var toDelete = verifyMainBuild(project, 1);
        verifyRecordSize(toDelete, 2);

        var toDeleteId = toDelete.getExternalizableId();
        assertThat(toDelete.getNumber()).isEqualTo(1);

        createFeatureBranchAndAddCommits();

        addAdditionalFileTo(MAIN);

        var nextMaster = buildAgain(toDelete.getParent());
        verifyMainBuild(project, 2);
        verifyRecordSize(nextMaster, 1);

        // Now delete Build before the feature branch is build.
        delete(toDeleteId);

        buildProject(project);
        var featureBuild = verifyFeatureBuild(project, 1);
        verifyRecordSize(featureBuild, 3);

        assertThat(featureBuild.getAction(ReferenceBuild.class)).isNotNull()
                .hasOwner(featureBuild)
                .hasReferenceBuildId(ReferenceBuild.NO_REFERENCE_BUILD)
                .hasReferenceBuild(Optional.empty());
    }

    /**
     * If the Intersection point is not found due to the build being deleted the newest main build should be taken
     * with {@code latestBuildIfNotFound} enabled.
     * <pre>
     * {@code
     * M:  [M1]#(1) - [M2]#2
     *       \
     *  F:  [F1]#1 - [F2]#2}
     * </pre>
     */
    @Test
    void shouldTakeNewestMasterBuildIfBuildWasDeletedAndNewestBuildIfNotFoundIsEnabled() {
        var project = initializeGitAndMultiBranchProject();

        buildProject(project);
        var toDelete = verifyMainBuild(project, 1);
        verifyRecordSize(toDelete, 2);

        var toDeleteId = toDelete.getExternalizableId();
        assertThat(toDelete.getNumber()).isEqualTo(1);

        createFeatureBranchAndAddCommits("latestBuildIfNotFound: true");

        buildProject(project);
        var firstFeature = verifyFeatureBuild(project, 1);
        verifyRecordSize(firstFeature, 3);

        addAdditionalFileTo(MAIN);

        buildProject(project);
        var nextMaster = verifyMainBuild(project, 2);
        verifyRecordSize(nextMaster, 1);

        changeContentOfAdditionalFile(FEATURE, CHANGED_CONTENT);

        delete(toDeleteId);

        buildProject(project);
        var featureBuild = verifyFeatureBuild(project, 2);
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
            writeFile(SOURCE_FILE, MAIN + " content");
            addFile(JENKINS_FILE);
            commit("initial content");

            return createMultiBranchProject();
        }
        catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private String createFeatureBranchAndAddCommits(final String... parameters) {
        String[] actual = Arrays.copyOf(parameters, parameters.length + 1);
        actual[parameters.length] = "targetBranch: '" + MAIN + "'";
        return createBranchAndAddCommits(FEATURE, actual);
    }

    private String createBranchAndAddCommits(final String branch, final String... parameters) {
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
            return getHead();
        }
        catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private String addAdditionalFileTo(final String branch) {
        return changeContentOfAdditionalFile(branch, "test");
    }

    private String changeContentOfAdditionalFile(final String branch, final String content) {
        checkout(branch);
        writeFile(ADDITIONAL_SOURCE_FILE, content);
        addFile(ADDITIONAL_SOURCE_FILE);
        commit("Add additional file");
        return getHead();
    }

    private void delete(final String toDeleteId) {
        try {
            Objects.requireNonNull(Run.fromExternalizableId(toDeleteId)).delete();
        }
        catch (IOException exception) {
            throw new AssertionError(exception);
        }
    }

    private WorkflowRun verifyMainBuild(final WorkflowMultiBranchProject project, final int buildNumber) {
        return verifyBuild(project, buildNumber, INITIAL_BRANCH, MAIN + " content");
    }

    private WorkflowRun verifyFeatureBuild(final WorkflowMultiBranchProject project, final int buildNumber) {
        return verifyBuild(project, buildNumber, FEATURE, FEATURE.toUpperCase(Locale.ENGLISH) + " CONTENT");
    }

    @SuppressWarnings("PMD.SystemPrintln")
    private WorkflowRun verifyBuild(final WorkflowMultiBranchProject project, final int buildNumber,
            final String branch, final String branchContent) {
        try {
            var p = findBranchProject(project, branch);

            System.out.println("====================================================================================================");
            git("log");
            System.out.println("====================================================================================================");
            var build = p.getLastBuild();
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
            var project = createProject(WorkflowMultiBranchProject.class);
            project.getSourcesList().add(
                    new BranchSource(new GitSCMSource(null, getGitRepositoryPath(), "", "*",
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
        var masterRecord = build.getAction(GitCommitsRecord.class);

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
