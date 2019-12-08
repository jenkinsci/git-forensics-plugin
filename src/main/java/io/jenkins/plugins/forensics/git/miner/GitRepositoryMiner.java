package io.jenkins.plugins.forensics.git.miner;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;

import edu.hm.hafner.util.FilteredLog;
import edu.umd.cs.findbugs.annotations.Nullable;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import org.jenkinsci.plugins.gitclient.GitClient;
import hudson.remoting.VirtualChannel;

import io.jenkins.plugins.forensics.git.util.AbstractRepositoryCallback;
import io.jenkins.plugins.forensics.miner.FileStatistics;
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
    public RepositoryStatistics mine(final Collection<String> absoluteFileNames, final FilteredLog logger)
            throws InterruptedException {
        try {
            long nano = System.nanoTime();
            RepositoryStatistics statistics = gitClient.withRepository(
                    new RepositoryStatisticsCallback(absoluteFileNames, logger));
            logger.logInfo("Mining of the Git repository took %d seconds",
                    1 + (System.nanoTime() - nano) / 1_000_000_000L);
            return statistics;
        }
        catch (IOException exception) {
            RepositoryStatistics statistics = new RepositoryStatistics();
            logger.logException(exception, "Exception occurred while mining the Git repository using GitClient");
            return statistics;
        }
    }

    private static class RepositoryStatisticsCallback extends AbstractRepositoryCallback<RepositoryStatistics> {
        private static final long serialVersionUID = 7667073858514128136L;

        private final Collection<String> paths;
        private final FilteredLog logger;

        RepositoryStatisticsCallback(final Collection<String> paths, final FilteredLog logger) {
            super();

            this.paths = paths;
            this.logger = logger;
        }

        @Override
        public RepositoryStatistics invoke(final Repository repository, final VirtualChannel channel) {
            try {
                if (paths.isEmpty()) { // scan whole repository
                    ObjectId head = repository.resolve(Constants.HEAD);
                    if (head == null) {
                        RepositoryStatistics statistics = new RepositoryStatistics();
                        logger.logError("Can't obtain HEAD of repository.");
                        return statistics;
                    }
                    Set<String> files = new FilesCollector(repository).findAllFor(head);
                    return analyze(repository, files);
                }
                return analyze(repository, paths);
            }
            catch (IOException exception) {
                RepositoryStatistics statistics = new RepositoryStatistics();
                logger.logException(exception, "Can't obtain HEAD of repository.");
                return statistics;
            }
            finally {
                repository.close();
            }
        }

        RepositoryStatistics analyze(final Repository repository, final Collection<String> files) {
            RepositoryStatistics statistics = new RepositoryStatistics();
            logger.logInfo("Invoking Git miner to create statistics for all available files");
            logger.logInfo("Git working tree = '%s'", getWorkTree(repository));

            List<FileStatistics> fileStatistics = files.stream()
                    .map(file -> analyzeHistory(repository, file, statistics))
                    .collect(Collectors.toList());
            statistics.addAll(fileStatistics);

            logger.logInfo("-> created statistics for %d files", statistics.size());

            return statistics;
        }

        private FileStatistics analyzeHistory(final Repository repository, final String fileName,
                final RepositoryStatistics statistics) {
            FileStatistics fileStatistics = new FileStatistics(fileName);

            try (Git git = new Git(repository)) {
                Iterable<RevCommit> commits = git.log().addPath(getRelativePath(repository, fileName)).call();
                commits.forEach(c -> fileStatistics.inspectCommit(c.getCommitTime(), getAuthor(c)));
                return fileStatistics;
            }
            catch (GitAPIException exception) {
                logger.logException(exception, "Can't analyze history of file %s", fileName);
            }
            return fileStatistics;
        }

        @Nullable
        private String getAuthor(final RevCommit commit) {
            PersonIdent author = commit.getAuthorIdent();
            if (author != null) {
                return StringUtils.defaultString(author.getEmailAddress(), author.getName());
            }
            PersonIdent committer = commit.getCommitterIdent();
            if (committer != null) {
                return StringUtils.defaultString(committer.getEmailAddress(), committer.getName());
            }
            return StringUtils.EMPTY;
        }
    }
}
