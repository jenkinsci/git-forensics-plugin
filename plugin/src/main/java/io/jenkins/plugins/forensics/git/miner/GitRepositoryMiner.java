package io.jenkins.plugins.forensics.git.miner;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.EmptyTreeIterator;

import edu.hm.hafner.util.FilteredLog;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import org.jenkinsci.plugins.gitclient.GitClient;
import hudson.remoting.VirtualChannel;

import io.jenkins.plugins.forensics.git.util.AbstractRepositoryCallback;
import io.jenkins.plugins.forensics.git.util.RemoteResultWrapper;
import io.jenkins.plugins.forensics.miner.FileStatistics;
import io.jenkins.plugins.forensics.miner.FileStatistics.FileStatisticsBuilder;
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
@SuppressWarnings("PMD.ExcessiveImports")
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
            logger.logInfo("Analyzing the commit log of the Git repository '%s'", gitClient.getWorkTree());
            RemoteResultWrapper<RepositoryStatistics> wrapped = gitClient.withRepository(
                    new RepositoryStatisticsCallback(previousStatistics));

            wrapped.getInfoMessages().forEach(logger::logInfo);

            RepositoryStatistics statistics = wrapped.getResult();
            logger.logInfo("-> created report for %d files in %d seconds", statistics.size(),
                    1 + (System.nanoTime() - nano) / 1_000_000_000L);
            return statistics;
        }
        catch (IOException exception) {
            RepositoryStatistics statistics = new RepositoryStatistics();
            logger.logException(exception, "Exception occurred while mining the Git repository using GitClient");
            return statistics;
        }
    }

    private static class RepositoryStatisticsCallback
            extends AbstractRepositoryCallback<RemoteResultWrapper<RepositoryStatistics>> {
        private static final long serialVersionUID = 7667073858514128136L;

        private final RepositoryStatistics previousStatistics;

        RepositoryStatisticsCallback(final RepositoryStatistics previousStatistics) {
            super();

            this.previousStatistics = previousStatistics;
        }

        @Override
        public RemoteResultWrapper<RepositoryStatistics> invoke(
                final Repository repository, final VirtualChannel channel) {
            RemoteResultWrapper<RepositoryStatistics> result = new RemoteResultWrapper<>(
                    createStatisticsFromHead(repository), "Errors while mining the Git repository:");

            try {
                try (Git git = new Git(repository)) {
                    List<RevCommit> commits = new CommitCollector(repository, git,
                            previousStatistics.getLatestCommitId()).findAllCommits();
                    analyze(repository, git, commits, result);
                }
                catch (GitAPIException | IOException exception) {
                    result.logException(exception, "Can't obtain all commits for the repository.");
                }
            }
            finally {
                repository.close();
            }

            return result;
        }

        private RepositoryStatistics createStatisticsFromHead(final Repository repository) {
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

        private void analyze(final Repository repository, final Git git, final List<RevCommit> commits,
                final RemoteResultWrapper<RepositoryStatistics> result)
                throws IOException {
            Map<String, FileStatistics> fileStatistics = new HashMap<>(result.getResult().getMapping());
            FileStatisticsBuilder builder = new FileStatisticsBuilder();
            Set<String> filesInHead = new FilesCollector(repository).findAllFor(repository.resolve(Constants.HEAD));
            for (int i = commits.size() - 1; i >= 0; i--) {
                RevCommit newCommit = commits.get(i);
                String oldCommitName = i < commits.size() - 1 ? commits.get(i + 1).getName() : null;
                List<String> files = getFilesFromCommit(repository, git, oldCommitName, newCommit.getName(),
                        result);

                files.forEach(f -> fileStatistics.computeIfAbsent(f, builder::build)
                        .inspectCommit(newCommit.getCommitTime(), getAuthor(newCommit)));
            }
            fileStatistics.keySet().removeIf(f -> !filesInHead.contains(f));
            result.getResult().addAll(fileStatistics.values());
        }

        private List<String> getFilesFromCommit(final Repository repository, final Git git, final String oldCommit,
                final String newCommit, final FilteredLog logger) {
            List<String> filePaths = new ArrayList<>();

            try {
                final List<DiffEntry> diffEntries = git.diff()
                        .setOldTree(getTreeParser(repository, oldCommit))
                        .setNewTree(getTreeParser(repository, newCommit))
                        .call();

                filePaths = diffEntries.stream()
                        .map(DiffEntry::getNewPath)
                        .collect(Collectors.toList());

                filePaths.remove(DiffEntry.DEV_NULL);
                return filePaths;
            }
            catch (GitAPIException exception) {
                logger.logException(exception, "Can't analyze files for commits.");
            }
            catch (IOException exception) {
                logger.logException(exception, "Can't get treeParser.");
            }
            return filePaths;
        }

        private AbstractTreeIterator getTreeParser(final Repository repository, final String objectId)
                throws IOException {
            if (objectId == null) {
                return new EmptyTreeIterator();
            }
            try (RevWalk walk = new RevWalk(repository)) {
                RevCommit commit = walk.parseCommit(repository.resolve(objectId));
                RevTree tree = walk.parseTree(commit.getTree().getId());

                CanonicalTreeParser treeParser = new CanonicalTreeParser();
                try (ObjectReader reader = repository.newObjectReader()) {
                    treeParser.reset(reader, tree.getId());
                }
                walk.dispose();
                return treeParser;
            }
        }

        @CheckForNull
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
