package io.jenkins.plugins.forensics.git.reference;

import java.util.Optional;

import edu.hm.hafner.util.FilteredLog;
import edu.hm.hafner.util.VisibleForTesting;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.verb.POST;
import org.jenkinsci.Symbol;
import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.model.Item;
import hudson.model.Run;
import hudson.util.ComboBoxModel;
import hudson.util.FormValidation;
import jenkins.scm.api.SCMHead;

import io.jenkins.plugins.forensics.reference.ReferenceRecorder;
import io.jenkins.plugins.util.JenkinsFacade;

/**
 * Recorder that finds a reference build that matches best with the current build of a given Git branch.
 *
 * @author Arne Schöntag
 * @author Ullrich Hafner
 */
@SuppressWarnings("PMD.DataClass")
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
     * comparison with the current branch. In order to avoid an indefinite scanning of the build history until a
     * matching reference has been found this value is used as a stop criteria.
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
        Optional<GitCommitsRecord> referenceCommit
                = GitCommitsRecord.findRecordForScm(lastCompletedBuildOfReferenceJob, getScm());
        if (referenceCommit.isPresent()) {
            Optional<GitCommitsRecord> thisCommit = GitCommitsRecord.findRecordForScm(owner, getScm());
            if (thisCommit.isPresent()) {
                GitCommitsRecord commitsRecord = thisCommit.get();
                Optional<Run<?, ?>> referencePoint = commitsRecord.getReferencePoint(
                        referenceCommit.get(), getMaxCommits(), isSkipUnknownCommits(), log);
                if (referencePoint.isPresent()) {
                    log.logInfo("-> found build '%s' in reference job with matching commits",
                            referencePoint.get().getDisplayName());

                    return referencePoint;
                }
            }
            else {
                log.logInfo("-> current build '%s' does not yet contain a `GitCommitsRecord`",
                        owner.getDisplayName());
            }
        }
        else {
            log.logInfo("-> selected build '%s' of reference job does not yet contain a `GitCommitsRecord`",
                    lastCompletedBuildOfReferenceJob.getDisplayName());
        }

        Optional<SCMHead> targetBranchHead = findTargetBranchHead(owner.getParent());
        if (targetBranchHead.isPresent()) {
            log.logInfo("-> falling back to latest build '%s' since a pull or merge request has been detected",
                    lastCompletedBuildOfReferenceJob.getDisplayName());
            return Optional.of(lastCompletedBuildOfReferenceJob);
        }
        log.logInfo("-> no reference build found");

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
        private static final JenkinsFacade JENKINS = new JenkinsFacade();

        @NonNull
        @Override
        public String getDisplayName() {
            return Messages.Recorder_DisplayName();
        }

        private final GitReferenceJobModelValidation model = new GitReferenceJobModelValidation();

        /**
         * Returns the model with the possible reference jobs.
         *
         * @param project
         *         the project that is configured
         *
         * @return the model with the possible reference jobs
         */
        @Override
        @POST
        public ComboBoxModel doFillReferenceJobItems(@AncestorInPath final AbstractProject<?, ?> project) {
            if (JENKINS.hasPermission(Item.CONFIGURE)) {
                return model.getAllJobs();
            }
            return new ComboBoxModel();
        }

        /**
         * Performs on-the-fly validation of the reference job.
         *
         * @param project
         *         the project that is configured
         * @param referenceJob
         *         the reference job
         *
         * @return the validation result
         */
        @Override
        @POST
        @SuppressWarnings("unused") // Used in jelly validation
        public FormValidation doCheckReferenceJob(@AncestorInPath final AbstractProject<?, ?> project,
                @QueryParameter final String referenceJob) {
            if (!JENKINS.hasPermission(Item.CONFIGURE)) {
                return FormValidation.ok();
            }
            return model.validateJob(referenceJob);
        }
    }
}
