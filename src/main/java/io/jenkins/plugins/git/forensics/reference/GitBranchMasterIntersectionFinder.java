package io.jenkins.plugins.git.forensics.reference;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.LogCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * This class tries to find the reversion of the last shared Commit of the current Git branch and the master branch.
 *
 * @author Arne Schöntag
 */
// TODO Name to be Changed
// TODO Suche über GitCommitLogs nicht über Git Befehle
public class GitBranchMasterIntersectionFinder {

    private final Repository repo;
    /**
     * Defines how far the Finder will look in the past commits to find an intersection.
     */
    private int maxLogs = Integer.MAX_VALUE;

    /**
     * @param repo the git repository.
     */
    public GitBranchMasterIntersectionFinder(final Repository repo) {
        this.repo = repo;
    }

    /**
     * @param repo the git repository.
     * @param maxLogs defines how far the Finder will look in the past commits to find an intersection
     */
    public GitBranchMasterIntersectionFinder(final Repository repo, int maxLogs) {
        this.repo = repo;
        this.maxLogs = maxLogs;
    }

    /**
     * Method to determine the Reversion of the last Commit which is shared with the master branch.
     *
     * @return the hash value (ObjectId) of the reversion
     *          or null if an error occurred during evaluation or no intersection was found (should not happen)
     * @throws GitAPIException
     */
    public ObjectId findReferencePoint() throws GitAPIException {
        try {
            String currentBranch = repo.getBranch();
            if ("master".equals(currentBranch)) {
                return null;
            }
            Git git = new Git(repo);
            LogCommand branchLog = git.log();
            Iterable<RevCommit> branchCommits = branchLog.call();

            // TODO does this work?
            LogCommand masterLog = git.log().add(repo.resolve("master"));
            Iterable<RevCommit> masterCommits = masterLog.call();

            // Find newest shared Commit
            List<RevCommit> masterCommitList = new ArrayList<>();
            masterCommits.forEach(masterCommitList::add);

            RevCommit result = null;
            Iterator<RevCommit> iterator = branchCommits.iterator();
            RevCommit next;
            int index = 0;
            while (result == null && iterator.hasNext()) {
                next = iterator.next();
                if (masterCommitList.contains(next)) {
                    result = next;
                }
                // If the maximal count of commits in the past is reached
                if (++index >= maxLogs) {
                    break;
                }
            }
            if (result == null) {
                return null;
            }
            return result.getId();
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
}
