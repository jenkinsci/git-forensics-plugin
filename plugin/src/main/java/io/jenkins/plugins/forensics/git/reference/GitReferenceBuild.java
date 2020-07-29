package io.jenkins.plugins.forensics.git.reference;

import java.util.Optional;

import hudson.model.Run;

import io.jenkins.plugins.forensics.reference.ReferenceBuild;

/**
 * This class tries to find the revision of the last shared Commit of the current Git branch and the master branch.
 *
 * @author Arne Schöntag
 */
public class GitReferenceBuild extends ReferenceBuild {
    private static final long serialVersionUID = -3918849482344551811L;

    /**
     * Creates a new reference build.
     *
     * @param owner
     *         the current run as owner of this action
     * @param maxLogs
     *         the maximum number of commits to inspect
     * @param skipUnknownCommits
     *         determines whether unkonwn commits should be skipped
     * @param latestBuildIfNotFound
     *         determines whether the latest build should be returned if no reference buiild has been found
     * @param lastCompletedReference
     *         the run to use as starting point for the search
     */
    public GitReferenceBuild(final Run<?, ?> owner, final int maxLogs, final boolean skipUnknownCommits,
            final boolean latestBuildIfNotFound, final Run<?, ?> lastCompletedReference) {
        super(owner, findReferenceBuild(owner, maxLogs, skipUnknownCommits,
                latestBuildIfNotFound, lastCompletedReference));
    }

    private static String findReferenceBuild(final Run<?, ?> owner,
            final int maxLogs, final boolean skipUnknownCommits,
            final boolean newestBuildIfNotFound, final Run<?, ?> lastCompletedReference) {
        GitCommitsRecord thisCommit = owner.getAction(GitCommitsRecord.class);
        GitCommitsRecord referenceCommit = lastCompletedReference.getAction(GitCommitsRecord.class);

        Optional<String> referencePoint = thisCommit.getReferencePoint(referenceCommit, maxLogs, skipUnknownCommits);
        if (referencePoint.isPresent()) {
            return referencePoint.get();
        }
        if (newestBuildIfNotFound) {
            return lastCompletedReference.getExternalizableId();
        }
        return ReferenceBuild.NO_REFERENCE_BUILD;
    }
}
