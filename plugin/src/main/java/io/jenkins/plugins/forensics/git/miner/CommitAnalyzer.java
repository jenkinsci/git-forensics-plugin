package io.jenkins.plugins.forensics.git.miner;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

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

/**
 * Analyzes the new Git repository commits since a previous commit ID and and creates {@link Commit} instances for all
 * changes.
 *
 * @author Giulia Del Bravo
 * @author Ullrich Hafner
 */
class CommitAnalyzer {
    private static final int MAX_COMMIT_TO_LOG = 7;

    List<Commit> run(final Repository repository, final Git git,
            final String latestCommitOfPreviousBuild,
            final FilteredLog logger) throws IOException, GitAPIException {
        List<RevCommit> newRevCommits = new CommitCollector().findAllCommits(repository, git,
                latestCommitOfPreviousBuild);
        if (newRevCommits.isEmpty()) {
            logger.logInfo("No commits found since previous commit '%s'", latestCommitOfPreviousBuild);
        }
        if (newRevCommits.size() >= MAX_COMMIT_TO_LOG) {
            logger.logInfo("Found %d commits", newRevCommits.size());
        }

        List<Commit> commitsOfBuild = new ArrayList<>();
        for (int i = 0; i < newRevCommits.size(); i++) {
            AbstractTreeIterator toTree = createTreeIteratorToCompareTo(repository, newRevCommits, i,
                    latestCommitOfPreviousBuild);
            Commit commit = createFromRevCommit(newRevCommits.get(i));
            List<Commit> commits = new DiffsCollector().getDiffsForCommit(repository, git, commit, toTree, logger);
            if (newRevCommits.size() < MAX_COMMIT_TO_LOG) {
                logger.logInfo("Analyzed commit '%s' (authored by %s): %d files affected",
                        commit.getId(), commit.getAuthor(), commits.size());
                Commit.logCommits(commits, logger);
            }
            commitsOfBuild.addAll(commits);
        }

        return commitsOfBuild;
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
