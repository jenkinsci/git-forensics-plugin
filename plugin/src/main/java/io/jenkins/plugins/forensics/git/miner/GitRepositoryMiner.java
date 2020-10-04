package io.jenkins.plugins.forensics.git.miner;

import java.io.IOException;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;

import edu.hm.hafner.util.FilteredLog;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import org.jenkinsci.plugins.gitclient.GitClient;
import hudson.remoting.VirtualChannel;

import io.jenkins.plugins.forensics.git.util.AbstractRepositoryCallback;
import io.jenkins.plugins.forensics.git.util.RemoteResultWrapper;
import io.jenkins.plugins.forensics.miner.RepositoryMiner;
import io.jenkins.plugins.forensics.miner.RepositoryStatistics;

/**
 * Mines a Git repository and creates statistics for all available files.
 *
 * @author Ullrich Hafner
 * @see io.jenkins.plugins.forensics.miner.FileStatistics
 * @see FilesCollector
 */
@SuppressFBWarnings(value = "SE", justification = "GitClient implementation is Serializable")
public class GitRepositoryMiner extends RepositoryMiner {
    private static final long serialVersionUID = 1157958118716013983L;

    private final GitClient gitClient;

    GitRepositoryMiner(final GitClient gitClient) {
        super();

        this.gitClient = gitClient;
    }

    @Override
    public RepositoryStatistics mine(final RepositoryStatistics previousStatistics, final FilteredLog logger)
            throws InterruptedException {
        try {
            long nano = System.nanoTime();
            logger.logInfo("Analyzing the commit log of the Git repository '%s'",
                    gitClient.getWorkTree());
            RepositoryStatisticsCallback callback = new RepositoryStatisticsCallback(
                    previousStatistics.getLatestCommitId());
            RemoteResultWrapper<RepositoryStatistics> wrapped = gitClient.withRepository(
                    callback);
            wrapped.getInfoMessages().forEach(logger::logInfo);

            RepositoryStatistics statistics = wrapped.getResult();
            logger.logInfo("-> created report for %d files in %d seconds", statistics.size(),
                    1 + (System.nanoTime() - nano) / 1_000_000_000L);
            statistics.addAll(previousStatistics);

            return statistics;
        }
        catch (IOException exception) {
            logger.logException(exception,
                    "Exception occurred while mining the Git repository using GitClient");
            return new RepositoryStatistics();
        }
    }

    private static class RepositoryStatisticsCallback
            extends AbstractRepositoryCallback<RemoteResultWrapper<RepositoryStatistics>> {
        private static final long serialVersionUID = 7667073858514128136L;

        private final String previousCommitId;

        RepositoryStatisticsCallback(final String previousCommitId) {
            super();

            this.previousCommitId = previousCommitId;
        }

        @Override
        public RemoteResultWrapper<RepositoryStatistics> invoke(
                final Repository repository, final VirtualChannel channel) {
            RepositoryStatistics currentStatistics = createEmptyStatisticsFromHead(repository);
            RemoteResultWrapper<RepositoryStatistics> wrapper = new RemoteResultWrapper<>(
                    currentStatistics, "Errors while mining the Git repository:");

            try {
                try (Git git = new Git(repository)) {
                    CommitAnalyzer commitAnalyzer = new CommitAnalyzer();
                    commitAnalyzer.run(repository, git, currentStatistics, previousCommitId, wrapper);
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

        private RepositoryStatistics createEmptyStatisticsFromHead(final Repository repository) {
            try {
                ObjectId headId = repository.resolve(Constants.HEAD);
                if (headId != null) {
                    return new RepositoryStatistics(headId.getName());
                }
            }
            catch (IOException exception) {
                // ignore
            }
            return new RepositoryStatistics();
        }
    }
}
