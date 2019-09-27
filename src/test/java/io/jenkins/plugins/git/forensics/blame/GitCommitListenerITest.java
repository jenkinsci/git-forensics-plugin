package io.jenkins.plugins.git.forensics.blame;

import hudson.model.Action;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.plugins.git.GitSCM;
import hudson.scm.SCM;
import io.jenkins.plugins.git.forensics.reference.GitCommit;
import jenkins.plugins.git.GitSampleRepoRule;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class GitCommitListenerITest {

    /** Jenkins rule per suite. */
    @ClassRule
    public static final JenkinsRule JENKINS_PER_SUITE = new JenkinsRule();

    /**
     * Rule for a git repository.
     */
    @Rule
    public GitSampleRepoRule gitRepo = new GitSampleRepoRule();

    @Test
    public void shouldLogAllGitCommits() throws Exception {
        gitRepo.init();
        createAndCommitFile("First.java", "first commit after init");
        createAndCommitFile("Second.java", "second commit after init");

        FreeStyleProject job = createFreeStyleProject("job", new GitSCM(gitRepo.toString()));
        JENKINS_PER_SUITE.assertBuildStatus(Result.SUCCESS, job.scheduleBuild2(0, new Action[0]));

        GitCommit gitCommit = job.getLastCompletedBuild().getAction(GitCommit.class);
        assertFalse(gitCommit.getRevisions().isEmpty());
        assertEquals(3, gitCommit.getRevisions().size());

        createAndCommitFile("Third.java", "third commit after init");
        JENKINS_PER_SUITE.assertBuildStatus(Result.SUCCESS, job.scheduleBuild2(0, new Action[0]));

        gitCommit = job.getLastCompletedBuild().getAction(GitCommit.class);
        assertEquals(1, gitCommit.getRevisions().size());
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
