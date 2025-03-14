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
@SuppressWarnings("PMD.SignatureDeclareThrowsException")
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

    private void createAndCommitFile(final String fileName, final String content) throws Exception {
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
