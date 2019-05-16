package io.jenkins.plugins.git.forensics.reference;

import org.eclipse.jgit.lib.Repository;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * This class represent the Storage of the Information which Commits has been pushed since the last build of this job.
 *
 * @author Arne Schöntag
 */
public class GitCommitLog implements Serializable {

    // Default value 100
    private final int maxCommits;

    /** Holds the hash keys of the commit reversions */
    final List<String> reversions = new ArrayList<>();

    private final Repository repo;

    public GitCommitLog(Repository repo) {
        this(repo, 100);
    }

    public GitCommitLog(Repository repo, int maxCommits) {
        this.repo = repo;
        this.maxCommits = maxCommits;
    }

    public List<String> getReversions(){
        return reversions;
    }

    public void addRevision(String rev) {
        reversions.add(rev);
    }
}
