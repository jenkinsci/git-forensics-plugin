package io.jenkins.plugins.forensics.git.reference;

import edu.hm.hafner.util.FilteredLog;
import edu.hm.hafner.util.VisibleForTesting;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import java.util.Optional;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.jenkinsci.Symbol;
import hudson.Extension;
import hudson.model.Run;

import io.jenkins.plugins.forensics.reference.ReferenceRecorder;
import io.jenkins.plugins.util.JenkinsFacade;

/**
 * Recorder that finds a reference build that matches best with the current build of a given Git branch.
 *
 * @author Arne Schöntag
 * @author Ullrich Hafner
 */
public class GitReferenceRecorder extends ReferenceRecorder {
    private int maxCommits = 100;
    private boolean skipUnknownCommits = false;

    /**
     * Creates a new instance of {@link GitReferenceRecorder}.
     */
    @DataBoundConstructor
    public GitReferenceRecorder() {
        this(new JenkinsFacade());
    }

    @VisibleForTesting
    GitReferenceRecorder(final JenkinsFacade jenkins) {
        super(jenkins);
    }

    /**
     * Sets the maximal number of additional commits in the reference job that will be considered during the
     * comparison with the current branch. To avoid an indefinite scanning of the build history until a
     * matching reference has been found, this value is used as a stop criteria.
     *
     * @param maxCommits
     *         maximal number of commits
     */
    @DataBoundSetter
    public void setMaxCommits(final int maxCommits) {
        this.maxCommits = maxCommits;
    }

    public int getMaxCommits() {
        return maxCommits;
    }

    /**
     * If enabled, then a build of the reference job will be skipped if one of the commits is unknown in the current
     * branch.
     *
     * @param skipUnknownCommits
     *         if {@code true} then builds with unknown commits will be skipped, otherwise unknown commits will be
     *         ignored
     */
    @DataBoundSetter
    public void setSkipUnknownCommits(final boolean skipUnknownCommits) {
        this.skipUnknownCommits = skipUnknownCommits;
    }

    public boolean isSkipUnknownCommits() {
        return skipUnknownCommits;
    }

    @Override
    protected Optional<Run<?, ?>> find(final Run<?, ?> owner, final Run<?, ?> lastCompletedBuildOfReferenceJob,
            final FilteredLog log) {
        var referenceCommit = GitCommitsRecord.findRecordForScm(lastCompletedBuildOfReferenceJob, getScm());

        Optional<Run<?, ?>> referenceBuild;
        if (referenceCommit.isPresent()) {
            referenceBuild = findByCommits(owner, referenceCommit.get(), log);
        }
        else {
            log.logInfo("-> selected build '%s' of reference job does not yet contain a `GitCommitsRecord`",
                    lastCompletedBuildOfReferenceJob.getDisplayName());
            referenceBuild = Optional.empty();
        }

        if (referenceBuild.isPresent()) {
            return referenceBuild;
        }

        var targetBranchHead = findTargetBranchHead(owner.getParent());
        if (targetBranchHead.isPresent()) {
            log.logInfo("-> falling back to latest build '%s' since a pull or merge request has been detected",
                    lastCompletedBuildOfReferenceJob.getDisplayName());
            return Optional.of(lastCompletedBuildOfReferenceJob);
        }
        log.logInfo("-> no reference build found");

        return Optional.empty();
    }

    private Optional<Run<?, ?>> findByCommits(final Run<?, ?> owner, final GitCommitsRecord referenceCommit,
            final FilteredLog log) {
        var ownerCommits = GitCommitsRecord.findRecordForScm(owner, getScm());
        if (ownerCommits.isPresent()) {
            var commitsRecord = ownerCommits.get();
            var referencePoint = commitsRecord.getReferencePoint(referenceCommit, getMaxCommits(), isSkipUnknownCommits(), log);
            if (referencePoint.isPresent()) {
                var referenceBuild = referencePoint.get();
                log.logInfo("-> found build '%s' in reference job with matching commits",
                        referenceBuild.getDisplayName());

                return referencePoint;
            }
            else {
                log.logInfo("-> found no build with matching commits");
            }
        }
        else {
            log.logInfo("-> found no `GitCommitsRecord` in current build '%s'", owner.getDisplayName());
        }
        return Optional.empty();
    }

    @Override
    @SuppressFBWarnings("BC")
    public Descriptor getDescriptor() {
        return (Descriptor) super.getDescriptor();
    }

    /**
     * Descriptor for this step: defines the symbol for the pipeline.
     */
    @Extension
    @Symbol("discoverGitReferenceBuild")
    public static class Descriptor extends SimpleReferenceRecorderDescriptor {
        /**
         * Creates a new instance of {@link Descriptor}.
         */
        public Descriptor() {
            this(new JenkinsFacade());
        }

        @VisibleForTesting
        Descriptor(final JenkinsFacade jenkins) {
            super(jenkins);
        }

        @NonNull
        @Override
        public String getDisplayName() {
            return Messages.Recorder_DisplayName();
        }
    }
}
