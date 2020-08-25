package io.jenkins.plugins.forensics.git.reference;

import java.util.Optional;

import edu.hm.hafner.util.VisibleForTesting;

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
@Extension(ordinal = 10_000)
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
        GitCommitsRecord thisCommit = owner.getAction(GitCommitsRecord.class);
        GitCommitsRecord referenceCommit = lastCompletedBuildOfReferenceJob.getAction(GitCommitsRecord.class);

        return thisCommit.getReferencePoint(referenceCommit, getMaxCommits(), isSkipUnknownCommits());
    }

    @Override
    public Descriptor getDescriptor() {
        return (Descriptor)super.getDescriptor();
    }

    /**
     * Descriptor for this step: defines the symbol for the pipeline.
     */
    @Extension
    @Symbol("gitForensics")
    // TODO: should the symbol be part of the API?
    public static class Descriptor extends ReferenceRecorderDescriptor {
        // no special handling required for Git
    }
}
