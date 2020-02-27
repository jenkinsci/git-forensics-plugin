package io.jenkins.plugins.git.forensics.reference;

import hudson.model.Action;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.plugins.git.GitSCM;
import hudson.scm.SCM;
import jenkins.plugins.git.GitSampleRepoRule;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.IOException;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests the class {@link GitCommit}.
 *
 * @author Arne Schöntag
 */
@SuppressWarnings("PMD.SignatureDeclareThrowsException")
public class GitCommitITest {

    /**
     * Jenkins rule per suite.
     */
    @ClassRule
    public static final JenkinsRule JENKINS_PER_SUITE = new JenkinsRule();

    /**
     * Rule for a git repository.
     */
    @Rule
    public GitSampleRepoRule gitRepo = new GitSampleRepoRule();

    @Test
    public void shouldFindReferencePoint() throws Exception {
        gitRepo.init();
        createAndCommitFile("Test.java", "public class Test {}");

        FreeStyleProject reference = createFreeStyleProject("reference", new GitSCM(gitRepo.toString()));
        JENKINS_PER_SUITE.assertBuildStatus(Result.SUCCESS, reference.scheduleBuild2(0, new Action[0]));

        GitCommit referenceGitCommit = reference.getLastCompletedBuild().getAction(GitCommit.class);
        assertEquals(2, referenceGitCommit.getRevisions().size());

        createAndCommitFile("Branch.java", "another branch");

        GitSCM git = new GitSCM(gitRepo.toString());
        FreeStyleProject job = createFreeStyleProject("job", git);
        JENKINS_PER_SUITE.assertBuildStatus(Result.SUCCESS, job.scheduleBuild2(0, new Action[0]));

        GitCommit jobGitCommit = job.getLastCompletedBuild().getAction(GitCommit.class);
        assertEquals(3, jobGitCommit.getRevisions().size());

        Optional<String> result = jobGitCommit.getReferencePoint(referenceGitCommit, 100);
        assertTrue(result.isPresent());
        assertEquals(reference.getLastCompletedBuild().getExternalizableId(), result.get());
    }

    private void createAndCommitFile(final String fileName, final String content) throws Exception {
        gitRepo.write(fileName, content);
        gitRepo.git("add", fileName);
        gitRepo.git("commit", "--message=" + fileName + " created");
    }

    private FreeStyleProject createFreeStyleProject(final String name, final SCM scm) throws IOException {
        FreeStyleProject project = JENKINS_PER_SUITE.createFreeStyleProject(name);
        project.setScm(scm);
        return project;
    }
}
