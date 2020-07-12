package io.jenkins.plugins.forensics.git.reference;

import java.util.Optional;

import edu.hm.hafner.util.FilteredLog;
import edu.umd.cs.findbugs.annotations.NonNull;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.jenkinsci.Symbol;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.Job;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import jenkins.branch.MultiBranchProject;
import jenkins.tasks.SimpleBuildStep;

import io.jenkins.plugins.util.JenkinsFacade;

/**
 * Recorder that finds a reference build for the current build.
 *
 * @author Ullrich Hafner
 * @author Arne Schöntag
 */
@Extension(ordinal = 10_000)
@SuppressWarnings("unused")
public class GitReferenceRecorder extends Recorder implements SimpleBuildStep {
    /**
     * String value that indicates that no reference job is given.
     */
    public static final String NO_REFERENCE_JOB = "-";
    private static final String DEFAULT_BRANCH = "master";
    /**
     * The Jenkins build.
     */
    private Run<?, ?> run;
    /**
     * The name of the build. Will be used to find the reference job in Jenkins.
     */
    private String referenceJobName = "";
    /**
     * Indicates the maximal amount of commits which will be compared to find the intersection point.
     */
    private int maxCommits = 100;
    /**
     * If enabled, if a build of the reference job has more than one commit the build will be skipped if one of the commits is unknown to the current branch.
     */
    private boolean skipUnknownCommits = false;
    /**
     * If enabled, the newest build of the reference job will be taken if no intersection was found.
     */
    private boolean newestBuildIfNotFound = false;
    private String id;
    private String name;

    /**
     * Creates a new instance of {@link GitReferenceRecorder}.
     */
    @DataBoundConstructor
    public GitReferenceRecorder() {
        super();

        // empty constructor required for Stapler
    }

    @Override
    public void perform(@NonNull final Run<?, ?> run, @NonNull final FilePath workspace,
            @NonNull final Launcher launcher, @NonNull final TaskListener listener) {
        FilteredLog log = new FilteredLog("GitReferenceRecorder");
        setRun(run);

        String referenceJobName = getReferenceJobName();
        // Check if build is part of a multibranch pipeline
        if (run.getParent().getParent() instanceof MultiBranchProject && !isReferenceJobNameSet(referenceJobName)) {
            referenceJobName = buildReferenceJobName(run, log);
        }

        if (isReferenceJobNameSet(referenceJobName)) {
            JenkinsFacade jenkins = new JenkinsFacade();
            Optional<Job<?, ?>> referenceJob = jenkins.getJob(referenceJobName);
            referenceJob.ifPresent(job -> getRun().addAction(
                    new GitBranchMasterIntersectionFinder(getRun(), getMaxCommits(), isSkipUnknownCommits(),
                            isNewestBuildIfNotFound(), job.getLastCompletedBuild())));
            if (!referenceJob.isPresent()) {
                log.logInfo("ReferenceJob not found");
            }
            else {
                log.logInfo("ReferenceJob: " + referenceJob.get().getDisplayName());
            }
        }

        log.getInfoMessages().forEach(listener.getLogger()::println);

    }

    /**
     * Helping method to build a job name to look for in a multibranch pipeline.
     *
     * @param run
     *         the current job
     * @param log
     *         for logging
     *
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
     *
     * @param name
     *         given name to check
     *
     * @return true if name is not null, an empty string or '-'
     */
    private boolean isReferenceJobNameSet(String name) {
        return name != null && !"".equals(name) && !NO_REFERENCE_JOB.equals(name);
    }

    @Override
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    /**
     * Sets the reference job to get the results for the issue difference computation.
     *
     * @param referenceJobName
     *         the name of reference job
     */
    @DataBoundSetter
    public void setReferenceJobName(final String referenceJobName) {
        if (NO_REFERENCE_JOB.equals(referenceJobName)) {
            this.referenceJobName = "";
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
        if ("".equals(referenceJobName)) {
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

    public boolean isSkipUnknownCommits() {
        return skipUnknownCommits;
    }

    @DataBoundSetter
    public void setSkipUnknownCommits(boolean skipUnknownCommits) {
        this.skipUnknownCommits = skipUnknownCommits;
    }

    public boolean isNewestBuildIfNotFound() {
        return newestBuildIfNotFound;
    }

    @DataBoundSetter
    public void setNewestBuildIfNotFound(boolean newestBuildIfNotFound) {
        this.newestBuildIfNotFound = newestBuildIfNotFound;
    }

    public String getId() {
        return id;
    }

    @DataBoundSetter
    public void setId(final String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    @DataBoundSetter
    public void setName(final String name) {
        this.name = name;
    }

    public Run<?, ?> getRun() {
        return run;
    }

    public void setRun(final Run<?, ?> run) {
        this.run = run;
    }

    /**
     * Descriptor for this step: defines the context and the UI elements.
     */
    @Extension
    @Symbol("gitForensics")
    @SuppressWarnings("unused") // most methods are used by the corresponding jelly view
    public static class Descriptor extends BuildStepDescriptor<Publisher> {
        @NonNull @Override
        public String getDisplayName() {
            return "Git Forensics Recorder";
        }

        @Override
        public boolean isApplicable(final Class<? extends AbstractProject> jobType) {
            return true;
        }
    }
}
