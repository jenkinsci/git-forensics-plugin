package io.jenkins.plugins.forensics.git.reference;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import io.jenkins.plugins.forensics.git.reference.GitCommitsRecord.RecordingType;

/**
 * The commits of a given build. If these commits are part of a pull request, then a target commit ID might be stored
 * that defines the parent commit of the head in the target branch.
 *
 * @author Ullrich Hafner
 */
class BuildCommits implements Serializable {
    @Serial
    private static final long serialVersionUID = -580006422072874429L;

    private final String previousBuildCommit;

    @SuppressWarnings("serial")
    private final List<String> commits = new ArrayList<>();

    private ObjectId head = ObjectId.zeroId();
    private ObjectId target = ObjectId.zeroId();
    private ObjectId merge = ObjectId.zeroId();

    /**
     * Set to {@code true} when the commit collector hit the maximum number of commits to scan without finding the
     * previous build's anchor commit. In this situation the "commits since last build" value is indeterminate and
     * must not be displayed (JENKINS-67281).
     */
    private boolean maxCommitsReached = false;

    BuildCommits(final String previousBuildCommit) {
        this.previousBuildCommit = previousBuildCommit;
    }

    String getPreviousBuildCommit() {
        return previousBuildCommit;
    }

    void setHead(final RevCommit head) {
        this.head = head;
    }

    ObjectId getHead() {
        return head;
    }

    void setTarget(final RevCommit target) {
        this.target = target;
    }

    ObjectId getTarget() {
        return target;
    }

    ObjectId getMerge() {
        return merge;
    }

    void setMerge(final ObjectId merge) {
        this.merge = merge;
    }

    /**
     * Checks whether the commit record contains a merge commit.
     *
     * @return {@code true} if a merge commit exists
     */
    boolean hasMerge() {
        return !merge.equals(ObjectId.zeroId());
    }

    List<String> getCommits() {
        return commits;
    }

    int size() {
        return commits.size();
    }

    void add(final String commitId) {
        commits.add(commitId);
    }

    boolean isEmpty() {
        return commits.isEmpty();
    }

    /**
     * Marks that the commit scan hit the configurable limit without finding the previous build's anchor commit. This
     * means the "commits since last build" count is indeterminate and should not be surfaced in the UI.
     */
    void setMaxCommitsReached() {
        this.maxCommitsReached = true;
    }

    /**
     * Returns {@code true} if the scan stopped because the maximum number of commits was reached before finding the
     * previous build's anchor commit — i.e., the "commits since last build" count is indeterminate.
     *
     * @return {@code true} if the commit count is indeterminate
     */
    boolean isMaxCommitsReached() {
        return maxCommitsReached;
    }

    RecordingType getRecordingType() {
        if (StringUtils.isBlank(previousBuildCommit)) {
            return RecordingType.START;
        }
        return RecordingType.INCREMENTAL;
    }

    String getLatestCommit() {
        if (commits.isEmpty()) {
            return previousBuildCommit;
        }
        return commits.get(0);
    }

    /**
     * Returns a merge commit if existent or the latest commit if not. In case that there is a merge commit, it is the
     * head of this commits record.
     *
     * @return the found commit
     */
    String getMergeOrLatestCommit() {
        if (getMerge().equals(ObjectId.zeroId())) {
            return getLatestCommit();
        }
        return getMerge().name();
    }
}
