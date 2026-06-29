package io.jenkins.plugins.forensics.git.reference;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import hudson.model.FreeStyleProject;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.extensions.impl.CloneOption;

import io.jenkins.plugins.forensics.git.util.GitCommitTextDecorator;
import io.jenkins.plugins.forensics.git.util.GitITest;
import io.jenkins.plugins.forensics.git.util.GitRepositoryValidator;

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
     * Verifies that a build using a shallow clone (depth=1) still records a {@link GitCommitsRecord}.
     * Previously, the {@link io.jenkins.plugins.forensics.git.util.GitRepositoryValidator#isGitRepository()}
     * method incorrectly returned {@code false} for shallow clones, preventing commit recording and thus
     * breaking {@code discoverGitReferenceBuild}.
     *
     * @throws Exception
     *         in case of an IO exception
     */
    @Test
    void shouldRecordCommitsForShallowClone() throws Exception {
        createAndCommitFile("First.java", "first commit");
        createAndCommitFile("Second.java", "second commit");

        var job = createFreeStyleProjectWithShallowClone("shallow-listener");

        var record = buildSuccessfully(job).getAction(GitCommitsRecord.class);
        assertThat(record)
                .as("GitCommitsRecord must be present even for shallow-clone builds (JENKINS-74921)")
                .isNotNull()
                .isNotEmpty()
                .hasNoErrorMessages()
                .hasInfoMessages(
                        GitRepositoryValidator.INFO_SHALLOW_CLONE_COMMIT_RECORDING,
                        "Found no previous build with recorded Git commits",
                        "-> Starting initial recording of commits");
    }

    /**
     * Verifies that when the commit recorded in the previous build no longer exists in the repository (e.g., after a
     * {@code git commit --amend} followed by a force-push), the plugin sets the {@code maxCommitsReached} flag on the
     * {@link GitCommitsRecord} so that the UI can suppress the misleading "Commits since last build: 200" message.
     * (JENKINS-67281)
     *
     * @throws Exception
     *         in case of an IO exception
     */
    @Test
    void shouldSetMaxCommitsReachedWhenPreviousBuildCommitNoLongerExists() throws Exception {
        writeFile("Initial.java", "initial content");
        addFile("Initial.java");
        commit("Initial commit");

        var job = createFreeStyleProject("max-commits-on-missing-commit");

        var firstRecord = buildSuccessfully(job).getAction(GitCommitsRecord.class);
        assertThat(firstRecord).isNotNull()
                .hasNoErrorMessages()
                .isNotEmpty();
        assertThat(firstRecord.isMaxCommitsReached()).isFalse();

        amendLatestCommit("Amended initial commit");
        var amendedHead = getHead();
        assertThat(amendedHead).isNotEqualTo(firstRecord.getLatestCommit());

        var secondRecord = buildSuccessfully(job).getAction(GitCommitsRecord.class);
        assertThat(secondRecord).isNotNull()
                .hasNoErrorMessages();

        assertThat(secondRecord.isMaxCommitsReached())
                .as("maxCommitsReached must be true when anchor commit was replaced by force-push/amend")
                .isTrue();

        assertThat(secondRecord.getInfoMessages())
                .anySatisfy(msg -> assertThat(msg).contains("Could not determine commits since last build"));
    }

    /**
     * Regression guard for JENKINS-67281: verifies that the normal (no force-push) case still works correctly —
     * the commit count is exact, the flag is not set, and the count is not 200.
     *
     * @throws Exception
     *         in case of an IO exception
     */
    @Test
    void shouldNotSetMaxCommitsReachedForNormalBuilds() throws Exception {
        writeFile("Base.java", "base content");
        addFile("Base.java");
        commit("Base commit");

        var job = createFreeStyleProject("normal-no-flag");

        var firstRecord = buildSuccessfully(job).getAction(GitCommitsRecord.class);
        assertThat(firstRecord).isNotNull().isNotEmpty();
        assertThat(firstRecord.isMaxCommitsReached()).isFalse();

        writeFile("A.java", "class A {}");
        addFile("A.java");
        commit("Add A");
        var commitA = getHead();

        writeFile("B.java", "class B {}");
        addFile("B.java");
        commit("Add B");
        var commitB = getHead();

        var secondRecord = buildSuccessfully(job).getAction(GitCommitsRecord.class);
        assertThat(secondRecord).isNotNull()
                .hasNoErrorMessages()
                .hasLatestCommit(commitB);

        assertThat(secondRecord.isMaxCommitsReached()).isFalse();
        assertThat(secondRecord.getSize()).isEqualTo(2);
        assertThat(secondRecord.getCommits()).containsExactly(commitB, commitA);
    }

    private void amendLatestCommit(final String newMessage) {
        git("commit", "--amend", "--message=" + newMessage);
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

    private FreeStyleProject createFreeStyleProjectWithShallowClone(final String name) throws IOException {
        var project = createProject(FreeStyleProject.class, name);
        var cloneOption = new CloneOption(true, null, null);
        var scm = new GitSCM(GitSCM.createRepoList(getGitRepositoryPath(), null),
                Collections.emptyList(), null, null, Collections.singletonList(cloneOption));
        project.setScm(scm);
        return project;
    }
}