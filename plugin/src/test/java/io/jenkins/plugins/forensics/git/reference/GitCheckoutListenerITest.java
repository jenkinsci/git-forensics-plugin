package io.jenkins.plugins.forensics.git.reference;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;

import hudson.model.FreeStyleProject;
import hudson.plugins.git.GitSCM;
import jenkins.plugins.git.GitSampleRepoRule;

import io.jenkins.plugins.util.IntegrationTestWithJenkinsPerSuite;

import static io.jenkins.plugins.forensics.git.assertions.Assertions.*;

/**
 * Integration tests of the recording of Git commits using the classes {@link GitCheckoutListener} and {@link
 * GitCommitsRecord}.
 *
 * @author Arne Schöntag
 */
@SuppressWarnings("PMD.SignatureDeclareThrowsException")
public class GitCheckoutListenerITest extends IntegrationTestWithJenkinsPerSuite {
    /** Provides the Git repository for the test. */
    @Rule
    public GitSampleRepoRule gitRepo = new GitSampleRepoRule();

    /**
     * Creates two builds: the first one has 3 commits, the second one an additional commit. Verifies that the commits
     * are recorded correctly in the {@link GitCommitsRecord} instances of the associated builds.
     *
     * @throws Exception
     *         in case of an IO exception
     */
    @Test
    public void shouldLogAllGitCommits() throws Exception {
        gitRepo.init();

        List<String> expectedCommits = new ArrayList<>();
        expectedCommits.add(gitRepo.head());
        createAndCommitFile("First.java", "first commit after init");
        expectedCommits.add(gitRepo.head());
        createAndCommitFile("Second.java", "second commit after init");
        expectedCommits.add(gitRepo.head());

        FreeStyleProject job = createFreeStyleProject("listener");

        String referenceBuildHead = gitRepo.head();

        GitCommitsRecord referenceBuild = buildSuccessfully(job).getAction(GitCommitsRecord.class);
        assertThat(referenceBuild).isNotNull()
                .hasOnlyCommits(expectedCommits)
                .isNotEmpty()
                .hasLatestCommit(referenceBuildHead)
                .hasNoErrorMessages()
                .hasInfoMessages("Found no previous build with recorded Git commits",
                        "-> Starting initial recording of commits");

        createAndCommitFile("Third.java", "third commit after init");

        GitCommitsRecord nextBuild = buildSuccessfully(job).getAction(GitCommitsRecord.class);

        String nextBuildHead = gitRepo.head();
        assertThat(nextBuild).isNotEmpty()
                .hasLatestCommit(gitRepo.head())
                .hasOnlyCommits(nextBuildHead)
                .hasNoErrorMessages().hasInfoMessages("-> Recorded one new commit",
                String.format("Found previous build '%s' that contains recorded Git commits",
                        referenceBuild.getOwner()),
                String.format("-> Starting recording of new commits since '%s'", referenceBuildHead));
    }

    /**
     * Creates three builds: the first one is the starting point, then we have one additional commit for build #2 and #3.
     * Then the reference point is queried for #3 which should return build #2.
     *
     * @throws Exception
     *         in case of an IO exception
     */
    @Test
    public void shouldFindReferencePoint() throws Exception {
        gitRepo.init();
        createAndCommitFile("Test.java", "public class Test {}");

        FreeStyleProject reference = createFreeStyleProject("reference");
        buildSuccessfully(reference);

        createAndCommitFile("first-after-start", "first commit in reference");
        GitCommitsRecord first = buildSuccessfully(reference).getAction(GitCommitsRecord.class);
        String firstHead = gitRepo.head();
        assertThat(first).isNotEmpty()
                .hasLatestCommit(firstHead)
                .hasOnlyCommits(firstHead);

        createAndCommitFile("second-after-start", "second commit in reference");
        GitCommitsRecord second = buildSuccessfully(reference).getAction(GitCommitsRecord.class);
        String secondHead = gitRepo.head();
        assertThat(second).isNotEmpty()
                .hasLatestCommit(secondHead)
                .hasOnlyCommits(secondHead);

        assertThat(second.getReferencePoint(first, 10, false))
                .isPresent()
                .hasValue(first.getOwner());
    }

    private void createAndCommitFile(final String fileName, final String content) throws Exception {
        gitRepo.write(fileName, content);
        gitRepo.git("add", fileName);
        gitRepo.git("commit", "--message=" + fileName + " created");
    }

    private FreeStyleProject createFreeStyleProject(final String name) throws IOException {
        FreeStyleProject project = createProject(FreeStyleProject.class, name);
        project.setScm(new GitSCM(gitRepo.toString()));
        return project;
    }
}
