package io.jenkins.plugins.forensics.git.reference;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import hudson.model.FreeStyleProject;
import hudson.plugins.git.GitSCM;

import io.jenkins.plugins.forensics.git.util.GitCommitTextDecorator;
import io.jenkins.plugins.forensics.git.util.GitITest;

import static io.jenkins.plugins.forensics.git.assertions.Assertions.*;

/**
 * Integration tests of the recording of Git commits using the classes {@link GitCheckoutListener} and
 * {@link GitCommitsRecord}.
 *
 * @author Arne Schöntag
 */
class GitCheckoutListenerITest extends GitITest {
    private static final GitCommitTextDecorator RENDERER = new GitCommitTextDecorator();

    private static final String GIT_FORENSICS_URL = "https://github.com/jenkinsci/git-forensics-plugin.git";
    private static final String FORENSICS_API_URL = "https://github.com/jenkinsci/forensics-api-plugin.git";

    /**
     * Creates two builds: the first one has 3 commits, the second one an additional commit. Verifies that the commits
     * are recorded correctly in the {@link GitCommitsRecord} instances of the associated builds.
     *
     * @throws Exception
     *         in case of an IO exception
     */
    @Test
    void shouldLogAllGitCommits() throws Exception {
        List<String> expectedCommits = new ArrayList<>();
        expectedCommits.add(getHead());
        createAndCommitFile("First.java", "first commit after init");
        expectedCommits.add(getHead());
        createAndCommitFile("Second.java", "second commit after init");
        expectedCommits.add(getHead());

        var job = createFreeStyleProject("listener");

        var referenceBuildHead = getHead();

        var referenceBuild = buildSuccessfully(job).getAction(GitCommitsRecord.class);
        assertThat(referenceBuild).isNotNull()
                .hasOnlyCommits(expectedCommits)
                .isNotEmpty()
                .hasLatestCommit(referenceBuildHead)
                .hasNoErrorMessages()
                .hasInfoMessages("Found no previous build with recorded Git commits",
                        "-> Starting initial recording of commits");

        createAndCommitFile("Third.java", "third commit after init");

        var nextBuild = buildSuccessfully(job).getAction(GitCommitsRecord.class);

        var nextBuildHead = getHead();
        assertThat(nextBuild).isNotEmpty()
                .hasLatestCommit(getHead())
                .hasOnlyCommits(nextBuildHead)
                .hasNoErrorMessages().hasInfoMessages("-> Recorded one new commit",
                "Found previous build '%s' that contains recorded Git commits".formatted(
                        referenceBuild.getOwner()),
                "-> Starting recording of new commits since '%s'".formatted(
                        RENDERER.asText(referenceBuildHead)),
                "-> Using head commit '%s' as starting point".formatted(
                        RENDERER.asText(nextBuildHead)));
    }

    /**
     * Creates three builds: the first one is the starting point, then we have one additional commit for build #2 and
     * #3. Then the reference point is queried for #3 which should return build #2.
     *
     * @throws Exception
     *         in case of an IO exception
     */
    @Test
    void shouldFindReferencePoint() throws Exception {
        createAndCommitFile("Test.java", "public class Test {}");

        var reference = createFreeStyleProject("reference");
        buildSuccessfully(reference);

        createAndCommitFile("first-after-start", "first commit in reference");
        var first = buildSuccessfully(reference).getAction(GitCommitsRecord.class);
        var firstHead = getHead();
        assertThat(first).isNotEmpty()
                .hasLatestCommit(firstHead)
                .hasOnlyCommits(firstHead);

        createAndCommitFile("second-after-start", "second commit in reference");
        var second = buildSuccessfully(reference).getAction(GitCommitsRecord.class);
        var secondHead = getHead();
        assertThat(second).isNotEmpty()
                .hasLatestCommit(secondHead)
                .hasOnlyCommits(secondHead);

        assertThat(second.getReferencePoint(first, 10, false))
                .isPresent()
                .hasValue(first.getOwner());
    }

    /**
     * Creates a pipeline that checks out two different repositories and verifies that the decorator correctly will be
     * attached to both of them.
     */
    @Test
    void shouldDecorateSeveralRepositories() {
        var job = createPipeline();
        job.setDefinition(asStage(
                "checkout([$class: 'GitSCM', "
                        + "branches: [[name: 'a6d0ef09ab3c418e370449a884da99b8190ae950' ]],\n"
                        + "userRemoteConfigs: [[url: '" + FORENSICS_API_URL + "']],\n"
                        + "extensions: [[$class: 'RelativeTargetDirectory', \n"
                        + "            relativeTargetDir: 'forensics-api']]])",
                "checkout([$class: 'GitSCM', "
                        + "branches: [[name: '28af63def44286729e3b19b03464d100fd1d0587' ]],\n"
                        + "userRemoteConfigs: [[url: '" + GIT_FORENSICS_URL + "']],\n"
                        + "extensions: [[$class: 'RelativeTargetDirectory', \n"
                        + "            relativeTargetDir: 'git-forensics']]])"));

        List<GitCommitsRecord> actions = buildSuccessfully(job).getActions(GitCommitsRecord.class);
        assertThat(actions).hasSize(2);

        verifyAction(actions.get(0), FORENSICS_API_URL);
        verifyAction(actions.get(1), GIT_FORENSICS_URL);
    }

