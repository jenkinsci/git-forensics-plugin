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
import io.jenkins.plugins.forensics.util.FilteredLog;
import jenkins.model.Jenkins;
import jenkins.tasks.SimpleBuildStep;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Optional;

/**
 * Recorder for finding reference Jobs and intersection Points in the git history.
 * PostBuildAction
 *
 * @author Ullrich Hafner
 * @author Arne Schöntag
 */
@Extension(ordinal = 10_000)
@SuppressWarnings("unused")
public class GitReferenceRecorder extends ReferenceRecorder implements SimpleBuildStep {

    FilteredLog log = new FilteredLog("GitReferenceRecorder");

    @DataBoundConstructor
    public GitReferenceRecorder() {
        super();
    }

    @Override
    @SuppressWarnings("unchecked")
    public void perform(final Run<?, ?> run, final FilePath workspace, final Launcher launcher, final TaskListener listener) {
        setRun(run);
        if (!NO_REFERENCE_JOB.equals(getReferenceJobName()) && getReferenceJobName() != null) {
            Jenkins jenkins = Jenkins.getInstanceOrNull();
            Optional<Job<?, ?>> referenceJob =  Optional.ofNullable(jenkins.getItemByFullName(getReferenceJobName(), Job.class));

            referenceJob.ifPresent(job ->  getRun().addAction(new GitBranchMasterIntersectionFinder(getRun(), getMaxCommits(), job.getLastCompletedBuild())));
            if (!referenceJob.isPresent()) {
                log.logInfo("ReferenceJob not found");
            } else {
                log.logInfo("ReferenceJob: " + referenceJob.get().getDisplayName());
            }

            log.getInfoMessages().forEach(listener.getLogger()::println);
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
