package io.jenkins.plugins.git.forensics.reference;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.Job;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Publisher;
import io.jenkins.plugins.forensics.reference.ReferenceRecorder;
import jenkins.model.Jenkins;
import jenkins.tasks.SimpleBuildStep;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.Nonnull;
import java.util.Optional;

/**
 * Recorder for finding reference Jobs and intersection Points in the git history.
 * PostBuildAction
 *
 * @author Ullrich Hafner
 * @author Arne Schöntag
 */
@Extension(ordinal = 10000)
@SuppressWarnings("unused")
public class GitReferenceRecorder extends ReferenceRecorder implements SimpleBuildStep {


    @DataBoundConstructor
    public GitReferenceRecorder() {
        super();
    }

    @Override
    @SuppressWarnings("unchecked")
    public void perform(@Nonnull final Run<?, ?> run, @Nonnull final FilePath workspace, @Nonnull final Launcher launcher, @Nonnull final TaskListener listener) {
        setRun(run);
        if (!NO_REFERENCE_JOB.equals(getReferenceJobName()) && getReferenceJobName() != null) {
            Jenkins jenkins = Jenkins.getInstanceOrNull();
            Optional<Job<?, ?>> referenceJob =  Optional.ofNullable(jenkins.getItemByFullName(getReferenceJobName(), Job.class));
            referenceJob.ifPresent(job -> getRun().addAction(new GitBranchMasterIntersectionFinder(getRun(), getMaxCommits(), job.getLastCompletedBuild())));
        }
    }


    /**
     * Descriptor for this step: defines the context and the UI elements.
     */
    @Extension
    @Symbol("gitForensics")
    @SuppressWarnings("unused") // most methods are used by the corresponding jelly view
    public static class Descriptor extends BuildStepDescriptor<Publisher> {

        @Nonnull
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
