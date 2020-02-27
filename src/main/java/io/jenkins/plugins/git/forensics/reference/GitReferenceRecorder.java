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
import edu.hm.hafner.util.FilteredLog;
import jenkins.model.Jenkins;
import jenkins.tasks.SimpleBuildStep;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import jenkins.branch.MultiBranchProject;

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

    private static final String DEFAULT_BRANCH = "master";

    @DataBoundConstructor
    public GitReferenceRecorder() {
        super();
    }

    @Override
    @SuppressWarnings("unchecked")
    public void perform(final Run<?, ?> run, final FilePath workspace, final Launcher launcher, final TaskListener listener) {
        FilteredLog log = new FilteredLog("GitReferenceRecorder");
        setRun(run);

        String referenceJobName = getReferenceJobName();
        // Check if build is part of a multibranch pipeline
        if (run.getParent().getParent() instanceof MultiBranchProject && !isReferenceJobNameSet(referenceJobName)) {
            referenceJobName = buildReferenceJobName(run, log);
        }

        if (isReferenceJobNameSet(referenceJobName)) {
            Jenkins jenkins = Jenkins.getInstanceOrNull();
            Optional<Job<?, ?>> referenceJob = Optional.ofNullable(jenkins.getItemByFullName(referenceJobName, Job.class));
            referenceJob.ifPresent(job -> getRun().addAction(new GitBranchMasterIntersectionFinder(getRun(), getMaxCommits(), job.getLastCompletedBuild())));
            if (!referenceJob.isPresent()) {
                log.logInfo("ReferenceJob not found");
            } else {
                log.logInfo("ReferenceJob: " + referenceJob.get().getDisplayName());
            }
        }

        log.getInfoMessages().forEach(listener.getLogger()::println);

    }

    /**
     * Helping method to build a job name to look for in a multibranch pipeline.
     * @param run the current job
     * @param log for logging
     * @return Projectname + "/master"
     */
    private String buildReferenceJobName(final Run<?, ?> run, FilteredLog log) {
        if (DEFAULT_BRANCH.equals(run.getParent().getDisplayName())) {
            // This is the master branch build - No intersection estimation necessary
            return null;
        }
        if (run.getParent().getParent() != null) {
            String result = run.getParent().getParent().getFullDisplayName() + "/" + DEFAULT_BRANCH;
            log.logInfo("Searching for " + result);
            return result;
        }
        return null;
    }

    /**
     * Helping method to determine if a name is not null, an empty string or '-'.
     * @param name given name to check
     * @return true if name is not null, an empty string or '-'
     */
    private boolean isReferenceJobNameSet(String name) {
        return name != null && !"".equals(name) && !NO_REFERENCE_JOB.equals(name);
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
