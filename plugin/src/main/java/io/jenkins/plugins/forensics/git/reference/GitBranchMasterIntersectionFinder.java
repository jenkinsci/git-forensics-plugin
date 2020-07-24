package io.jenkins.plugins.forensics.git.reference;

import java.io.Serializable;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;

import hudson.model.Run;
import jenkins.model.RunAction2;

/**
 * This class tries to find the revision of the last shared Commit of the current Git branch and the master branch.
 *
 * @author Arne Schöntag
 */
// TODO Name to be Changed
@SuppressWarnings({"PMD.DataClass", "checkstyle:HiddenField"})
public class GitBranchMasterIntersectionFinder implements RunAction2, Serializable {
    private static final long serialVersionUID = -4549516129641755356L;
    static final String NO_INTERSECTION_FOUND = "No intersection was found in master commits";

    private transient Run<?, ?> owner;
    private transient Run<?, ?> reference;

    private final String buildId;

    /**
     * Defines how far the Finder will look in the past commits to find an intersection.
     */
    private final int maxLogs;

    private final boolean skipUnknownCommits;

    private final boolean newestBuildIfNotFound;

    public GitBranchMasterIntersectionFinder(final Run<?, ?> owner, final int maxLogs, final boolean skipUnknownCommits,
            final boolean newestBuildIfNotFound, final Run<?, ?> reference) {
        super();
        this.owner = owner;
        this.maxLogs = maxLogs;
        this.skipUnknownCommits = skipUnknownCommits;
        this.newestBuildIfNotFound = newestBuildIfNotFound;
        this.reference = reference;
        this.buildId = findReferencePoint().get();
    }

    /**
     * Method to determine the Revision of the last Commit which is shared with the master branch.
     *
     * @return the hash value (ObjectId) of the revision or null if an error occurred during evaluation or no
     *         intersection was found (should not happen)
     */
    public Optional<String> findReferencePoint() {
        GitCommitsRecord thisCommit = owner.getAction(GitCommitsRecord.class);
        GitCommitsRecord referenceCommit = reference.getAction(GitCommitsRecord.class);

        Optional<String> buildId = thisCommit.getReferencePoint(referenceCommit, maxLogs, skipUnknownCommits);
        if (!buildId.isPresent()) {
            if (newestBuildIfNotFound) {
                buildId = Optional.of(reference.getExternalizableId());
            }
            else {
                buildId = Optional.of(NO_INTERSECTION_FOUND);
            }
        }
        return buildId;
    }

    public String getSummary() {
        if (this.buildId != null) {
            return buildId;
        }
        Optional<String> summary = findReferencePoint();
        return summary.orElse(NO_INTERSECTION_FOUND);
    }

    public String getBuildId() {
        return buildId;
    }

    @Override
    public void onAttached(final Run<?, ?> r) {
        this.owner = r;
    }

    @Override
    public void onLoad(final Run<?, ?> r) {
        onAttached(r);
    }

    public Run<?, ?> getReference() {
        return reference;
    }

    public void setReference(final Run<?, ?> reference) {
        this.reference = reference;
    }

    public Run<?, ?> getOwner() {
        return owner;
    }

    public Optional<Run<?, ?>> getReferenceBuild() {
        // FIXME: this makes no sense is never null
        if (buildId != null && !StringUtils.equals(NO_INTERSECTION_FOUND, buildId)) {
            return Optional.ofNullable(Run.fromExternalizableId(buildId));
        }
        return Optional.empty();
    }

    @Override
    public String getIconFileName() {
        return null;
    }

    @Override
    public String getDisplayName() {
        return Messages.Action_DisplayName();
    }

    @Override
    public String getUrlName() {
        return null;
    }
}
