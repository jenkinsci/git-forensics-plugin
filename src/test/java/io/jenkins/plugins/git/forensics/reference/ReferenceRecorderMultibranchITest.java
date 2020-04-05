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
import org.junit.Ignore;
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
        assertNotNull("GitBranchMasterIntersectionFinder Action must not be null", finder);
        assertEquals("Should have found the correct intersection point", "p/master#1",finder.getBuildId());
    }

    /**
     * Checks if the intersectionpoint is found if the master is ahead of the feature branch.
     * @throws Exception
     */
    @Test
    public void shouldFindCorrectBuildForMultibranchPipelineWithExtraCommitsAfterBranchPointOnMaster() throws Exception {
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

        // new master commits
        sampleRepo.git("checkout", "master");
        sampleRepo.write("test.txt", "test");
        sampleRepo.git("add", "test.txt");
        sampleRepo.git("commit", "--all", "--message=test");

        p = scheduleAndFindBranchProject(mp, "master");
        assertEquals(2, mp.getItems().size());
        r.waitUntilNoActivity();
        b1 = p.getLastBuild();
        assertEquals(2, b1.getNumber());
        r.assertLogContains("branch=master", b1);

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
        assertNotNull("GitBranchMasterIntersectionFinder Action must not be null", finder);
        assertEquals("Should have found the correct intersection point", "p/master#1",finder.getBuildId());
    }

    /**
     * Checks if the correct build is found even if the reference build has commits that the feature build does not.
     * This also checks the algorithm if the config skipUnknownCommits is disabled.
     * @throws Exception
     */
    @Test
    public void shouldFindBuildWithMultipleCommitsInReferenceBuild() throws Exception {
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

        sampleRepo.write("test.txt", "test");
        sampleRepo.git("add", "test.txt");
        sampleRepo.git("commit", "--all", "--message=test");

        sampleRepo.git("checkout", "-b", "feature");
        sampleRepo.write("Jenkinsfile", "echo \"branch=${env.BRANCH_NAME}\"; node {checkout scm; echo readFile('file').toUpperCase(); echo \"GitForensics\"; gitForensics()}");
        sampleRepo.write("file", "subsequent content");
        sampleRepo.git("commit", "--all", "--message=tweaked");

        // new master commits
        sampleRepo.git("checkout", "master");
        sampleRepo.write("test.txt", "test edit");
        sampleRepo.git("commit", "--all", "--message=edit-test");

        p = scheduleAndFindBranchProject(mp, "master");
        assertEquals(2, mp.getItems().size());
        r.waitUntilNoActivity();
        b1 = p.getLastBuild();
        assertEquals(2, b1.getNumber());
        r.assertLogContains("branch=master", b1);

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
        assertEquals("Repository should now have 4 commits",4, gitCommit.getGitCommitLog().getRevisions().size());
        // Found correct intersection?
        BranchMasterIntersectionFinder finder = b1.getAction(BranchMasterIntersectionFinder.class);
        assertNotNull("GitBranchMasterIntersectionFinder Action must not be null", finder);
        assertEquals("Should have found the correct intersection point", "p/master#2",finder.getBuildId());
    }

    // Testing the configs

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
        assertNotNull("GitBranchMasterIntersectionFinder Action must not be null", finder);
        assertEquals("Should have found the correct intersection point", GitBranchMasterIntersectionFinder.NO_INTERSECTION_FOUND,finder.getBuildId());
    }

    /**
     * Checks the Configuration skipUnknownCommits. If there are unknown commits in the master build the algorithm should
     * skip the build in search for the reference point.
     * @throws Exception
     */
    @Test
    public void shouldSkipBuildWithUnknownBuildsEnabled() throws Exception {
        sampleRepo.init();
        sampleRepo.write("Jenkinsfile", "echo \"branch=${env.BRANCH_NAME}\"; node {checkout scm; echo readFile('file'); echo \"GitForensics\"; gitForensics skipUnknownCommits: true}");
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

        sampleRepo.write("test.txt", "test");
        sampleRepo.git("add", "test.txt");
        sampleRepo.git("commit", "--all", "--message=test");

        sampleRepo.git("checkout", "-b", "feature");
        sampleRepo.write("Jenkinsfile", "echo \"branch=${env.BRANCH_NAME}\"; node {checkout scm; echo readFile('file').toUpperCase(); echo \"GitForensics\"; gitForensics skipUnknownCommits: true}");
        sampleRepo.write("file", "subsequent content");
        sampleRepo.git("commit", "--all", "--message=tweaked");

        // new master commits
        sampleRepo.git("checkout", "master");
        sampleRepo.write("test.txt", "test edit");
        sampleRepo.git("commit", "--all", "--message=edit-test");

        p = scheduleAndFindBranchProject(mp, "master");
        assertEquals(2, mp.getItems().size());
        r.waitUntilNoActivity();
        b1 = p.getLastBuild();
        assertEquals(2, b1.getNumber());
        r.assertLogContains("branch=master", b1);

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
        assertEquals("Repository should now have 4 commits",4, gitCommit.getGitCommitLog().getRevisions().size());
        // Found correct intersection?
        BranchMasterIntersectionFinder finder = b1.getAction(BranchMasterIntersectionFinder.class);
        assertNotNull("GitBranchMasterIntersectionFinder Action must not be null", finder);
        assertEquals("Should have found the correct intersection point", "p/master#1",finder.getBuildId());
    }

    /**
     * Checks the configuration newestBuildIfNotFound. If there is no intersection found (in this case due to insufficient maxCommits)
     * then the newest Build of the master job should be taken as reference point.
     * @throws Exception
     */
    @Test
    public void shouldUseNewestBuildIfNewestBuildIfNotFoundIsEnabled() throws Exception {
        sampleRepo.init();
        sampleRepo.write("Jenkinsfile", "echo \"branch=${env.BRANCH_NAME}\"; node {checkout scm; echo readFile('file'); echo \"GitForensics\"; gitForensics maxCommits: 2, newestBuildIfNotFound: true}");
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

        sampleRepo.write("testfile.txt", "testfile");
        sampleRepo.git("add", "testfile.txt");
        sampleRepo.git("commit", "--all", "--message=testfile");

        // Second master build
        p = scheduleAndFindBranchProject(mp, "master");
        assertEquals(1, mp.getItems().size());
        r.waitUntilNoActivity();
        b1 = p.getLastBuild();
        assertEquals(2, b1.getNumber());
        r.assertLogContains("branch=master", b1);

        // Check this plugin
        GitCommit gitCommit = b1.getAction(GitCommit.class);
        assertNotNull("GitCommit Action must not be null", gitCommit);
        assertEquals("Repository should only have two Commits: the init and the flow commit",1, gitCommit.getGitCommitLog().getRevisions().size());

        sampleRepo.git("checkout", "-b", "feature");
        sampleRepo.write("Jenkinsfile", "echo \"branch=${env.BRANCH_NAME}\"; node {checkout scm; echo readFile('file').toUpperCase(); echo \"GitForensics\"; gitForensics maxCommits: 2, newestBuildIfNotFound: true}");
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
        assertEquals("Repository should now have 3 commits",5, gitCommit.getGitCommitLog().getRevisions().size());
        // Found correct intersection?
        BranchMasterIntersectionFinder finder = b1.getAction(BranchMasterIntersectionFinder.class);
        assertNotNull("GitBranchMasterIntersectionFinder Action must not be null", finder);
        assertEquals("Should have found the correct intersection point", "p/master#2",finder.getBuildId());
    }

    /**
     * Checks if the correct intersection point is found when there are multiple feature branches and one is checked out
     * not from the master but from one of the other feature branches.
     * @throws Exception
     */
    @Test
    public void shouldFindMasterReferenceIfBranchIsCheckedOutFromAnotherFeatureBranch() throws Exception {
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

        // Now the second branch
        sampleRepo.git("checkout", "-b", "feature2");
        sampleRepo.write("test.txt", "my second feature");
        sampleRepo.git("add", "test.txt");
        sampleRepo.git("commit", "--all", "--message=secondFeature");
        p = scheduleAndFindBranchProject(mp, "feature2");
        assertEquals(3, mp.getItems().size());
        r.waitUntilNoActivity();
        b1 = p.getLastBuild();
        assertEquals(1, b1.getNumber());
        // should also contain the log from the first feature branch
        r.assertLogContains("SUBSEQUENT CONTENT", b1);
        r.assertLogContains("branch=feature2", b1);

        // Found correct intersection? (master and not the first feature branch)
        BranchMasterIntersectionFinder finder = b1.getAction(BranchMasterIntersectionFinder.class);
        assertNotNull("GitBranchMasterIntersectionFinder Action must not be null", finder);
        assertEquals("Should have found the correct intersection point", "p/master#1",finder.getBuildId());
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
