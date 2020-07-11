package io.jenkins.plugins.forensics.git.reference;

import java.io.Serializable;
import java.util.Optional;

import hudson.model.Run;
import jenkins.model.RunAction2;

/**
 * This class tries to find the revision of the last shared Commit of the current VCS branch and the master branch.
 *
 * @author Arne Schöntag
 */
public abstract class BranchMasterIntersectionFinder implements RunAction2, Serializable {

    private static final long serialVersionUID = -4549516129641755356L;

    public static final String NO_INTERSECTION_FOUND = "No intersection was found in master commits";

    /**
     * Method to determine the Revision of the last Commit which is shared with the master branch.
     *
     * @return the hash value (ObjectId) of the revision
     *          or null if an error occurred during evaluation or no intersection was found (should not happen)
     */
    public abstract Optional<String> findReferencePoint();

    public abstract String getSummary();

    public abstract String getBuildId();

    public abstract Run<?, ?> getRun();

    public abstract Run<?, ?> getReference();

    public abstract Optional<Run<?, ?>> getReferenceBuild();

}
