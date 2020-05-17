package io.jenkins.plugins.forensics.git.reference;

import edu.hm.hafner.util.FilteredLog;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.SCMListener;
import hudson.plugins.git.GitSCM;
import hudson.remoting.VirtualChannel;
import hudson.scm.SCM;
import hudson.scm.SCMRevisionState;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.LogCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.jenkinsci.plugins.gitclient.RepositoryCallback;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Called on Checkout of a Git Repository in Jenkins. This Class determines the Commits since the last Build
 * and writes them into a GitCommit RunAction to be accessed later.
 *
 * @author Arne Schöntag
 */
@Extension
@SuppressWarnings("unused")
public class GitCommitListener extends SCMListener {

    @Override
    public void onCheckout(final Run<?, ?> build, final SCM scm, final FilePath workspace, final TaskListener listener, final File changelogFile, final SCMRevisionState pollingBaseline) throws Exception {
        if (!(scm instanceof GitSCM)) {
            return;
        }
        /* Looking for the latest revision (Commit) on the previous build.
         * If the previous build has no commits (f.e. manual triggered) then it will looked further
         * in the past until one is found or there is no previous build.
         */
        String latestRevisionOfPreviousCommit = null;
        Run previous = build.getPreviousBuild();
        while (previous != null && latestRevisionOfPreviousCommit == null) {
            io.jenkins.plugins.git.forensics.reference.GitCommit gitCommit = previous.getAction(
                    io.jenkins.plugins.git.forensics.reference.GitCommit.class);
            if (gitCommit == null) {
                break;
            }
            if (gitCommit.getRevisions().isEmpty()) {
                previous = previous.getPreviousBuild();
            }
            else {
                latestRevisionOfPreviousCommit = gitCommit.getLatestRevision();
            }
        }

        // Build Repo
        GitSCM gitSCM = (GitSCM) scm;
        EnvVars environment = build.getEnvironment(listener);
        GitClient gitClient = gitSCM.createClient(listener, environment, build, workspace);

        // Save new commits
        io.jenkins.plugins.git.forensics.reference.GitCommit gitCommit = gitClient.withRepository(new GitCommitCall(build, latestRevisionOfPreviousCommit, gitSCM.getKey()));
        if (gitCommit != null) {
            build.addAction(gitCommit);
        }
    }

    /**
     * Writes the Commits since last build into a GitCommit object.
     */
    static class GitCommitCall implements RepositoryCallback<io.jenkins.plugins.git.forensics.reference.GitCommit> {

        private static final long serialVersionUID = -5980402198857923793L;
        private final transient Run<?, ?> build;
        private final String latestRevisionOfPreviousCommit;

        private final FilteredLog log = createLog();
        private final String repositoryKey;

        GitCommitCall(final Run<?, ?> build, final String latestRevisionOfPreviousCommit, String repositoryKey) {
            this.build = build;
            this.latestRevisionOfPreviousCommit = latestRevisionOfPreviousCommit;
            this.repositoryKey = repositoryKey;
        }

        @Override
        public io.jenkins.plugins.git.forensics.reference.GitCommit invoke(final Repository repo, final VirtualChannel channel) throws IOException {
            // Check if GitCommit of Repository already Exists
            List<io.jenkins.plugins.git.forensics.reference.GitCommit> gitCommits = build.getActions(io.jenkins.plugins.git.forensics.reference.GitCommit.class);
            if (gitCommits.stream().anyMatch(gitCommit -> repositoryKey.equals(gitCommit.getRepositoryId()))) {
                return null;
            }

            io.jenkins.plugins.git.forensics.reference.GitCommit result = new io.jenkins.plugins.git.forensics.reference.GitCommit(build, repositoryKey);
            List<String> newCommits = new ArrayList<>();
            try (Git git = new Git(repo)) {
                // Determine new commits to log since last build
                RevWalk walk = new RevWalk(repo);
                ObjectId head = repo.resolve(Constants.HEAD);
                ObjectId headCommit = walk.parseCommit(head);
                LogCommand logCommand = git.log().add(headCommit);
                Iterable<RevCommit> commits;
                commits = logCommand.call();
                Iterator<RevCommit> iterator = commits.iterator();
                RevCommit next;
                while (iterator.hasNext()) {
                    next = iterator.next();
                    String commitId = next.getId().toString();
                    // If latestRevisionOfPreviousCommit is null, all commits will be added.
                    if (commitId.equals(latestRevisionOfPreviousCommit)) {
                        break;
                    }
                    newCommits.add(commitId);
                }
            }
            catch (GitAPIException e) {
                log.logException(e, "Unable to call log command on git repository.");
            }

            result.getGitCommitLog().addRevisions(newCommits);
            return result;
        }

        private FilteredLog createLog() {
            return new FilteredLog("Errors while extracting commit revision information from Git:");
        }
    }
}
