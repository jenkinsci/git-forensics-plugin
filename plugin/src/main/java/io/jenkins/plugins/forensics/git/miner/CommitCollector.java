package io.jenkins.plugins.forensics.git.miner;

import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.LogCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;

/**
 * Collects all commits for a git repository.
 *
 * @author Giulia Del Bravo
 */
public class CommitCollector {
    private final Repository repository;
    private final Git git;
    //TODO Assign later when incremental analysis is implemented.
    private final String latestRevisionOfPreviousCommit;

    CommitCollector(final Repository repository, final Git git) {
        this.repository = repository;
        this.git = git;
        latestRevisionOfPreviousCommit = null;
    }

    List<RevCommit> findAllCommits() throws IOException, GitAPIException {
        ObjectId headCommit = repository.resolve(Constants.HEAD);
        LogCommand logCommand = git.log().add(headCommit);
        Iterable<RevCommit> commits = logCommand.call();
        Iterator<RevCommit> iterator = commits.iterator();
        RevCommit next;
        List<RevCommit> newCommits = new LinkedList<>();
        while (iterator.hasNext()) {
            next = iterator.next();
            String commitId = next.getId().getName();
            if (commitId.equals(latestRevisionOfPreviousCommit)) {
                break;
            }
            newCommits.add(next);
        }
        return newCommits;
    }
}
