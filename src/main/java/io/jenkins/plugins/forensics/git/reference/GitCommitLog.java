package io.jenkins.plugins.forensics.git.reference;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * This class represent the Storage of the Information which Commits has been pushed since the last build of this job.
 *
 * @author Arne Schöntag
 */
@SuppressWarnings("unused")
public class GitCommitLog implements Serializable {
    private static final long serialVersionUID = 8988806831945499189L;

    /** Holds the hash keys of the commit revisions. */
    private final List<String> revisions = new ArrayList<>();

    public List<String> getRevisions() {
        return revisions;
    }

    public void addRevisions(final List<String> list) {
        revisions.addAll(list);
    }

    public void addRevision(final String rev) {
        revisions.add(rev);
    }
}
