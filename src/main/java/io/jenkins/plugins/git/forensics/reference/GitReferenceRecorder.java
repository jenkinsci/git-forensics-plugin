package io.jenkins.plugins.git.forensics.reference;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Job;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import jenkins.model.Jenkins;
import org.apache.commons.lang3.StringUtils;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import java.io.IOException;
import java.util.Optional;

/**
 * Recorder for finding reference Jobs and intersection Points in the git history.
 * PostBuildAction
 *
 * @author Ullrich Hafner
 * @author Arne Schöntag
 */
@Extension(ordinal=10000)
public class GitReferenceRecorder extends Recorder {
    private static final String NO_REFERENCE_JOB = "-";

    private String referenceJobName;

    private int maxCommits;

    // Needed to register in Jenkins?
    private String id;
    private String name;

    @DataBoundConstructor
    public GitReferenceRecorder() {
        super();
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        if (!NO_REFERENCE_JOB.equals(referenceJobName) && referenceJobName != null) {
            Jenkins jenkins = Jenkins.getInstanceOrNull();
            if (jenkins == null) {
                return false;
            }
            Optional<Job<?, ?>> referenceJob =  Optional.ofNullable(jenkins.getItemByFullName(referenceJobName, Job.class));
            if (referenceJob.isPresent()) {
                build.addAction(new GitBranchMasterIntersectionFinder(build, maxCommits, referenceJob.get().getLastBuild()));
            }
        }
        return true;
    }

    // Partly copied from IssuesRecorder.java (warnings-ng)

    /**
     * Sets the reference job to get the results for the issue difference computation.
     *
     * @param referenceJobName
     *         the name of reference job
     */
    @DataBoundSetter
    public void setReferenceJobName(final String referenceJobName) {
        if (NO_REFERENCE_JOB.equals(referenceJobName)) {
            this.referenceJobName = StringUtils.EMPTY;
        }
        this.referenceJobName = referenceJobName;
    }

    /**
     * Returns the reference job to get the results for the issue difference computation. If the job is not defined,
     * then {@link #NO_REFERENCE_JOB} is returned.
     *
     * @return the name of reference job, or {@link #NO_REFERENCE_JOB} if undefined
     */
    public String getReferenceJobName() {
        if (StringUtils.isBlank(referenceJobName)) {
            return NO_REFERENCE_JOB;
        }
        return referenceJobName;
    }

    public int getMaxCommits() {
        return maxCommits;
    }

    @DataBoundSetter
    public void setMaxCommits(final int maxCommits) {
        this.maxCommits = maxCommits;
    }

    public String getId() {
        return id;
    }

    @DataBoundSetter
    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    @DataBoundSetter
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Descriptor for this step: defines the context and the UI elements.
     */
    @Extension
    @Symbol("gitForensics")
    @SuppressWarnings("unused") // most methods are used by the corresponding jelly view
    public static class Descriptor extends BuildStepDescriptor<Publisher> {



        @NonNull
        @Override
        public String getDisplayName() {
            return "Git Forensics Recorder";
        }

        @Override
        public boolean isApplicable(final Class<? extends AbstractProject> jobType) {
            return true;
        }


    }
}
