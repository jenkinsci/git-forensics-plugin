package io.jenkins.plugins.forensics.git.miner;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

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
import edu.hm.hafner.util.TreeStringBuilder;

import io.jenkins.plugins.forensics.miner.CommitDiffItem;

/**
 * Analyzes the new Git repository commits since a previous commit ID and creates {@link CommitDiffItem} instances for all
 * changes.
 *
 * @author Giulia Del Bravo
 * @author Ullrich Hafner
 */
class CommitAnalyzer {
    List<CommitDiffItem> run(final Repository repository, final Git git,
            final String latestCommitOfPreviousBuild,
            final FilteredLog logger) throws IOException, GitAPIException {
        List<RevCommit> newRevCommits = new CommitCollector().findAllCommits(
                repository, git, latestCommitOfPreviousBuild, logger);
        if (newRevCommits.isEmpty()) {
            logger.logInfo("No commits found since previous commit '%s'", latestCommitOfPreviousBuild);
        }
        logger.logInfo("Found %d commits", newRevCommits.size());

        TreeStringBuilder fileNameBuilder = new TreeStringBuilder();
        List<CommitDiffItem> commitsOfBuild = new ArrayList<>();
        for (int i = 0; i < newRevCommits.size(); i++) {
            AbstractTreeIterator toTree = createTreeIteratorToCompareTo(
                    repository, newRevCommits, i, latestCommitOfPreviousBuild, logger);
            CommitDiffItem commit = createFromRevCommit(newRevCommits.get(i));
            commitsOfBuild.addAll(new DiffsCollector().getDiffsForCommit(
                    repository, git, commit, toTree, fileNameBuilder, logger));
        }
        fileNameBuilder.dedup();

        return commitsOfBuild;
    }

    private CommitDiffItem createFromRevCommit(final RevCommit newCommit) {
        return new CommitDiffItem(newCommit.getName(), getAuthor(newCommit), newCommit.getCommitTime());
    }

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
            final List<RevCommit> newCommits, final int index, final String latestCommitOfPreviousBuild,
            final FilteredLog logger) throws IOException {
        int compareIndex = index + 1;
        if (compareIndex < newCommits.size()) { // compare with another commit in the list
            return createTreeIteratorFor(newCommits.get(compareIndex).getName(), repository, logger);
        }
        if (StringUtils.isNotBlank(latestCommitOfPreviousBuild)) {
            return createTreeIteratorFor(latestCommitOfPreviousBuild, repository, logger);
        }
        return new EmptyTreeIterator();
    }

    static AbstractTreeIterator createTreeIteratorFor(final String commitId, final Repository repository,
            final FilteredLog logger) throws IOException {
        try (RevWalk walk = new RevWalk(repository)) {
            ObjectId resolve = repository.resolve(commitId);
            if (resolve == null) {
                logger.logError("No commit found with ID " + commitId);
                return new EmptyTreeIterator();
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
