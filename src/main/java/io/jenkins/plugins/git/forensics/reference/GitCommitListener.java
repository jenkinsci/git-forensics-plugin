package io.jenkins.plugins.git.forensics.reference;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.SCMListener;
import hudson.plugins.git.GitSCM;
import hudson.scm.SCM;
import hudson.scm.SCMRevisionState;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.LogCommand;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.jenkinsci.plugins.gitclient.GitClient;

import javax.annotation.CheckForNull;
import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Extension
public class GitCommitListener extends SCMListener {

    @Override
    public void onCheckout(Run<?,?> build, SCM scm, FilePath workspace, TaskListener listener, @CheckForNull File changelogFile, @CheckForNull SCMRevisionState pollingBaseline) throws Exception {
        // TODO reduce complexity
        if (!(scm instanceof GitSCM)) {
            return;
        }
        /* Looking for the latest Reversion (Commit) on the previous build.
         * If the previous build has no commits (f.e. manual triggered) then it will looked further
         * in the past until one is found or there is no previous build.
         */
        String latestReversionOfPreviousCommit = null;
        Run previous = build.getPreviousBuild();
        while (previous != null && latestReversionOfPreviousCommit == null) {
            GitCommit gitCommit = previous.getAction(GitCommit.class);
            if (gitCommit.getGitCommitLog().getReversions().isEmpty()) {
                previous = previous.getPreviousBuild();
            } else {
                latestReversionOfPreviousCommit = gitCommit.getGitCommitLog().getReversions().get(0);
            }
        }

        List<String> newCommits = new ArrayList<>();

        // Build Repo
        GitSCM gitSCM = (GitSCM) scm;
        EnvVars environment = build.getEnvironment(listener);
        GitClient gitClient = gitSCM.createClient(listener, environment, build, workspace);
        // TODO find not deprecated way?
        Repository repo = gitClient.getRepository();
        Git git = new Git(repo);

        // Determine new commits to log since last build
        String localBranch = gitSCM.getParamLocalBranch(build,listener);
        LogCommand logCommand = git.log().add(repo.resolve(localBranch));
        Iterable<RevCommit> commits = logCommand.call();
        Iterator<RevCommit> iterator = commits.iterator();
        RevCommit next;
        while (iterator.hasNext()) {
            next = iterator.next();
            String commitId = next.getId().toString();
            // If latestReversionOfPreviousCommit is null, all commits will be added.
            if (commitId.equals(latestReversionOfPreviousCommit)) {
                break;
            }
            newCommits.add(commitId);
        }

        // Save new commits
        GitCommit gitCommit = new GitCommit(build);
        gitCommit.getGitCommitLog().getReversions().addAll(newCommits);
        build.addAction(gitCommit);
    }

}
