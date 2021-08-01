package io.jenkins.plugins.forensics.git.reference;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;

import io.jenkins.plugins.forensics.git.reference.GitCommitsRecord.RecordingType;

/**
 * The commits of a given build. If these commits are part of a pull request then a target commit ID might be stored
 * that defines the parent commit of the head in the target branch.
 *
 * @author Ullrich Hafner
 */
class BuildCommits implements Serializable {
    private static final long serialVersionUID = -580006422072874429L;

    private final String previousBuildCommit;

    private final List<String> commits = new ArrayList<>();

    private ObjectId head = ObjectId.zeroId();
    private ObjectId target = ObjectId.zeroId();

    BuildCommits(final String previousBuildCommit) {
        this.previousBuildCommit = previousBuildCommit;
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
}
