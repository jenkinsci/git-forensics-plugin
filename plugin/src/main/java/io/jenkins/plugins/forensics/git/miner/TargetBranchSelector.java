package io.jenkins.plugins.forensics.git.miner;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.filter.RevFilter;

import java.io.IOException;
import java.io.Serial;

import org.jenkinsci.plugins.gitclient.RepositoryCallback;
import hudson.remoting.VirtualChannel;

/**
 * Finds the merge base of the current {@code HEAD} and the head of a target branch. The target branch is resolved
 * using the common local and remote-tracking ref names.
 *
 * @author Michael Trimarchi
 */
class TargetBranchSelector implements RepositoryCallback<String> {
    @Serial
    private static final long serialVersionUID = 1L;

    private final String targetBranch;

    TargetBranchSelector(final String targetBranch) {
        this.targetBranch = targetBranch;
    }

    @Override
    public String invoke(final Repository repository, final VirtualChannel channel) throws IOException {
        var head = repository.resolve(Constants.HEAD);
        if (head == null) {
            return StringUtils.EMPTY;
        }
        var target = resolveTarget(repository);
        if (target == null) {
            return StringUtils.EMPTY;
        }
        try (var walk = new RevWalk(repository)) {
            walk.setRevFilter(RevFilter.MERGE_BASE);
            walk.markStart(walk.parseCommit(head));
            walk.markStart(walk.parseCommit(target));
            var next = walk.next();
            return next == null ? target.getName() : next.getName();
        }
    }

    private ObjectId resolveTarget(final Repository repository) throws IOException {
        var candidates = new String[] {
            targetBranch,
            "origin/" + targetBranch,
            "refs/remotes/origin/" + targetBranch,
            "refs/heads/" + targetBranch
        };
        for (String candidate : candidates) {
            var resolved = repository.resolve(candidate);
            if (resolved != null) {
                return resolved;
            }
        }
        return null;
    }
}
