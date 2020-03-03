package io.jenkins.plugins.git.forensics.reference;

import com.cloudbees.hudson.plugins.folder.computed.FolderComputation;
import io.jenkins.plugins.forensics.reference.BranchMasterIntersectionFinder;
import jenkins.branch.BranchProperty;
import jenkins.branch.BranchSource;
import jenkins.branch.DefaultBranchPropertyStrategy;
import jenkins.plugins.git.GitBranchSCMHead;
import jenkins.plugins.git.GitSCMSource;
import jenkins.plugins.git.GitSampleRepoRule;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMSource;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.JenkinsRule;

import javax.annotation.Nonnull;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

/**
 * Integrationtest for finding the correct reference point for multibranch pipelines.
 * @author Arne Schöntag
 * also see https://github.com/jenkinsci/workflow-multibranch-plugin
 */
@SuppressWarnings("PMD.SignatureDeclareThrowsException")
public class ReferenceRecorderMultibranchITest {

    @ClassRule public static BuildWatcher buildWatcher = new BuildWatcher();
    @Rule public JenkinsRule r = new JenkinsRule();
    @Rule public GitSampleRepoRule sampleRepo = new GitSampleRepoRule();


    /**
     * Builds a  multibranch-pipeline with a master and a feature branch, builds them and checks if the correct reference build is found.
     * Mostly copied from https://github.com/jenkinsci/workflow-multibranch-plugin/blob/master/src/test/java/org/jenkinsci/plugins/workflow/multibranch/WorkflowMultiBranchProjectTest.java
     * @throws Exception
     */
    @Test
    public void shouldFindCorrectBuildForMultibranchPipeline() throws Exception {
        sampleRepo.init();
        sampleRepo.write("Jenkinsfile", "echo \"branch=${env.BRANCH_NAME}\"; node {checkout scm; echo readFile('file'); echo \"GitForensics\"; gitForensics()}");
        sampleRepo.write("file", "initial content");
        sampleRepo.git("add", "Jenkinsfile");
        sampleRepo.git("commit", "--all", "--message=flow");
        WorkflowMultiBranchProject mp = r.jenkins.createProject(WorkflowMultiBranchProject.class, "p");
        mp.getSourcesList().add(new BranchSource(new GitSCMSource(null, sampleRepo.toString(), "", "*", "", false), new DefaultBranchPropertyStrategy(new BranchProperty[0])));
        for (SCMSource source : mp.getSCMSources()) {
            assertEquals(mp, source.getOwner());
        }
        WorkflowJob p = scheduleAndFindBranchProject(mp, "master");
        assertEquals(new GitBranchSCMHead("master"), SCMHead.HeadByItem.findHead(p));
        assertEquals(1, mp.getItems().size());
        r.waitUntilNoActivity();
        WorkflowRun b1 = p.getLastBuild();
        assertEquals(1, b1.getNumber());
        r.assertLogContains("initial content", b1);
        r.assertLogContains("branch=master", b1);

        // Check this plugin
        GitCommit gitCommit = b1.getAction(GitCommit.class);
        assertNotNull("GitCommit Action must not be null", gitCommit);
        assertEquals("Repository should only have two Commits: the init and the flow commit",2, gitCommit.getGitCommitLog().getRevisions().size());

        sampleRepo.git("checkout", "-b", "feature");
        sampleRepo.write("Jenkinsfile", "echo \"branch=${env.BRANCH_NAME}\"; node {checkout scm; echo readFile('file').toUpperCase(); echo \"GitForensics\"; gitForensics()}");
        sampleRepo.write("file", "subsequent content");
        sampleRepo.git("commit", "--all", "--message=tweaked");
        p = scheduleAndFindBranchProject(mp, "feature");
        assertEquals(2, mp.getItems().size());
        r.waitUntilNoActivity();
        b1 = p.getLastBuild();
        assertEquals(1, b1.getNumber());
        r.assertLogContains("SUBSEQUENT CONTENT", b1);
        r.assertLogContains("branch=feature", b1);

        // Check this plugin
        gitCommit = b1.getAction(GitCommit.class);
        assertNotNull("GitCommit Action must not be null", gitCommit);
        assertEquals("Repository should now have 3 commits",3, gitCommit.getGitCommitLog().getRevisions().size());
        // Found correct intersection?
        BranchMasterIntersectionFinder finder = b1.getAction(BranchMasterIntersectionFinder.class);
        assertNotNull("GitBranchMasterIntersectionFinder Action must not be null or", finder);
        assertEquals("Should have found the correct intersection point", "p/master#1",finder.getBuildId());
    }

