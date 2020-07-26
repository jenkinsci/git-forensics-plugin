package io.jenkins.plugins.forensics.git.reference;

import java.io.Serializable;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;

import hudson.model.Run;
import jenkins.model.RunAction2;

import io.jenkins.plugins.util.JenkinsFacade;

/**
 * This class tries to find the revision of the last shared Commit of the current Git branch and the master branch.
 *
 * @author Arne Schöntag
 */
public class GitReferenceBuild implements RunAction2, Serializable {
    private static final long serialVersionUID = -4549516129641755356L;

    static final String NO_REFERENCE_BUILD = "-";

    private transient Run<?, ?> owner;
    private final String referenceBuildId;

    private final JenkinsFacade jenkinsFacade = new JenkinsFacade();

    public GitReferenceBuild(final Run<?, ?> owner, final int maxLogs, final boolean skipUnknownCommits,
            final boolean newestBuildIfNotFound, final Run<?, ?> lastCompletedReference) {
        super();

        this.owner = owner;

        GitCommitsRecord thisCommit = owner.getAction(GitCommitsRecord.class);
        GitCommitsRecord referenceCommit = lastCompletedReference.getAction(GitCommitsRecord.class);

        Optional<String> referencePoint = thisCommit.getReferencePoint(referenceCommit, maxLogs, skipUnknownCommits);
        if (referencePoint.isPresent()) {
            referenceBuildId = referencePoint.get();
        }
        else {
            if (newestBuildIfNotFound) {
                referenceBuildId = lastCompletedReference.getExternalizableId();
            }
            else {
                referenceBuildId = NO_REFERENCE_BUILD;
            }
        }
    }

    public String getSummary() {
        if (!hasReferenceBuild()) {
            return Messages.No_Reference_Build();
        }
        return referenceBuildId;
    }

    public boolean hasReferenceBuild() {
        return !StringUtils.equals(referenceBuildId, NO_REFERENCE_BUILD);
    }

    public String getReferenceBuildId() {
        return referenceBuildId;
    }

    @Override
    public void onAttached(final Run<?, ?> r) {
        this.owner = r;
    }

    @Override
    public void onLoad(final Run<?, ?> r) {
        onAttached(r);
    }

    public Run<?, ?> getOwner() {
        return owner;
    }

    public Optional<Run<?, ?>> getReferenceBuild() {
        if (hasReferenceBuild()) {
            return jenkinsFacade.getBuild(referenceBuildId);
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
