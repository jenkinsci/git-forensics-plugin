package io.jenkins.plugins.forensics.git.reference;

import com.cloudbees.hudson.plugins.folder.computed.FolderComputation;
import hudson.model.Run;
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

import static io.jenkins.plugins.forensics.assertions.Assertions.fail;
import static org.assertj.core.api.Assertions.assertThat;

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
            assertThat(mp).isEqualTo(source.getOwner());
        }
        WorkflowJob p = scheduleAndFindBranchProject(mp, "master");
        assertThat(new GitBranchSCMHead("master")).isEqualTo(SCMHead.HeadByItem.findHead(p));
        assertThat(mp.getItems()).hasSize(1);
        r.waitUntilNoActivity();
        WorkflowRun b1 = p.getLastBuild();
        assertThat(b1.getNumber()).isEqualTo(1);
        r.assertLogContains("initial content", b1);
        r.assertLogContains("branch=master", b1);

        // Check this plugin
        GitCommit gitCommit = b1.getAction(GitCommit.class);
        assertThat(gitCommit).isNotNull();
        assertThat(gitCommit.getGitCommitLog().getRevisions()).hasSize(2);

        sampleRepo.git("checkout", "-b", "feature");
        sampleRepo.write("Jenkinsfile", "echo \"branch=${env.BRANCH_NAME}\"; node {checkout scm; echo readFile('file').toUpperCase(); echo \"GitForensics\"; gitForensics()}");
        sampleRepo.write("file", "subsequent content");
        sampleRepo.git("commit", "--all", "--message=tweaked");
        p = scheduleAndFindBranchProject(mp, "feature");
        assertThat(mp.getItems()).hasSize(2);
        r.waitUntilNoActivity();
        b1 = p.getLastBuild();
        assertThat(b1.getNumber()).isEqualTo(1);
        r.assertLogContains("SUBSEQUENT CONTENT", b1);
        r.assertLogContains("branch=feature", b1);

        // Check this plugin
        gitCommit = b1.getAction(GitCommit.class);
        assertThat(gitCommit).isNotNull();
        assertThat(gitCommit.getGitCommitLog().getRevisions()).hasSize(3);
        // Found correct intersection?
        BranchMasterIntersectionFinder finder = b1.getAction(BranchMasterIntersectionFinder.class);
        assertThat(finder).isNotNull();
        assertThat("p/master#1").isEqualTo(finder.getBuildId());
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
            assertThat(mp).isEqualTo(source.getOwner());
        }
        WorkflowJob p = scheduleAndFindBranchProject(mp, "master");
        assertThat(new GitBranchSCMHead("master")).isEqualTo(SCMHead.HeadByItem.findHead(p));
        assertThat(mp.getItems()).hasSize(1);
        r.waitUntilNoActivity();
        WorkflowRun b1 = p.getLastBuild();
        assertThat(b1.getNumber()).isEqualTo(1);
        r.assertLogContains("initial content", b1);
        r.assertLogContains("branch=master", b1);

        // Check this plugin
        GitCommit gitCommit = b1.getAction(GitCommit.class);
        assertThat(gitCommit).isNotNull();
        assertThat(gitCommit.getGitCommitLog().getRevisions()).hasSize(2);

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
        assertThat(mp.getItems()).hasSize(2);
        r.waitUntilNoActivity();
        b1 = p.getLastBuild();
        assertThat(b1.getNumber()).isEqualTo(2);
        r.assertLogContains("branch=master", b1);

        p = scheduleAndFindBranchProject(mp, "feature");
        assertThat(mp.getItems()).hasSize(2);
        r.waitUntilNoActivity();
        b1 = p.getLastBuild();
        assertThat(b1.getNumber()).isEqualTo(1);
        r.assertLogContains("SUBSEQUENT CONTENT", b1);
        r.assertLogContains("branch=feature", b1);

        // Check this plugin
        gitCommit = b1.getAction(GitCommit.class);
        assertThat(gitCommit).isNotNull();
        assertThat(gitCommit.getGitCommitLog().getRevisions()).hasSize(3);
        // Found correct intersection?
        BranchMasterIntersectionFinder finder = b1.getAction(BranchMasterIntersectionFinder.class);
        assertThat(finder).isNotNull();
        assertThat("p/master#1").isEqualTo(finder.getBuildId());
    }

    /**
     * Checks if the correct build is found even if the reference build has commits that the feature build does not.
     * This also checks the algorithm if the config skipUnknownCommits is disabled.
     * @throws Exception
     */
    @Test
    public void shouldFindBuildWithMultipleCommitsInReferenceBuild() throws Exception {
        sampleRepo.init();
        sampleRepo.write("Jenkinsfile", "echo \"branch=${env.BRANCH_NAME}\"; node {checkout scm; echo readFile('file'); echo \"GitForensics\"; gitForensics newestBuildIfNotFound: false}");
        sampleRepo.write("file", "initial content");
        sampleRepo.git("add", "Jenkinsfile");
        sampleRepo.git("commit", "--all", "--message=flow");
        WorkflowMultiBranchProject mp = r.jenkins.createProject(WorkflowMultiBranchProject.class, "p");
        mp.getSourcesList().add(new BranchSource(new GitSCMSource(null, sampleRepo.toString(), "", "*", "", false), new DefaultBranchPropertyStrategy(new BranchProperty[0])));
        for (SCMSource source : mp.getSCMSources()) {
            assertThat(mp).isEqualTo(source.getOwner());
        }
        WorkflowJob p = scheduleAndFindBranchProject(mp, "master");
        assertThat(new GitBranchSCMHead("master")).isEqualTo(SCMHead.HeadByItem.findHead(p));
        assertThat(mp.getItems()).hasSize(1);
        r.waitUntilNoActivity();
        WorkflowRun b1 = p.getLastBuild();
        assertThat(b1.getNumber()).isEqualTo(1);
        r.assertLogContains("initial content", b1);
        r.assertLogContains("branch=master", b1);

        // Check this plugin
        GitCommit gitCommit = b1.getAction(GitCommit.class);
        assertThat(gitCommit).isNotNull();
        assertThat(gitCommit.getGitCommitLog().getRevisions()).hasSize(2);

        sampleRepo.write("test.txt", "test");
        sampleRepo.git("add", "test.txt");
        sampleRepo.git("commit", "--all", "--message=test");

        // The Test will automatically build this new branch.
        // For this scenario a new commit will be added later and
        // build a second time.
        sampleRepo.git("checkout", "-b", "feature");

        // new master commits
        sampleRepo.git("checkout", "master");
        sampleRepo.write("test.txt", "test edit");
        sampleRepo.git("commit", "--all", "--message=edit-test");

        p = scheduleAndFindBranchProject(mp, "master");
        assertThat(mp.getItems()).hasSize(2);
        r.waitUntilNoActivity();
        b1 = p.getLastBuild();
        assertThat(b1.getNumber()).isEqualTo(2);
        r.assertLogContains("branch=master", b1);

        // commits on feature branch
        sampleRepo.git("checkout", "feature");
        sampleRepo.write("Jenkinsfile", "echo \"branch=${env.BRANCH_NAME}\"; node {checkout scm; echo readFile('file').toUpperCase(); echo \"GitForensics\"; gitForensics()}");
        sampleRepo.write("file", "subsequent content");
        sampleRepo.git("commit", "--all", "--message=tweaked");

        p = scheduleAndFindBranchProject(mp, "feature");
        assertThat(mp.getItems()).hasSize(2);
        r.waitUntilNoActivity();
        b1 = p.getLastBuild();
        assertThat(b1.getNumber()).isEqualTo(2);
        r.assertLogContains("SUBSEQUENT CONTENT", b1);
        r.assertLogContains("branch=feature", b1);

        // Check this plugin
        gitCommit = b1.getAction(GitCommit.class);
        assertThat(gitCommit).isNotNull();
        // Only 1 Commit since the checkout is build as well
        assertThat(gitCommit.getGitCommitLog().getRevisions()).hasSize(1);
        // Found correct intersection?
        BranchMasterIntersectionFinder finder = b1.getAction(BranchMasterIntersectionFinder.class);
        assertThat(finder).isNotNull();
        assertThat(finder.getBuildId()).isEqualTo("p/master#2");
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
            assertThat(mp).isEqualTo(source.getOwner());
        }
        WorkflowJob p = scheduleAndFindBranchProject(mp, "master");
        assertThat(new GitBranchSCMHead("master")).isEqualTo(SCMHead.HeadByItem.findHead(p));
        assertThat(mp.getItems()).hasSize(1);
        r.waitUntilNoActivity();
        WorkflowRun b1 = p.getLastBuild();
        assertThat(b1.getNumber()).isEqualTo(1);
        r.assertLogContains("initial content", b1);
        r.assertLogContains("branch=master", b1);

        // Check this plugin
        GitCommit gitCommit = b1.getAction(GitCommit.class);
        assertThat(gitCommit).isNotNull();
        assertThat(gitCommit.getGitCommitLog().getRevisions()).hasSize(2);

        sampleRepo.git("checkout", "-b", "feature");
        sampleRepo.write("Jenkinsfile", "echo \"branch=${env.BRANCH_NAME}\"; node {checkout scm; echo readFile('file').toUpperCase(); echo \"GitForensics\"; gitForensics maxCommits: 2}");
        sampleRepo.write("file", "subsequent content");
        sampleRepo.git("commit", "--all", "--message=tweaked");
        // Second Commit
        sampleRepo.write("test.txt", "Test");
        sampleRepo.git("add", "test.txt");
        sampleRepo.git("commit", "--all", "--message=test");
        p = scheduleAndFindBranchProject(mp, "feature");
        assertThat(mp.getItems()).hasSize(2);
        r.waitUntilNoActivity();
        b1 = p.getLastBuild();
        assertThat(b1.getNumber()).isEqualTo(1);
        r.assertLogContains("SUBSEQUENT CONTENT", b1);
        r.assertLogContains("branch=feature", b1);

        // Check this plugin
        gitCommit = b1.getAction(GitCommit.class);
        assertThat(gitCommit).isNotNull();
        assertThat(gitCommit.getGitCommitLog().getRevisions()).hasSize(4);
        // Found correct intersection?
        BranchMasterIntersectionFinder finder = b1.getAction(BranchMasterIntersectionFinder.class);
        assertThat(finder).isNotNull();
        assertThat(GitBranchMasterIntersectionFinder.NO_INTERSECTION_FOUND).isEqualTo(finder.getBuildId());
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
            assertThat(mp).isEqualTo(source.getOwner());
        }
        WorkflowJob p = scheduleAndFindBranchProject(mp, "master");
        assertThat(new GitBranchSCMHead("master")).isEqualTo(SCMHead.HeadByItem.findHead(p));
        assertThat(mp.getItems()).hasSize(1);
        r.waitUntilNoActivity();
        WorkflowRun b1 = p.getLastBuild();
        assertThat(b1.getNumber()).isEqualTo(1);
        r.assertLogContains("initial content", b1);
        r.assertLogContains("branch=master", b1);

        // Check this plugin
        GitCommit gitCommit = b1.getAction(GitCommit.class);
        assertThat(gitCommit).isNotNull();
        assertThat(gitCommit.getGitCommitLog().getRevisions()).hasSize(2);

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
        assertThat(mp.getItems()).hasSize(2);
        r.waitUntilNoActivity();
        b1 = p.getLastBuild();
        assertThat(b1.getNumber()).isEqualTo(2);
        r.assertLogContains("branch=master", b1);

        p = scheduleAndFindBranchProject(mp, "feature");
        assertThat(mp.getItems()).hasSize(2);
        r.waitUntilNoActivity();
        b1 = p.getLastBuild();
        assertThat(b1.getNumber()).isEqualTo(1);
        r.assertLogContains("SUBSEQUENT CONTENT", b1);
        r.assertLogContains("branch=feature", b1);

        // Check this plugin
        gitCommit = b1.getAction(GitCommit.class);
        assertThat(gitCommit).isNotNull();
        assertThat(gitCommit.getGitCommitLog().getRevisions()).hasSize(4);
        // Found correct intersection?
        BranchMasterIntersectionFinder finder = b1.getAction(BranchMasterIntersectionFinder.class);
        assertThat(finder).isNotNull();
        assertThat("p/master#1").isEqualTo(finder.getBuildId());
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
            assertThat(mp).isEqualTo(source.getOwner());
        }
        WorkflowJob p = scheduleAndFindBranchProject(mp, "master");
        assertThat(new GitBranchSCMHead("master")).isEqualTo(SCMHead.HeadByItem.findHead(p));
        assertThat(mp.getItems()).hasSize(1);
        r.waitUntilNoActivity();
        WorkflowRun b1 = p.getLastBuild();
        assertThat(b1.getNumber()).isEqualTo(1);
        r.assertLogContains("initial content", b1);
        r.assertLogContains("branch=master", b1);

        sampleRepo.write("testfile.txt", "testfile");
        sampleRepo.git("add", "testfile.txt");
        sampleRepo.git("commit", "--all", "--message=testfile");

        // Second master build
        p = scheduleAndFindBranchProject(mp, "master");
        assertThat(mp.getItems()).hasSize(1);
        r.waitUntilNoActivity();
        b1 = p.getLastBuild();
        assertThat(b1.getNumber()).isEqualTo(2);
        r.assertLogContains("branch=master", b1);

        // Check this plugin
        GitCommit gitCommit = b1.getAction(GitCommit.class);
        assertThat(gitCommit).isNotNull();
        assertThat(gitCommit.getGitCommitLog().getRevisions()).hasSize(1);

        sampleRepo.git("checkout", "-b", "feature");
        sampleRepo.write("Jenkinsfile", "echo \"branch=${env.BRANCH_NAME}\"; node {checkout scm; echo readFile('file').toUpperCase(); echo \"GitForensics\"; gitForensics maxCommits: 2, newestBuildIfNotFound: true}");
        sampleRepo.write("file", "subsequent content");
        sampleRepo.git("commit", "--all", "--message=tweaked");
        // Second Commit
        sampleRepo.write("test.txt", "Test");
        sampleRepo.git("add", "test.txt");
        sampleRepo.git("commit", "--all", "--message=test");
        p = scheduleAndFindBranchProject(mp, "feature");
        assertThat(mp.getItems()).hasSize(2);
        r.waitUntilNoActivity();
        b1 = p.getLastBuild();
        assertThat(b1.getNumber()).isEqualTo(1);
        r.assertLogContains("SUBSEQUENT CONTENT", b1);
        r.assertLogContains("branch=feature", b1);

        // Check this plugin
        gitCommit = b1.getAction(GitCommit.class);
        assertThat(gitCommit).isNotNull();
        assertThat(gitCommit.getGitCommitLog().getRevisions()).hasSize(5);
        // Found correct intersection?
        BranchMasterIntersectionFinder finder = b1.getAction(BranchMasterIntersectionFinder.class);
        assertThat(finder).isNotNull();
        assertThat(finder.getBuildId()).isEqualTo("p/master#2");
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
            assertThat(mp).isEqualTo(source.getOwner());
        }
        WorkflowJob p = scheduleAndFindBranchProject(mp, "master");
        assertThat(new GitBranchSCMHead("master")).isEqualTo(SCMHead.HeadByItem.findHead(p));
        assertThat(mp.getItems()).hasSize(1);
        r.waitUntilNoActivity();
        WorkflowRun b1 = p.getLastBuild();
        assertThat(b1.getNumber()).isEqualTo(1);
        r.assertLogContains("initial content", b1);
        r.assertLogContains("branch=master", b1);

        // Check this plugin
        GitCommit gitCommit = b1.getAction(GitCommit.class);
        assertThat(gitCommit).isNotNull();
        assertThat(gitCommit.getGitCommitLog().getRevisions()).hasSize(2);

        sampleRepo.git("checkout", "-b", "feature");
        sampleRepo.write("Jenkinsfile", "echo \"branch=${env.BRANCH_NAME}\"; node {checkout scm; echo readFile('file').toUpperCase(); echo \"GitForensics\"; gitForensics()}");
        sampleRepo.write("file", "subsequent content");
        sampleRepo.git("commit", "--all", "--message=tweaked");
        p = scheduleAndFindBranchProject(mp, "feature");
        assertThat(mp.getItems()).hasSize(2);
        r.waitUntilNoActivity();
        b1 = p.getLastBuild();
        assertThat(b1.getNumber()).isEqualTo(1);
        r.assertLogContains("SUBSEQUENT CONTENT", b1);
        r.assertLogContains("branch=feature", b1);

        // Check this plugin
        gitCommit = b1.getAction(GitCommit.class);
        assertThat(gitCommit).isNotNull();
        assertThat(gitCommit.getGitCommitLog().getRevisions()).hasSize(3);

        // Now the second branch
        sampleRepo.git("checkout", "-b", "feature2");
        sampleRepo.write("test.txt", "my second feature");
        sampleRepo.git("add", "test.txt");
        sampleRepo.git("commit", "--all", "--message=secondFeature");
        p = scheduleAndFindBranchProject(mp, "feature2");
        assertThat(mp.getItems()).hasSize(3);
        r.waitUntilNoActivity();
        b1 = p.getLastBuild();
        assertThat(b1.getNumber()).isEqualTo(1);
        // should also contain the log from the first feature branch
        r.assertLogContains("SUBSEQUENT CONTENT", b1);
        r.assertLogContains("branch=feature2", b1);

        // Found correct intersection? (master and not the first feature branch)
        BranchMasterIntersectionFinder finder = b1.getAction(BranchMasterIntersectionFinder.class);
        assertThat(finder).isNotNull();
        assertThat("p/master#1").isEqualTo(finder.getBuildId());
    }

    /**
     * Tests if the Intersection point is not found if the build is deleted.
     * @throws Exception
     */
    @Test
    public void shouldNotFindIntersectionIfBuildWasDeleted() throws Exception {
        sampleRepo.init();
        sampleRepo.write("Jenkinsfile", "echo \"branch=${env.BRANCH_NAME}\"; node {checkout scm; echo readFile('file'); echo \"GitForensics\"; gitForensics()}");
        sampleRepo.write("file", "initial content");
        sampleRepo.git("add", "Jenkinsfile");
        sampleRepo.git("commit", "--all", "--message=flow");
        WorkflowMultiBranchProject mp = r.jenkins.createProject(WorkflowMultiBranchProject.class, "p");
        mp.getSourcesList().add(new BranchSource(new GitSCMSource(null, sampleRepo.toString(), "", "*", "", false), new DefaultBranchPropertyStrategy(new BranchProperty[0])));
        for (SCMSource source : mp.getSCMSources()) {
            assertThat(mp).isEqualTo(source.getOwner());
        }
        WorkflowJob p = scheduleAndFindBranchProject(mp, "master");
        assertThat(new GitBranchSCMHead("master")).isEqualTo(SCMHead.HeadByItem.findHead(p));
        assertThat(mp.getItems()).hasSize(1);
        r.waitUntilNoActivity();
        WorkflowRun toDelete = p.getLastBuild();
        String toDeleteId =  toDelete.getExternalizableId();
        assertThat(toDelete.getNumber()).isEqualTo(1);
        r.assertLogContains("initial content", toDelete);
        r.assertLogContains("branch=master", toDelete);

        // Check this plugin
        GitCommit gitCommit = toDelete.getAction(GitCommit.class);
        assertThat(gitCommit).isNotNull();
        assertThat(gitCommit.getGitCommitLog().getRevisions()).hasSize(2);

        sampleRepo.git("checkout", "-b", "feature");
        sampleRepo.write("Jenkinsfile", "echo \"branch=${env.BRANCH_NAME}\"; node {checkout scm; echo readFile('file').toUpperCase(); echo \"GitForensics\"; gitForensics()}");
        sampleRepo.write("file", "subsequent content");
        sampleRepo.git("commit", "--all", "--message=tweaked");

        // New master commits
        sampleRepo.git("checkout", "master");
        sampleRepo.write("testfile.txt", "testfile");
        sampleRepo.git("add", "testfile.txt");
        sampleRepo.git("commit", "--all", "--message=testfile");

        // Second master build
        p.scheduleBuild2(0).get();
        assertThat(mp.getItems()).hasSize(1);
        r.waitUntilNoActivity();
        WorkflowRun b1 = p.getLastBuild();
        assertThat(b1.getNumber()).isEqualTo(2);
        r.assertLogContains("branch=master", b1);

        // Check this plugin
        gitCommit = b1.getAction(GitCommit.class);
        assertThat(gitCommit).isNotNull();
        assertThat(gitCommit.getGitCommitLog().getRevisions()).hasSize(1);

        // Now delete Build before the feature branch is build.
        WorkflowRun run = (WorkflowRun) Run.fromExternalizableId(toDeleteId);
        run.delete();

        p = scheduleAndFindBranchProject(mp, "feature");
        assertThat(mp.getItems()).hasSize(2);
        r.waitUntilNoActivity();
        b1 = p.getLastBuild();
        assertThat(b1.getNumber()).isEqualTo(1);
        r.assertLogContains("SUBSEQUENT CONTENT", b1);
        r.assertLogContains("branch=feature", b1);

        // Check this plugin
        gitCommit = b1.getAction(GitCommit.class);
        assertThat(gitCommit).isNotNull();
                assertThat(gitCommit.getGitCommitLog().getRevisions()).hasSize(3);
        // Found correct intersection?
        BranchMasterIntersectionFinder finder = b1.getAction(BranchMasterIntersectionFinder.class);
        assertThat(finder).isNotNull();
        assertThat(GitBranchMasterIntersectionFinder.NO_INTERSECTION_FOUND).isEqualTo(finder.getBuildId());
    }

    /**
     * If the Intersection point is not found due to the build being deleted the newest master build should be taken with newestBuildIfNotFound enabled.
     * @throws Exception
     */
    @Test
    public void shouldTakeNewestMasterBuildIfBuildWasDeletedAndNewestBuildIfNotFoundIsEnabled() throws Exception {
        sampleRepo.init();
        sampleRepo.write("Jenkinsfile", "echo \"branch=${env.BRANCH_NAME}\"; node {checkout scm; echo readFile('file'); echo \"GitForensics\"; gitForensics newestBuildIfNotFound: true}");
        sampleRepo.write("file", "initial content");
        sampleRepo.git("add", "Jenkinsfile");
        sampleRepo.git("commit", "--all", "--message=flow");
        WorkflowMultiBranchProject mp = r.jenkins.createProject(WorkflowMultiBranchProject.class, "p");
        mp.getSourcesList().add(new BranchSource(new GitSCMSource(null, sampleRepo.toString(), "", "*", "", false), new DefaultBranchPropertyStrategy(new BranchProperty[0])));
        for (SCMSource source : mp.getSCMSources()) {
            assertThat(mp).isEqualTo(source.getOwner());
        }
        WorkflowJob p = scheduleAndFindBranchProject(mp, "master");
        assertThat(new GitBranchSCMHead("master")).isEqualTo(SCMHead.HeadByItem.findHead(p));
        assertThat(mp.getItems()).hasSize(1);
        r.waitUntilNoActivity();
        WorkflowRun toDelete = p.getLastBuild();
        String toDeleteId =  toDelete.getExternalizableId();
        assertThat(toDelete.getNumber()).isEqualTo(1);
        r.assertLogContains("initial content", toDelete);
        r.assertLogContains("branch=master", toDelete);

        // Check this plugin
        GitCommit gitCommit = toDelete.getAction(GitCommit.class);
        assertThat(gitCommit).isNotNull();
        assertThat(gitCommit.getGitCommitLog().getRevisions()).hasSize(2);

        sampleRepo.git("checkout", "-b", "feature");
        sampleRepo.write("Jenkinsfile", "echo \"branch=${env.BRANCH_NAME}\"; node {checkout scm; echo readFile('file').toUpperCase(); echo \"GitForensics\"; gitForensics newestBuildIfNotFound: true}");
        sampleRepo.write("file", "subsequent content");
        sampleRepo.git("commit", "--all", "--message=tweaked");

        // New master commits
        sampleRepo.git("checkout", "master");
        sampleRepo.write("testfile.txt", "testfile");
        sampleRepo.git("add", "testfile.txt");
        sampleRepo.git("commit", "--all", "--message=testfile");

        // Second master build
        p.scheduleBuild2(0).get();
        assertThat(mp.getItems()).hasSize(1);
        r.waitUntilNoActivity();
        WorkflowRun b1 = p.getLastBuild();
        assertThat(b1.getNumber()).isEqualTo(2);
        r.assertLogContains("branch=master", b1);

        // Check this plugin
        gitCommit = b1.getAction(GitCommit.class);
        assertThat(gitCommit).isNotNull();
        assertThat(gitCommit.getGitCommitLog().getRevisions()).hasSize(1);

        // Now delete Build before the feature branch is build.
        WorkflowRun run = (WorkflowRun) Run.fromExternalizableId(toDeleteId);
        run.delete();

        WorkflowJob p2 = scheduleAndFindBranchProject(mp, "feature");
        assertThat(mp.getItems()).hasSize(2);
        r.waitUntilNoActivity();
        b1 = p2.getLastBuild();
        assertThat(b1.getNumber()).isEqualTo(1);
        r.assertLogContains("SUBSEQUENT CONTENT", b1);
        r.assertLogContains("branch=feature", b1);

        // Check this plugin
        gitCommit = b1.getAction(GitCommit.class);
        assertThat(gitCommit).isNotNull();
                assertThat(gitCommit.getGitCommitLog().getRevisions()).hasSize(3);
        // Found correct intersection?
        BranchMasterIntersectionFinder finder = b1.getAction(BranchMasterIntersectionFinder.class);
        assertThat(finder).isNotNull();
        assertThat(p.getLastBuild().getExternalizableId()).isEqualTo(finder.getBuildId());
    }

    public static WorkflowJob scheduleAndFindBranchProject( WorkflowMultiBranchProject mp,  String name) throws Exception {
        mp.scheduleBuild2(0).getFuture().get();
        return findBranchProject(mp, name);
    }

    public static  WorkflowJob findBranchProject( WorkflowMultiBranchProject mp,  String name) throws Exception {
        WorkflowJob p = mp.getItem(name);
        showIndexing(mp);
        if (p == null) {
            fail(name + " project not found");
        }
        return p;
    }

    static void showIndexing( WorkflowMultiBranchProject mp) throws Exception {
        FolderComputation<?> indexing = mp.getIndexing();
        System.out.println("---%<--- " + indexing.getUrl());
        indexing.writeWholeLogTo(System.out);
        System.out.println("---%<--- ");
    }
}
