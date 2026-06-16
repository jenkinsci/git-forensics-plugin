package io.jenkins.plugins.forensics.git.reference;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

import edu.hm.hafner.util.FilteredLog;

import java.io.IOException;
import java.io.Serial;

import hudson.remoting.VirtualChannel;

import io.jenkins.plugins.forensics.git.util.AbstractRepositoryCallback;
import io.jenkins.plugins.forensics.git.util.GitCommitTextDecorator;
import io.jenkins.plugins.forensics.git.util.RemoteResultWrapper;

/**
 * Collects all the commits since the last build.
 */
class GitCommitsCollector extends AbstractRepositoryCallback<RemoteResultWrapper<BuildCommits>> {
    @Serial
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
        try (var git = new Git(repository)) {
            var effectivePreviousCommit = resolveLatestRecordedCommit(repository);
            var commits = new BuildCommits(effectivePreviousCommit);
            RemoteResultWrapper<BuildCommits> result = new RemoteResultWrapper<>(commits,
                    "Errors while collecting commits");
            if (!StringUtils.isBlank(latestRecordedCommit) && StringUtils.isBlank(effectivePreviousCommit)) {
                result.logInfo(
                        "-> Previous build commit '%s' no longer exists in the repository (possibly due to a "
                                + "force-push after an amend) - restarting commit recording from scratch",
                        DECORATOR.asText(latestRecordedCommit));
            }
            findHeadCommit(repository, commits, result);
            for (RevCommit commit : git.log().add(commits.getHead()).call()) {
                var commitId = commit.getName();
                if (commitId.equals(effectivePreviousCommit) || commits.size() >= MAX_COMMITS) {
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

    /**
     * Resolves the latest recorded commit. If the commit no longer exists in the repository (e.g., after a force-push
     * with an amended commit), an empty string is returned so that the recording is treated as a fresh start rather
     * than incorrectly reporting up to {@value MAX_COMMITS} new commits.
     *
     * @param repository
     *         the Git repository
     *
     * @return the latest recorded commit ID if it still exists in the repository, or an empty string if the commit can
     *         no longer be found
     *
     * @throws IOException
     *         in case of an IO error while accessing the repository
     */
    String resolveLatestRecordedCommit(final Repository repository) throws IOException {
        if (StringUtils.isBlank(latestRecordedCommit)) {
            return latestRecordedCommit; // no previous commit recorded; treat as fresh start
        }
        var resolved = repository.resolve(latestRecordedCommit);
        if (resolved == null) {
            return StringUtils.EMPTY; // commit no longer exists (e.g. force-push after amend); treat as fresh start
        }
        return latestRecordedCommit;
    }

    private void findHeadCommit(final Repository repository, final BuildCommits commits, final FilteredLog logger)
            throws IOException {
        var head = getHead(repository);
        var parents = head.getParents();
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
        var head = repository.resolve(Constants.HEAD);
        if (head == null) {
            throw new IOException("No HEAD commit found in " + repository);
        }
        return new RevWalk(repository).parseCommit(head);
    }
}