    /**
     * Creates a pipeline that checks out the same repository twice.
     */
    @Test
    void shouldSkipDuplicateRepositories() {
        var job = createPipeline();
        job.setDefinition(asStage(
                "checkout([$class: 'GitSCM', "
                        + "branches: [[name: 'a6d0ef09ab3c418e370449a884da99b8190ae950' ]],\n"
                        + "userRemoteConfigs: [[url: '" + FORENSICS_API_URL + "']],\n"
                        + "extensions: [[$class: 'RelativeTargetDirectory', \n"
                        + "            relativeTargetDir: 'forensics-api']]])",
                "checkout([$class: 'GitSCM', "
                        + "branches: [[name: 'a6d0ef09ab3c418e370449a884da99b8190ae950' ]],\n"
                        + "userRemoteConfigs: [[url: '" + FORENSICS_API_URL + "']],\n"
                        + "extensions: [[$class: 'RelativeTargetDirectory', \n"
                        + "            relativeTargetDir: 'forensics-api']]])"));

        List<GitCommitsRecord> actions = buildSuccessfully(job).getActions(GitCommitsRecord.class);
        assertThat(actions).hasSize(1);

        verifyAction(actions.get(0), FORENSICS_API_URL);
    }

    private void verifyAction(final GitCommitsRecord record, final String repository) {
        assertThat(record.getInfoMessages())
                .contains("Recording commits of 'git " + repository + "'",
                        "Found no previous build with recorded Git commits",
                        "-> Recorded 200 new commits")
                .anySatisfy(value -> assertThat(value)
                        .startsWith(
                                "-> Git commit decorator successfully obtained 'hudson.plugins.git.browser.GithubWeb"));
    }

    /**
     * Verifies that when the commit recorded in the previous build no longer exists in the repository (e.g., after a
     * {@code git commit --amend} followed by a force-push), the plugin does NOT report the incorrect value of 200
     * commits since the last build. Instead it should treat the situation as a fresh start and record commits from
     * scratch (JENKINS-67281).
     *
     * @throws Exception
     *         in case of an IO exception
     */
    @Test
    void shouldRestartRecordingWhenPreviousBuildCommitNoLongerExists() throws Exception {
        writeFile("Initial.java", "initial content");
        addFile("Initial.java");
        commit("Initial commit");

        var job = createFreeStyleProject("restart-on-missing-commit");

        // Build #1: record the initial commit
        var firstBuild = buildSuccessfully(job).getAction(GitCommitsRecord.class);
        assertThat(firstBuild).isNotNull()
                .hasNoErrorMessages()
                .isNotEmpty();

        var firstBuildHead = getHead();
        assertThat(firstBuild.getLatestCommit()).isEqualTo(firstBuildHead);

        // Amend the last commit — this creates a new commit ID, making the old one vanish
        amendLatestCommit("Amended initial commit");
        var amendedHead = getHead();

        assertThat(amendedHead).isNotEqualTo(firstBuildHead);

        // Build #2: the previous build's commit no longer exists
        var secondBuild = buildSuccessfully(job).getAction(GitCommitsRecord.class);
        assertThat(secondBuild).isNotNull()
                .hasNoErrorMessages()
                // Must NOT report 200 — should be treated as a fresh start
                .isFirstBuild()
                .hasLatestCommit(amendedHead);

        // The commit count must not equal MAX_COMMITS (200) due to the bug
        assertThat(secondBuild.getSize()).isNotEqualTo(200);
        assertThat(secondBuild.getInfoMessages())
                .anySatisfy(message -> assertThat(message)
                        .contains("no longer exists in the repository"));
    }

    /**
     * Verifies that when there is a normal build without any force-push/amend, the commit count is computed
     * correctly (regression guard for the fix of JENKINS-67281).
     *
     * @throws Exception
     *         in case of an IO exception
     */
    @Test
    void shouldCountCommitsCorrectlyWithNoForcePush() throws Exception {
        writeFile("Base.java", "base content");
        addFile("Base.java");
        commit("Base commit");

        var job = createFreeStyleProject("normal-count");

        var firstBuild = buildSuccessfully(job).getAction(GitCommitsRecord.class);
        assertThat(firstBuild).isNotNull().isNotEmpty();
        var firstHead = getHead();

        // Add exactly two commits after the first build
        writeFile("A.java", "class A {}");
        addFile("A.java");
        commit("Add A");
        var commitA = getHead();

        writeFile("B.java", "class B {}");
        addFile("B.java");
        commit("Add B");
        var commitB = getHead();

        var secondBuild = buildSuccessfully(job).getAction(GitCommitsRecord.class);
        assertThat(secondBuild).isNotNull()
                .hasNoErrorMessages()
                .hasLatestCommit(commitB);

        // Should have exactly 2 new commits, not 200
        assertThat(secondBuild.getSize()).isEqualTo(2);
        assertThat(secondBuild.getCommits()).containsExactly(commitB, commitA);
    }

    private void amendLatestCommit(final String newMessage) {
        getGitRepository().git("commit", "--amend", "--message=" + newMessage);
    }

    private void createAndCommitFile(final String fileName, final String content) {
        writeFile(fileName, content);
        addFile(fileName);
        commit(fileName + " created");
    }

    private FreeStyleProject createFreeStyleProject(final String name) throws IOException {
        var project = createProject(FreeStyleProject.class, name);
        project.setScm(new GitSCM(getGitRepositoryPath()));
        return project;
    }
}
