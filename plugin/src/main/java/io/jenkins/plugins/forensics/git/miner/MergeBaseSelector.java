package io.jenkins.plugins.forensics.git.miner;

import java.io.IOException;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.filter.RevFilter;

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
        ObjectId head = repository.resolve(Constants.HEAD);
        if (head == null) {
            return "";
        }

        ObjectId target = repository.resolve(latestCommit);

        RevWalk walk = new RevWalk(repository);
        walk.setRevFilter(RevFilter.MERGE_BASE);
        walk.markStart(repository.parseCommit(head));
        walk.markStart(repository.parseCommit(target));

        RevCommit next = walk.next();
        if (next == null) {
            return latestCommit;
        }
        return next.getId().getName();
    }
}
