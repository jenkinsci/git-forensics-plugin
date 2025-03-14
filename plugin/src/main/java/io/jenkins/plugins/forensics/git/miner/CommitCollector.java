package io.jenkins.plugins.forensics.git.miner;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;

import edu.hm.hafner.util.FilteredLog;

import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import one.util.streamex.StreamEx;

/**
 * Collects all commits for a git repository up to a given commit ID. The collected commits will be sorted ascending,
 * i.e. the list starts with the given commit ID up to the current HEAD.
 *
 * @author Giulia Del Bravo
 * @author Ullrich Hafner
 */
class CommitCollector {
    List<RevCommit> findAllCommits(final Repository repository, final Git git, final String latestCommitId,
            final FilteredLog logger) throws IOException, GitAPIException {
        var head = repository.resolve(Constants.HEAD);
        if (head == null) {
            logger.logError("No HEAD commit found in " + repository);
            return Collections.emptyList();
        }
        Iterator<RevCommit> commits = git.log().add(head).call().iterator();

        return StreamEx.of(commits)
                .takeWhile(commit -> !latestCommitId.equals(commit.getName()))
                .toList();
    }
}
