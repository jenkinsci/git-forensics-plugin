package io.jenkins.plugins.git.forensics.reference;

import hudson.model.Run;
import jenkins.model.RunAction2;

import javax.annotation.CheckForNull;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * This class tries to find the reversion of the last shared Commit of the current Git branch and the master branch.
 *
 * @author Arne Schöntag
 */
// TODO Name to be Changed
public class GitBranchMasterIntersectionFinder implements RunAction2, Serializable {

    private static final long serialVersionUID = -4549516129641755356L;
    private transient Run<?, ?> run;

    private transient Run<?, ?> reference;

    private final String NAME = "GitBranchMasterIntersectionFinder";
    private final String NO_INTERSECTION_FOUND = "No intersection was found in master commits";

    /**
     * Defines how far the Finder will look in the past commits to find an intersection.
     */
    private int maxLogs;

    /**
     * @param run the Jenkins build.
     * @param maxLogs defines how far the Finder will look in the past commits to find an intersection.
     * @param reference the name of the Reference Job
     */
    public GitBranchMasterIntersectionFinder(Run<?, ?> run, int maxLogs, Run<?, ?> reference) {
        this.run = run;
        this.maxLogs = maxLogs;
        this.reference = reference;
    }

    /**
     * Method to determine the Reversion of the last Commit which is shared with the master branch.
     *
     * @return the hash value (ObjectId) of the reversion
     *          or null if an error occurred during evaluation or no intersection was found (should not happen)
     */
    public Optional<String> findReferencePoint() {
        try {
            GitCommit thisCommit = run.getAction(GitCommit.class);
            GitCommit referenceCommit = reference.getAction(GitCommit.class);
            List<String> branchCommits = new ArrayList<>(thisCommit.getGitCommitLog().getReversions());
            List<String> masterCommits = new ArrayList<>(referenceCommit.getGitCommitLog().getReversions());

            // Fill master commit list
            Run<?, ?> tmp = reference;
            // TODO only maxLogs in the past or more?
            while (masterCommits.size() < maxLogs && tmp.getPreviousBuild() != null) {
                tmp = tmp.getPreviousBuild();
                masterCommits.addAll(tmp.getAction(GitCommit.class).getGitCommitLog().getReversions());
            }

            // Fill branch commit list
            tmp = run;
            while (branchCommits.size() < maxLogs && tmp.getPreviousBuild() != null) {
                tmp = tmp.getPreviousBuild();
                branchCommits.addAll(tmp.getAction(GitCommit.class).getGitCommitLog().getReversions());
            }

            Optional<String> referencePoint = branchCommits.stream().filter(reversion -> masterCommits.contains(reversion)).findFirst();
            return referencePoint;
        } catch (Exception e) {
            // TODO Logging
            return Optional.empty();
        }
    }

    public String getSummary() {
        Optional<String> summary = findReferencePoint();
        if (summary.isPresent()) {
            return summary.get();
        }
        return NO_INTERSECTION_FOUND;
    }

    @Override
    public void onAttached(Run<?, ?> r) {
        this.run = r;
    }

    @Override
    public void onLoad(Run<?, ?> r) {
        onAttached(r);
    }

    @CheckForNull
    @Override
    public String getIconFileName() {
        return null;
    }

    @CheckForNull
    @Override
    public String getDisplayName() {
        return NAME;
    }

    @CheckForNull
    @Override
    public String getUrlName() {
        return null;
    }
}
