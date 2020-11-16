package io.jenkins.plugins.forensics.git.reference;

import hudson.model.FreeStyleProject;

import java.io.IOException;

import hudson.plugins.git.GitSCM;
import org.junit.Test;

import hudson.model.Run;
import io.jenkins.plugins.forensics.git.util.GitITest;
import io.jenkins.plugins.forensics.reference.ReferenceBuild;

import static io.jenkins.plugins.forensics.assertions.Assertions.*;
import static io.jenkins.plugins.forensics.git.assertions.Assertions.*;

public class GitParentCommitITest extends GitITest {

    @Test
    public void testSingleBranch() throws IOException {
        // Commit A
        String commitA = getHead();
        FreeStyleProject job = createFreeStyleProject("SingleBranch");

        // Build 1: [A]
        Run<?, ?> build1 = buildSuccessfully(job);
        assertThat(build1.getAction(GitCommitsRecord.class)).isNotNull()
                .hasCommits(commitA)
                .hasLatestCommit(commitA);
        assertThat(build1.getAction(ReferenceBuild.class)).isNotNull()
                .hasOwner(build1)
                .doesNotHaveReferenceBuild();

        // Commit B
        writeFileAsAuthorBar("Commit B in master branch");
        String commitB = getHead();

        // Build 2: [B]
        Run<?, ?> build2 = buildSuccessfully(job);
        assertThat(build2.getAction(GitCommitsRecord.class)).isNotNull()
                .hasCommits(commitB)
                .hasLatestCommit(commitB);
        assertThat(build2.getAction(ReferenceBuild.class)).isNotNull()
                .hasReferenceBuildId(build1.getExternalizableId());

        // Build 3: [B]
        // build3 should have the same parent commit as the previous build, as no new
        // commits have been made
        Run<?, ?> build3 = buildSuccessfully(job);
        assertThat(build3.getAction(GitCommitsRecord.class)).isNotNull()
                .hasNoCommits()
                .hasLatestCommit(commitB);
        assertThat(build3.getAction(ReferenceBuild.class)).isNotNull()
                .hasReferenceBuildId(build1.getExternalizableId());
    }
    
    @Test
    public void testFeatureBranch() throws IOException {
        // master: Commit A
        String commitA = getHead();
        FreeStyleProject job = createFreeStyleProject("MultiBranch");

        // Build 1: [A]
        Run<?, ?> build1 = buildSuccessfully(job);

        // feature: Commit A
        checkoutNewBranch("feature");
        // feature: Commit B
        writeFileAsAuthorBar("Commit B in feature branch");
        String commitB = getHead();

        // Build 2: [B]
        Run<?, ?> build2 = buildSuccessfully(job);
        assertThat(build2.getAction(GitCommitsRecord.class)).isNotNull().hasCommits(commitB).hasLatestCommit(commitB);
        assertThat(build2.getAction(ReferenceBuild.class)).isNotNull()
                .hasReferenceBuildId(build1.getExternalizableId());

        // master: Commit A
        checkout("master");
        // master: Commit C
        writeFileAsAuthorBar("Commit C in master branch");
        String commitC = getHead();

        // Build 3: [A, C]
        Run<?, ?> build3 = buildSuccessfully(job);
        assertThat(build3.getAction(GitCommitsRecord.class)).isNotNull().hasCommits(commitA, commitC)
                .hasLatestCommit(commitC);
        assertThat(build3.getAction(ReferenceBuild.class)).isNotNull()
                .hasReferenceBuildId(build1.getExternalizableId());
    }

    @Test
    public void testMultiBranch2() throws IOException {
        /*
         * - commit A master - checkout new branch feature - checkout master - commit B
         * master - build master (#1, contains commits A and B) - checkout feature -
         * commit C feature - build feature #2
         */

        // Commit A
        String commitA = getHead();
        FreeStyleProject job = createFreeStyleProject("MultiBranchTwo");

        checkoutNewBranch("feature");

        checkout("master");
        // Commit B
        writeFileAsAuthorBar("Commit B in master branch");

        Run<?, ?> build1 = buildSuccessfully(job);
        // master has commits [A, B] , but only one build -> no reference build
        assertThat(build1.getAction(GitCommitsRecord.class)).isNotNull();
        assertThat(build1.getAction(ReferenceBuild.class)).isNotNull().hasOwner(build1).doesNotHaveReferenceBuild();

        checkout("feature");
        // Commit C
        writeFileAsAuthorBar("Commit C in feature branch");
        Run<?, ?> build2 = buildSuccessfully(job);
        // build #1 contains the parent commit -> the build #1 is reference build
        assertThat(build2.getAction(GitCommitsRecord.class)).isNotNull();
        assertThat(build2.getAction(ReferenceBuild.class)).isNotNull().hasOwner(build2).doesNotHaveReferenceBuild();
    }

    private FreeStyleProject createFreeStyleProject(final String jobName) throws IOException {
        FreeStyleProject project = createProject(FreeStyleProject.class, jobName);

        GitReferenceRecorder recorder = new GitReferenceRecorder();
        recorder.setReferenceJob(jobName);

        project.getPublishersList().add(recorder);
        project.setScm(new GitSCM(sampleRepo.toString()));
        return project;
    }
}