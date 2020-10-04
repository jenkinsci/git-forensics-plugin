package io.jenkins.plugins.forensics.git.miner;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.Function;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
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

import io.jenkins.plugins.forensics.miner.Commit;
import io.jenkins.plugins.forensics.miner.FileStatistics;
import io.jenkins.plugins.forensics.miner.FileStatistics.FileStatisticsBuilder;
import io.jenkins.plugins.forensics.miner.RepositoryStatistics;

/**
 * Analyzes new Git repository commits and creates {@link FileStatistics} instances for all affected files.
 *
 * @author Ullrich Hafner
 */
public class CommitAnalyzer {
    // TODO: we need to create the new results separately to compute the added and deleted lines per build
    // TODO: would it make sense to record the changed files as well
    void run(final Repository repository, final Git git,
            final RepositoryStatistics result, final String latestCommitOfPreviousBuild,
            final FilteredLog logger) throws IOException, GitAPIException {
        DiffsCollector diffsCollector = new DiffsCollector();
        FileStatisticsBuilder builder = new FileStatisticsBuilder();

        Map<String, FileStatistics> fileStatistics = new HashMap<>();

        List<RevCommit> newCommits = new CommitCollector().findAllCommits(
                repository, git, latestCommitOfPreviousBuild);
        if (newCommits.isEmpty()) {
            logger.logInfo("No commits found since previous commit '%s'", latestCommitOfPreviousBuild);
        }

        for (int i = 0; i < newCommits.size(); i++) {
            AbstractTreeIterator toTree = createTreeIteratorToCompareTo(repository, newCommits, i, latestCommitOfPreviousBuild);
            Commit commit = createFromRevCommit(newCommits.get(i));
            Map<String, Commit> files = diffsCollector.getFilesAndDiffEntriesForCommit(repository, git,
                    commit, toTree, logger);
            logger.logInfo("Analyzed commit '%s' (authored by %s): %d files, %d lines added, %d lines deleted",
                    commit.getId(), commit.getAuthor(), files.size(),
                    count(files, Commit::getTotalAddedLines), count(files, Commit::getTotalDeletedLines));

            files.forEach((file, statistics) ->
                    fileStatistics.computeIfAbsent(file, builder::build).inspectCommit(statistics));
        }

        result.addAll(fileStatistics.values());
    }

    private long count(final Map<String, Commit> files, final Function<Commit, Integer> property) {
        return files.values().stream().map(property).count();
    }

    private Commit createFromRevCommit(final RevCommit newCommit) {
        return new Commit(newCommit.getName(), getAuthor(newCommit), newCommit.getCommitTime());
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

    private AbstractTreeIterator createTreeIteratorToCompareTo(final Repository repository,
            final List<RevCommit> newCommits, final int index, final String latestCommitOfPreviousBuild)
            throws IOException {
        int compareIndex = index + 1;
        if (compareIndex < newCommits.size()) { // compare with another commit in the list
            return createTreeIteratorFor(repository, newCommits.get(compareIndex).getName());
        }
        if (StringUtils.isNotBlank(latestCommitOfPreviousBuild)) {
            return createTreeIteratorFor(repository, latestCommitOfPreviousBuild);
        }
        return new EmptyTreeIterator();
    }

    static AbstractTreeIterator createTreeIteratorFor(final Repository repository, final String commitId)
            throws IOException {
        try (RevWalk walk = new RevWalk(repository)) {
            ObjectId resolve = repository.resolve(commitId);
            if (resolve == null) {
                throw new NoSuchElementException("No commit found with ID " + commitId);
            }
            RevCommit commit = walk.parseCommit(resolve);
            RevTree tree = walk.parseTree(commit.getTree().getId());

            CanonicalTreeParser treeParser = new CanonicalTreeParser();
            try (ObjectReader reader = repository.newObjectReader()) {
                treeParser.reset(reader, tree.getId());
            }
            walk.dispose();
            return treeParser;
        }
    }
}
