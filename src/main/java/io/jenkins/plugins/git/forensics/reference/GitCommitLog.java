package io.jenkins.plugins.git.forensics.reference;

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

    /** Holds the hash keys of the commit reversions. */
    private final List<String> reversions = new ArrayList<>();

    public List<String> getReversions() {
        return reversions;
    }

    public void addRevision(final String rev) {
        reversions.add(rev);
    }
}
