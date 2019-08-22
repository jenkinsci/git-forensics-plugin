package io.jenkins.plugins.git.forensics.reference;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.*;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Publisher;
import io.jenkins.plugins.forensics.reference.ReferenceRecorder;
import jenkins.model.Jenkins;
import jenkins.tasks.SimpleBuildStep;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.Nonnull;
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
@SuppressWarnings("unused")
public class GitReferenceRecorder extends ReferenceRecorder implements SimpleBuildStep {


    @DataBoundConstructor
    public GitReferenceRecorder() {
        super();
    }

    @Override
    public void perform(@Nonnull Run<?, ?> run, @Nonnull FilePath workspace, @Nonnull Launcher launcher, @Nonnull TaskListener listener) throws InterruptedException, IOException {
        this.run = run;
        if (!NO_REFERENCE_JOB.equals(referenceJobName) && referenceJobName != null) {
            Jenkins jenkins = Jenkins.getInstanceOrNull();
            Optional<Job<?, ?>> referenceJob =  Optional.ofNullable(jenkins.getItemByFullName(referenceJobName, Job.class));
            referenceJob.ifPresent(job -> run.addAction(new GitBranchMasterIntersectionFinder(run, maxCommits, job.getLastBuild())));
        }
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
