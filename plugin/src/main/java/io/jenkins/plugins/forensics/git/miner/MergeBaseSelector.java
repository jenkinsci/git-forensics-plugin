package io.jenkins.plugins.forensics.git.miner;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.filter.RevFilter;

import java.io.IOException;

import org.jenkinsci.plugins.gitclient.RepositoryCallback;
import hudson.remoting.VirtualChannel;

/**
 * Finds as good common ancestors as possible for a merge.
 *
 * @author Ullrich Hafner
 * @see <a href="https://git-scm.com/docs/git-merge-base">Git git-merge-base command</a>
 */
class MergeBaseSelector implements RepositoryCallback<String> {
    private static final long serialVersionUID = 163631519980916591L;

    private final String latestCommit;

    MergeBaseSelector(final String latestCommit) {
        this.latestCommit = latestCommit;
    }

    @Override
    public String invoke(final Repository repository, final VirtualChannel virtualChannel) throws IOException {
        var head = repository.resolve(Constants.HEAD);
        if (head == null) {
            return "";
        }

        var target = repository.resolve(latestCommit);

        var walk = new RevWalk(repository);
        walk.setRevFilter(RevFilter.MERGE_BASE);
        walk.markStart(repository.parseCommit(head));
        walk.markStart(repository.parseCommit(target));

        var next = walk.next();
        if (next == null) {
            return latestCommit;
        }
        return next.getId().getName();
    }
}
