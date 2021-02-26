package io.jenkins.plugins.forensics.git.miner;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;

import edu.hm.hafner.util.FilteredLog;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import org.jenkinsci.plugins.gitclient.GitClient;
import hudson.remoting.VirtualChannel;

import io.jenkins.plugins.forensics.git.util.AbstractRepositoryCallback;
import io.jenkins.plugins.forensics.git.util.RemoteResultWrapper;
import io.jenkins.plugins.forensics.miner.CommitDiffItem;
import io.jenkins.plugins.forensics.miner.CommitStatistics;
import io.jenkins.plugins.forensics.miner.RepositoryMiner;
import io.jenkins.plugins.forensics.miner.RepositoryStatistics;

/**
 * Mines a Git repository and creates statistics for all available files.
 *
 * @author Giulia Del Bravo
 * @author Ullrich Hafner
 * @see io.jenkins.plugins.forensics.miner.RepositoryStatistics
 * @see io.jenkins.plugins.forensics.miner.FileStatistics
 * @see CommitDiffItem
 */
@SuppressFBWarnings(value = "SE", justification = "GitClient implementation is Serializable")
public class GitRepositoryMiner extends RepositoryMiner {
    private static final long serialVersionUID = 1157958118716013983L;

    private final GitClient gitClient;

    GitRepositoryMiner(final GitClient gitClient) {
        super();

        this.gitClient = gitClient;
    }

    // TODO: we need to create the new results separately to compute the added and deleted lines per build
    @Override
    public RepositoryStatistics mine(final RepositoryStatistics previous, final FilteredLog logger)
            throws InterruptedException {
        try {
            long nano = System.nanoTime();
            logger.logInfo("Analyzing the commit log of the Git repository '%s'",
                    gitClient.getWorkTree());
            RemoteResultWrapper<ArrayList<CommitDiffItem>> wrapped = gitClient.withRepository(
                    new RepositoryStatisticsCallback(previous.getLatestCommitId()));
            logger.merge(wrapped);

            List<CommitDiffItem> commits = wrapped.getResult();
            logger.logInfo("-> created report in %d seconds", 1 + (System.nanoTime() - nano) / 1_000_000_000L);
            CommitStatistics.logCommits(commits, logger);

            String latestCommitId;
            if (commits.isEmpty()) {
                latestCommitId = previous.getLatestCommitId();
            }
            else {
                latestCommitId = commits.get(0).getId();
            }
            RepositoryStatistics current = new RepositoryStatistics(latestCommitId);
            current.addAll(previous);
            Collections.reverse(commits); // make sure that we start with old commits to preserve the history
            current.addAll(commits);
            return current;
        }
        catch (IOException exception) {
            logger.logException(exception,
                    "Exception occurred while mining the Git repository using GitClient");
            return new RepositoryStatistics();
        }
    }

    private static class RepositoryStatisticsCallback
            extends AbstractRepositoryCallback<RemoteResultWrapper<ArrayList<CommitDiffItem>>> {
        private static final long serialVersionUID = 7667073858514128136L;

        private final String previousCommitId;

        RepositoryStatisticsCallback(final String previousCommitId) {
            super();

            this.previousCommitId = previousCommitId;
        }

        @Override @SuppressWarnings("PMD.UseTryWithResources")
        public RemoteResultWrapper<ArrayList<CommitDiffItem>> invoke(
                final Repository repository, final VirtualChannel channel) {
            List<CommitDiffItem> commits = new ArrayList<>();
            RemoteResultWrapper<ArrayList<CommitDiffItem>> wrapper = new RemoteResultWrapper<>(
                    commits, "Errors while mining the Git repository:");

            try {
                try (Git git = new Git(repository)) {
                    CommitAnalyzer commitAnalyzer = new CommitAnalyzer();
                    commits.addAll(commitAnalyzer.run(repository, git, previousCommitId, wrapper));
                }
                catch (IOException | GitAPIException exception) {
                    wrapper.logException(exception,
                            "Can't analyze commits for the repository " + repository.getIdentifier());
                }
            }
            finally {
                repository.close();
            }

            return wrapper;
        }
    }
}
