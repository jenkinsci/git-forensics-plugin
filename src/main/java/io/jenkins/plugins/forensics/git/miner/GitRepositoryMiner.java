package io.jenkins.plugins.forensics.git.miner;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.errors.AmbiguousObjectException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
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

import com.cloudbees.diff.Diff;

import edu.hm.hafner.util.FilteredLog;
import edu.umd.cs.findbugs.annotations.Nullable;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import org.jenkinsci.plugins.gitclient.GitClient;
import hudson.remoting.VirtualChannel;

import io.jenkins.plugins.forensics.git.util.AbstractRepositoryCallback;
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
            logger.logInfo("Analyzing the commit log of the Git repository '%s'", gitClient.getWorkTree());
            RepositoryStatistics statistics = gitClient.withRepository(
                    new RepositoryStatisticsCallback(absoluteFileNames, logger));
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
                    try (Git git = new Git(repository)) {
                        List<RevCommit> commits = new CommitCollector(repository, git).findAllCommits();
                        return analyze(repository, git, commits);
                    }
                    catch (GitAPIException exception) {
                        //update log text.
                        logger.logException(exception, "Can't analyze history of repository");
                    }
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

        RepositoryStatistics analyze(final Repository repository, final Git git, final List<RevCommit> commits) {
            RepositoryStatistics statistics = new RepositoryStatistics();
            FileStatisticsBuilder builder = new FileStatisticsBuilder();
            List<String> files = new ArrayList<>();
            Map<String, FileStatistics> fileStatistics = new HashMap<>();
            for (int i = commits.size() - 1; i >= 0 ; i--) {
                if (i== commits.size()-1) {
                    files = getFilesToCommit(repository, git, null, commits.get(i).getName());
                }
                else {
                    if(i > 0){
                        files = getFilesToCommit(repository, git, commits.get(i).getName(), commits.get(i - 1).getName());
                    }
                }
                int finalI = i;
                files.forEach(f -> fileStatistics.computeIfAbsent(f, builder::build)
                        .inspectCommit(commits.get(finalI).getCommitTime(), getAuthor(commits.get(finalI))));
            }
            statistics.addAll(fileStatistics.values());
            return statistics;
        }

        private List<String> getFilesToCommit(final Repository repository, final Git git, final String oldCommit,
                final String newCommit) {
            List<String> filePaths = new ArrayList<>();

            try {
                Set<String> filesInHead = new FilesCollector(repository).findAllFor(repository.resolve(Constants.HEAD));
                final List<DiffEntry> files = git.diff()
                        .setOldTree(getTreeParser(repository, oldCommit))
                        .setNewTree(getTreeParser(repository, newCommit))
                        .call();

                return files.stream()
                        .map(DiffEntry::getNewPath)
                        .filter(filesInHead::contains)
                        .collect(Collectors.toList());
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

        RepositoryStatistics analyze(final Repository repository, final Collection<String> files) {
            RepositoryStatistics statistics = new RepositoryStatistics();
            logger.logInfo("Invoking Git miner to create statistics for all available files");
            logger.logInfo("Git working tree = '%s'", getWorkTree(repository));

            FileStatisticsBuilder builder = new FileStatisticsBuilder();
            List<FileStatistics> fileStatistics = files.stream()
                    .map(builder::build)
                    .map(file -> analyzeHistory(repository, file))
                    .collect(Collectors.toList());
            statistics.addAll(fileStatistics);

            logger.logInfo("-> created statistics for %d files", statistics.size());

            return statistics;
        }

        private FileStatistics analyzeHistory(final Repository repository, final FileStatistics fileStatistics) {
            try (Git git = new Git(repository)) {
                Iterable<RevCommit> commits = git.log().addPath(fileStatistics.getFileName()).call();
                commits.forEach(c -> fileStatistics.inspectCommit(c.getCommitTime(), getAuthor(c)));
                return fileStatistics;
            }
            catch (GitAPIException exception) {
                logger.logException(exception, "Can't analyze history of file %s", fileStatistics.getFileName());
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
