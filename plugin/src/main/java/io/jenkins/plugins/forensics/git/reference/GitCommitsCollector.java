package io.jenkins.plugins.forensics.git.reference;

import java.io.IOException;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

import edu.hm.hafner.util.FilteredLog;

import hudson.remoting.VirtualChannel;

import io.jenkins.plugins.forensics.git.util.AbstractRepositoryCallback;
import io.jenkins.plugins.forensics.git.util.GitCommitTextDecorator;
import io.jenkins.plugins.forensics.git.util.RemoteResultWrapper;

/**
 * Collects and records all commits since the last build.
 */
class GitCommitsCollector extends AbstractRepositoryCallback<RemoteResultWrapper<BuildCommits>> {
    private static final long serialVersionUID = -5980402198857923793L;

    private static final GitCommitTextDecorator DECORATOR = new GitCommitTextDecorator();

    private static final int MAX_COMMITS = 200; // TODO: should the number of recorded commits be configurable?

    private final String latestRecordedCommit;

    GitCommitsCollector(final String latestRecordedCommit) {
        super();
        this.latestRecordedCommit = latestRecordedCommit;
    }

    @Override
    public RemoteResultWrapper<BuildCommits> invoke(final Repository repository, final VirtualChannel channel)
            throws IOException {
        try (Git git = new Git(repository)) {
            BuildCommits commits = new BuildCommits(latestRecordedCommit);
            RemoteResultWrapper<BuildCommits> result = new RemoteResultWrapper<>(commits,
                    "Errors while collecting commits");
            findHeadCommit(repository, commits, result);
            for (RevCommit commit : git.log().add(commits.getHead()).call()) {
                String commitId = commit.getName();
                if (commitId.equals(latestRecordedCommit) || commits.size() >= MAX_COMMITS) {
                    return result;
                }
                commits.add(commitId);
            }
            return result;
        }
        catch (GitAPIException e) {
            throw new IOException("Unable to record commits of git repository.", e);
        }
    }

    private void findHeadCommit(final Repository repository, final BuildCommits commits, final FilteredLog logger)
            throws IOException {
        RevCommit head = getHead(repository);
        RevCommit[] parents = head.getParents();
        if (parents.length < 1) {
            logger.logInfo("-> No parent commits found - detected the first commit in the branch");
            logger.logInfo("-> Using head commit '%s' as starting point",
                    DECORATOR.asText(head));
            commits.setHead(head);
        }
        else if (parents.length == 1) {
            logger.logInfo("-> Single parent commit found - branch is already descendant of target branch head");
            logger.logInfo("-> Using head commit '%s' as starting point",
                    DECORATOR.asText(head));
            commits.setHead(head);
        }
        else {
            logger.logInfo("-> Multiple parent commits found - storing latest commit of local merge '%s'",
                    DECORATOR.asText(head));
            logger.logInfo("-> Using parent commit '%s' of local merge as starting point",
                    DECORATOR.asText(parents[0]));
            logger.logInfo("-> Storing target branch head '%s' (second parent of local merge) ",
                    DECORATOR.asText(parents[1]));
            commits.setHead(parents[0]);
            commits.setTarget(parents[1]);
            commits.setMerge(head);
        }
    }

    private RevCommit getHead(final Repository repository) throws IOException {
        ObjectId head = repository.resolve(Constants.HEAD);
        if (head == null) {
            throw new IOException("No HEAD commit found in " + repository);
        }
        return new RevWalk(repository).parseCommit(head);
    }
}
