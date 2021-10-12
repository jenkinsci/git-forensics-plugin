package io.jenkins.plugins.forensics.git.reference;

import java.util.Optional;

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
     * Sets the maximal number of commits that will be compared with the builds of the reference job to find the
     * matching reference build.
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
    protected Optional<Run<?, ?>> find(final Run<?, ?> owner, final Run<?, ?> lastCompletedBuildOfReferenceJob) {
        Optional<GitCommitsRecord> referenceCommit = GitCommitsRecord.findRecordForScm(lastCompletedBuildOfReferenceJob, getScm());
        if (!referenceCommit.isPresent()) {
            return Optional.empty();
        }

        Optional<GitCommitsRecord> thisCommit = GitCommitsRecord.findRecordForScm(owner, getScm());
        if (thisCommit.isPresent()) {
            return thisCommit.get().getReferencePoint(referenceCommit.get(), getMaxCommits(), isSkipUnknownCommits());
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
        public FormValidation doCheckReferenceJob(@AncestorInPath final AbstractProject<?, ?> project, @QueryParameter final String referenceJob) {
            if (!JENKINS.hasPermission(Item.CONFIGURE)) {
                return FormValidation.ok();
            }
            return model.validateJob(referenceJob);
        }
    }
}
