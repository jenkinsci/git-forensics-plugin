package io.jenkins.plugins.git.forensics.reference;

import hudson.model.Run;
import io.jenkins.plugins.forensics.reference.BranchMasterIntersectionFinder;

import java.util.Optional;

/**
 * This class tries to find the revision of the last shared Commit of the current Git branch and the master branch.
 *
 * @author Arne Schöntag
 */
// TODO Name to be Changed
@SuppressWarnings({"PMD.DataClass", "checkstyle:HiddenField"})
public class GitBranchMasterIntersectionFinder extends BranchMasterIntersectionFinder {

    private static final long serialVersionUID = -4549516129641755356L;
    private transient Run<?, ?> run;

    private transient Run<?, ?> reference;

    private static final String NAME = "GitBranchMasterIntersectionFinder";
    private static final String NO_INTERSECTION_FOUND = "No intersection was found in master commits";

    private String buildId = "";

    /**
     * Defines how far the Finder will look in the past commits to find an intersection.
     */
    private final int maxLogs;

    public GitBranchMasterIntersectionFinder(final Run<?, ?> run, final int maxLogs, final Run<?, ?> reference) {
        super();
        this.run = run;
        this.maxLogs = maxLogs;
        this.reference = reference;
    }

    /**
     * Returns the build of the reference job which has the last intersection with the current build.
     * @return build id.
     */
    public Optional<String> findReferencePoint() {
        GitCommit thisCommit = run.getAction(GitCommit.class);
        GitCommit referenceCommit = reference.getAction(GitCommit.class);

        Optional<String> buildId = thisCommit.getReferencePoint(referenceCommit, maxLogs);
        buildId.ifPresent(this::setBuildId);
        return buildId;
    }

    public String getSummary() {
        Optional<String> summary = findReferencePoint();
        return summary.orElse(NO_INTERSECTION_FOUND);
    }

    public String getBuildId() {
        return buildId;
    }

    public void setBuildId(final String buildId) {
        this.buildId = buildId;
    }

    @Override
    public void onAttached(final Run<?, ?> r) {
        this.run = r;
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

    @Override
    public String getIconFileName() {
        return null;
    }

    @Override
    public String getDisplayName() {
        return NAME;
    }

    @Override
    public String getUrlName() {
        return null;
    }
}