    /**
     * Check negative case if maxCommits is too low to find the reference Point
     * Checks last 2 commits when 3 would be needed.
     * @throws Exception
     */
    @Test
    public void shouldNotFindBuildWithInsufficientMaxCommitsForMultibranchPipeline() throws Exception {
        sampleRepo.init();
        sampleRepo.write("Jenkinsfile", "echo \"branch=${env.BRANCH_NAME}\"; node {checkout scm; echo readFile('file'); echo \"GitForensics\"; gitForensics maxCommits: 2}");
        sampleRepo.write("file", "initial content");
        sampleRepo.git("add", "Jenkinsfile");
        sampleRepo.git("commit", "--all", "--message=flow");
        WorkflowMultiBranchProject mp = r.jenkins.createProject(WorkflowMultiBranchProject.class, "p");
        mp.getSourcesList().add(new BranchSource(new GitSCMSource(null, sampleRepo.toString(), "", "*", "", false), new DefaultBranchPropertyStrategy(new BranchProperty[0])));
        for (SCMSource source : mp.getSCMSources()) {
            assertEquals(mp, source.getOwner());
        }
        WorkflowJob p = scheduleAndFindBranchProject(mp, "master");
        assertEquals(new GitBranchSCMHead("master"), SCMHead.HeadByItem.findHead(p));
        assertEquals(1, mp.getItems().size());
        r.waitUntilNoActivity();
        WorkflowRun b1 = p.getLastBuild();
        assertEquals(1, b1.getNumber());
        r.assertLogContains("initial content", b1);
        r.assertLogContains("branch=master", b1);

        // Check this plugin
        GitCommit gitCommit = b1.getAction(GitCommit.class);
        assertNotNull("GitCommit Action must not be null", gitCommit);
        assertEquals("Repository should only have two Commits: the init and the flow commit",2, gitCommit.getGitCommitLog().getRevisions().size());

        sampleRepo.git("checkout", "-b", "feature");
        sampleRepo.write("Jenkinsfile", "echo \"branch=${env.BRANCH_NAME}\"; node {checkout scm; echo readFile('file').toUpperCase(); echo \"GitForensics\"; gitForensics maxCommits: 2}");
        sampleRepo.write("file", "subsequent content");
        sampleRepo.git("commit", "--all", "--message=tweaked");
        // Second Commit
        sampleRepo.write("test.txt", "Test");
        sampleRepo.git("add", "test.txt");
        sampleRepo.git("commit", "--all", "--message=test");
        p = scheduleAndFindBranchProject(mp, "feature");
        assertEquals(2, mp.getItems().size());
        r.waitUntilNoActivity();
        b1 = p.getLastBuild();
        assertEquals(1, b1.getNumber());
        r.assertLogContains("SUBSEQUENT CONTENT", b1);
        r.assertLogContains("branch=feature", b1);

        // Check this plugin
        gitCommit = b1.getAction(GitCommit.class);
        assertNotNull("GitCommit Action must not be null", gitCommit);
        assertEquals("Repository should now have 3 commits",4, gitCommit.getGitCommitLog().getRevisions().size());
        // Found correct intersection?
        BranchMasterIntersectionFinder finder = b1.getAction(BranchMasterIntersectionFinder.class);
        assertNotNull("GitBranchMasterIntersectionFinder Action must not be null or", finder);
        assertEquals("Should have found the correct intersection point", GitBranchMasterIntersectionFinder.NO_INTERSECTION_FOUND,finder.getBuildId());
    }

    public static @Nonnull
    WorkflowJob scheduleAndFindBranchProject(@Nonnull WorkflowMultiBranchProject mp, @Nonnull String name) throws Exception {
        mp.scheduleBuild2(0).getFuture().get();
        return findBranchProject(mp, name);
    }

    public static @Nonnull WorkflowJob findBranchProject(@Nonnull WorkflowMultiBranchProject mp, @Nonnull String name) throws Exception {
        WorkflowJob p = mp.getItem(name);
        showIndexing(mp);
        if (p == null) {
            fail(name + " project not found");
        }
        return p;
    }

    static void showIndexing(@Nonnull WorkflowMultiBranchProject mp) throws Exception {
        FolderComputation<?> indexing = mp.getIndexing();
        System.out.println("---%<--- " + indexing.getUrl());
        indexing.writeWholeLogTo(System.out);
        System.out.println("---%<--- ");
    }
}
