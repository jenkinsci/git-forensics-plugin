package io.jenkins.plugins.forensics.git.reference;

import java.io.IOException;

import org.junit.Rule;
import org.junit.Test;

import hudson.model.FreeStyleProject;
import hudson.plugins.git.GitSCM;
import hudson.scm.SCM;
import jenkins.plugins.git.GitSampleRepoRule;

import io.jenkins.plugins.util.IntegrationTestWithJenkinsPerSuite;

import static io.jenkins.plugins.forensics.assertions.Assertions.*;

/**
 * Integration tests of the recording of Git commits using the classes {@link GitCommitListener} and {@link GitCommit}.
 *
 * @author Arne Schöntag
 */
@SuppressWarnings("PMD.SignatureDeclareThrowsException")
public class GitCommitListenerITest extends IntegrationTestWithJenkinsPerSuite {
    /**
     * Rule for a git repository.
     */
    @Rule
    public GitSampleRepoRule gitRepo = new GitSampleRepoRule();

    /**
     * Creates two builds: the first one has 3 commits, the second one an additional commit. Verifies that
     * the commits are recorded correctly in the {@link GitCommit} instances of the associated builds.
     *
      * @throws Exception in case of an IO exception
     */
    @Test
    public void shouldLogAllGitCommits() throws Exception {
        gitRepo.init();
        createAndCommitFile("First.java", "first commit after init");
        createAndCommitFile("Second.java", "second commit after init");

        FreeStyleProject job = createFreeStyleProject(new GitSCM(gitRepo.toString()));
        buildSuccessfully(job);

        GitCommit referenceBuild = job.getLastCompletedBuild().getAction(GitCommit.class);
        String referenceBuildHead = gitRepo.head();
        assertThat(referenceBuild.getRevisions()).hasSize(3);
        assertThat(referenceBuild.getInfoMessages()).contains(
                "Found no previous build with recorded Git commits - starting initial recording",
                "-> Recorded 3 new commits");
        assertThat(referenceBuild.getErrorMessages()).isEmpty();

        createAndCommitFile("Third.java", "third commit after init");
        buildSuccessfully(job);

        GitCommit nextBuild = job.getLastCompletedBuild().getAction(GitCommit.class);
        assertThat(nextBuild.getRevisions()).hasSize(1);
        assertThat(nextBuild.getInfoMessages()).contains(
                String.format("Found previous build `%s` that contains recorded Git commits", referenceBuild.getOwner()),
                "Starting recording of new commits",
                String.format("-> Latest recorded commit SHA-1: %s", referenceBuildHead),
                "-> Recorded 1 new commits");
        assertThat(referenceBuild.getErrorMessages()).isEmpty();
    }

    private void createAndCommitFile(final String fileName, final String content) throws Exception {
        gitRepo.write(fileName, content);
        gitRepo.git("add", fileName);
        gitRepo.git("commit", "--message=" + fileName + " created");
    }

    private FreeStyleProject createFreeStyleProject(final SCM scm) throws IOException {
        FreeStyleProject project = createFreeStyleProject();
        project.setScm(scm);
        return project;
    }
}
