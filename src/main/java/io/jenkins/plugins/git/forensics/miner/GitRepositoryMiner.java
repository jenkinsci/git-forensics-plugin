package io.jenkins.plugins.git.forensics.miner;

import java.io.IOException;
import java.nio.file.LinkOption;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.dircache.InvalidPathException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;

import edu.umd.cs.findbugs.annotations.Nullable;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import org.jenkinsci.plugins.gitclient.GitClient;
import org.jenkinsci.plugins.gitclient.RepositoryCallback;
import hudson.FilePath;
import hudson.remoting.VirtualChannel;

import io.jenkins.plugins.forensics.miner.FileStatistics;
import io.jenkins.plugins.forensics.miner.RepositoryMiner;
import io.jenkins.plugins.forensics.miner.RepositoryStatistics;

/**
 * Mines a Git repository and creates statistics for all available files.
 *
 * @author Ullrich Hafner
 * @see io.jenkins.plugins.forensics.miner.FileStatistics
 * @see io.jenkins.plugins.git.forensics.miner.FilesCollector
 */
@SuppressFBWarnings(value = "SE", justification = "GitClient implementation is Serializable")
public class GitRepositoryMiner extends RepositoryMiner {
    private static final long serialVersionUID = 1157958118716013983L;

    private final GitClient gitClient;
    private final FilePath workspace;

    GitRepositoryMiner(final GitClient gitClient) {
        this.gitClient = gitClient;
        workspace = gitClient.getWorkTree();
    }

    @Override
    public RepositoryStatistics mine(final Collection<String> paths) throws InterruptedException {
        try {
            String workspacePath = getWorkspacePath();

            return gitClient.withRepository(new RepositoryStatisticsCallback(workspacePath, paths));
        }
        catch (IOException exception) {
            RepositoryStatistics statistics = new RepositoryStatistics();
            statistics.logException(exception, "Exception occurred while mining the Git repository using GitClient");
            return statistics;
        }
    }

    private String getWorkspacePath() {
        try {
            return Paths.get(workspace.getRemote()).toAbsolutePath().normalize().toRealPath(LinkOption.NOFOLLOW_LINKS).toString();
        }
        catch (IOException | InvalidPathException exception) {
            return workspace.getRemote();
        }
    }

    private static class RepositoryStatisticsCallback implements RepositoryCallback<RepositoryStatistics> {
        private static final long serialVersionUID = 7667073858514128136L;
        private final String workspacePath;
        private final Collection<String> paths;

        RepositoryStatisticsCallback(final String workspacePath, final Collection<String> paths) {
            this.workspacePath = workspacePath;
            this.paths = paths;
        }

        @Override
        public RepositoryStatistics invoke(final Repository repository, final VirtualChannel channel) {
            try {
                if (paths.isEmpty()) { // scan whole repository
                    ObjectId head = repository.resolve(Constants.HEAD);
                    Set<String> files = new FilesCollector(repository).findAllFor(head);
                    return analyze(repository, files);
                }
                return analyze(repository, paths);
            }
            catch (IOException exception) {
                RepositoryStatistics statistics = new RepositoryStatistics();
                statistics.logException(exception, "Can't obtain HEAD of repository.");
                return statistics;
            }
            finally {
                repository.close();
            }
        }

        RepositoryStatistics analyze(final Repository repository, final Collection<String> files) {
            RepositoryStatistics statistics = new RepositoryStatistics();
            statistics.logInfo("Invoking Git miner to create creates statistics for all available files");

            List<FileStatistics> fileStatistics = files.stream()
                    .map(file -> analyzeHistory(repository, file, statistics))
                    .collect(Collectors.toList());
            statistics.addAll(fileStatistics);

            statistics.logInfo("-> created statistics for %d files", statistics.size());

            return statistics;
        }

        private FileStatistics analyzeHistory(final Repository repository, final String fileName,
                final RepositoryStatistics statistics) {
            FileStatistics fileStatistics = new FileStatistics(workspacePath + "/" + fileName);

            try {
                Git git = new Git(repository);
                Iterable<RevCommit> commits = git.log().addPath(fileName).call();
                commits.forEach(c -> fileStatistics.inspectCommit(c.getCommitTime(), getAuthor(c)));
                return fileStatistics;
            }
            catch (GitAPIException exception) {
                statistics.logException(exception, "Can't analyze history of file %s", fileName);
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
