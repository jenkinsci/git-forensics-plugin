package io.jenkins.plugins.forensics.git.reference;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.LogCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

import edu.hm.hafner.util.FilteredLog;

import org.jenkinsci.plugins.gitclient.GitClient;
import org.jenkinsci.plugins.gitclient.RepositoryCallback;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.SCMListener;
import hudson.remoting.VirtualChannel;
import hudson.scm.SCM;
import hudson.scm.SCMRevisionState;

import io.jenkins.plugins.forensics.git.util.GitRepositoryValidator;
import io.jenkins.plugins.util.LogHandler;

/**
 * Determines all commits since the last build and writes them into a {@link GitCommit} action to be accessed later.
 * This listener is called on every checkout of a Git Repository in a Jenkins build.
 *
 * @author Arne Schöntag
 */
@Extension
@SuppressWarnings("unused")
public class GitCommitListener extends SCMListener {
    @Override
    public void onCheckout(final Run<?, ?> build, final SCM scm, final FilePath workspace,
            final TaskListener listener, final File changelogFile, final SCMRevisionState pollingBaseline)
            throws IOException, InterruptedException {
        FilteredLog logger = new FilteredLog("Git commit listener errors:");

        List<GitCommit> gitCommits = build.getActions(GitCommit.class);
        if (gitCommits.stream().noneMatch(gitCommit -> scm.getKey().equals(gitCommit.getScmKey()))) {
            GitRepositoryValidator validator = new GitRepositoryValidator(scm, build, workspace, listener, logger);
            if (validator.isGitRepository()) {
                logger.logInfo("Recording commits of in working tree '%s'", workspace);
                recordCommits(build, validator.createClient(), scm.getKey(), logger);
            }
        }
        else {
            logger.logInfo("Skipping recording, since SCM `%s` already has been processed", scm.getKey());
        }
        LogHandler logHandler = new LogHandler(listener, "GitCommitsRecorder");
        logHandler.log(logger);
    }

    private void recordCommits(final Run<?, ?> build, final GitClient gitClient, final String key,
            final FilteredLog logger) throws IOException, InterruptedException {
        String previousCommit = getPreviousCommit(build, logger);
        List<String> commits = gitClient.withRepository(new GitCommitCall(previousCommit, logger));
        logger.logInfo("-> Recorded %d new commits", commits.size());
        GitCommit gitCommit = new GitCommit(build, key, commits, logger);
        build.addAction(gitCommit);
    }

    /**
     * Determines the latest commit of the previous build. If the previous build has no commits (e.g., the build
     * was manually triggered) then the history of builds is inspected until one is found (or no such build exists).
     *
     * @param currentBuild
     *         the build to start the search with
     * @param logger
     *         logs the result
     */
    private String getPreviousCommit(final Run<?, ?> currentBuild, final FilteredLog logger) {
        for (Run<?, ?> build = currentBuild.getPreviousBuild(); build != null; build = build.getPreviousBuild()) {
            GitCommit gitCommit = build.getAction(GitCommit.class);
            if (gitCommit != null && gitCommit.isNotEmpty()) {
                logger.logInfo("Found previous build `%s` that contains recorded Git commits", build);
                String latestCommitName = gitCommit.getLatestCommitName();
                logger.logInfo("-> Latest recorded commit SHA-1: %s", latestCommitName);
                logger.logInfo("Starting recording of new commits");
                return latestCommitName;
            }
        }
        logger.logInfo("Found no previous build with recorded Git commits - starting initial recording");
        return StringUtils.EMPTY;
    }

    /**
     * Writes the Commits since last build into a GitCommit object.
     */
    static class GitCommitCall implements RepositoryCallback<List<String>> {
        private static final long serialVersionUID = -5980402198857923793L;
        private static final int MAX_COMMITS = 200;

        private final String latestRecordedCommit;
        private final FilteredLog logger;

        GitCommitCall(final String latestRecordedCommit, final FilteredLog logger) {
            this.latestRecordedCommit = latestRecordedCommit;
            this.logger = logger;
        }

        @Override
        public List<String> invoke(final Repository repo, final VirtualChannel channel) throws IOException {
            List<String> newCommits = new ArrayList<>();
            try (Git git = new Git(repo)) {
                // Determine new commits to log since last build
                ObjectId head = repo.resolve(Constants.HEAD);
                RevWalk walk = new RevWalk(repo);
                RevCommit headCommit = walk.parseCommit(head);
                LogCommand logCommand = git.log().add(headCommit);
                Iterable<RevCommit> commits = logCommand.call();
                for (RevCommit commit : commits) {
                    String commitId = commit.getName();
                    if (commitId.equals(latestRecordedCommit) || newCommits.size() >= MAX_COMMITS) {
                        return newCommits;
                    }
                    newCommits.add(commitId);
                }
            }
            catch (GitAPIException e) {
                logger.logException(e, "Unable to record commits of git repository.");
            }
            return newCommits;
        }

        private FilteredLog createLog() {
            return new FilteredLog("Errors while extracting commit revision information from Git:");
        }
    }
}
