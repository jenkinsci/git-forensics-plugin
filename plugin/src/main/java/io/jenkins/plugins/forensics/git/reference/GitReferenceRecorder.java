package io.jenkins.plugins.forensics.git.reference;

import java.util.Optional;

import org.apache.commons.lang3.StringUtils;

import edu.hm.hafner.util.FilteredLog;
import edu.hm.hafner.util.VisibleForTesting;
import edu.umd.cs.findbugs.annotations.NonNull;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.jenkinsci.Symbol;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.ItemGroup;
import hudson.model.Job;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import hudson.util.ComboBoxModel;
import hudson.util.FormValidation;
import jenkins.branch.MultiBranchProject;
import jenkins.tasks.SimpleBuildStep;

import io.jenkins.plugins.util.JenkinsFacade;
import io.jenkins.plugins.util.LogHandler;

/**
 * Recorder that finds a reference build that matches best with the current build of a given branch.
 *
 * @author Arne Schöntag
 * @author Ullrich Hafner
 */
@Extension(ordinal = 10_000)
// TODO: for MultiBranch jobs there should be no need to call the recorder
public class GitReferenceRecorder extends Recorder implements SimpleBuildStep {
    private static final String DEFAULT_BRANCH = "master";

    static final String NO_REFERENCE_JOB = "-";

    private String referenceJob = StringUtils.EMPTY;
    private String defaultBranch = DEFAULT_BRANCH;
    private int maxCommits = 100;
    private boolean skipUnknownCommits = false;
    private boolean latestBuildIfNotFound = false;

    private final JenkinsFacade jenkins;

    /**
     * Creates a new instance of {@link GitReferenceRecorder}.
     */
    @DataBoundConstructor
    public GitReferenceRecorder() {
        this(new JenkinsFacade());
    }

    @VisibleForTesting
    GitReferenceRecorder(final JenkinsFacade jenkins) {
        super();

        this.jenkins = jenkins;
    }

    /**
     * Sets the default branch for {@link MultiBranchProject multi-branch projects}: the default branch is considered
     * the base branch in your repository. The builds of all other branches and pull requests will use this default
     * branch as baseline to search for a matching reference build.
     *
     * @param defaultBranch
     *         the name of the default branch
     */
    @DataBoundSetter
    public void setDefaultBranch(final String defaultBranch) {
        this.defaultBranch = StringUtils.defaultIfBlank(StringUtils.strip(defaultBranch), DEFAULT_BRANCH);
    }

    public String getDefaultBranch() {
        return defaultBranch;
    }

    /**
     * Sets the reference job: this job will be used as base line to search for the best matching reference build. If
     * the reference job should be computed automatically (supported by {@link MultiBranchProject multi-branch projects}
     * only), then let this field empty.
     *
     * @param referenceJob
     *         the name of reference job
     */
    @DataBoundSetter
    public void setReferenceJob(final String referenceJob) {
        if (NO_REFERENCE_JOB.equals(referenceJob)) {
            this.referenceJob = StringUtils.EMPTY;
        }
        this.referenceJob = StringUtils.strip(referenceJob);
    }

    /**
     * Returns the name of the reference job. If the job is not defined, then {@link #NO_REFERENCE_JOB} is returned.
     *
     * @return the name of reference job, or {@link #NO_REFERENCE_JOB} if undefined
     */
    @SuppressWarnings("unused") // Required by Stapler
    public String getReferenceJob() {
        if (StringUtils.isBlank(referenceJob)) {
            return NO_REFERENCE_JOB;
        }
        return referenceJob;
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

    /**
     * If enabled, then the latest build of the reference job will be used if no other reference build has been found.
     *
     * @param latestBuildIfNotFound
     *         if {@code true} then the latest build of the reference job will be used if no matching reference build
     *         has been found, otherwise no reference build is returned.
     */
    @DataBoundSetter
    public void setLatestBuildIfNotFound(final boolean latestBuildIfNotFound) {
        this.latestBuildIfNotFound = latestBuildIfNotFound;
    }

    public boolean isLatestBuildIfNotFound() {
        return latestBuildIfNotFound;
    }

    @Override
    public void perform(@NonNull final Run<?, ?> run, @NonNull final FilePath workspace,
            @NonNull final Launcher launcher, @NonNull final TaskListener listener) {
        FilteredLog log = new FilteredLog("Errors while computing the reference build");

        findReferenceBuild(run, log);

        LogHandler logHandler = new LogHandler(listener, "GitReferenceFinder");
        logHandler.log(log);
    }

    private void findReferenceBuild(final Run<?, ?> run, final FilteredLog log) {
        Optional<Job<?, ?>> actualReferenceJob = getReferenceJob(run, log);
        if (actualReferenceJob.isPresent()) {
            Job<?, ?> reference = actualReferenceJob.get();
            log.logInfo("Finding reference build for `%s`", reference.getFullDisplayName());
            Run<?, ?> lastCompletedBuild = reference.getLastCompletedBuild();
            if (lastCompletedBuild == null) {
                log.logInfo("-> no completed build found");
            }
            else {
                GitReferenceBuild action = new GitReferenceBuild(run, getMaxCommits(),
                        isSkipUnknownCommits(), isLatestBuildIfNotFound(), lastCompletedBuild);
                run.addAction(action);
                log.logInfo("-> found `%s`", action.getReferenceBuildId());
            }
        }
        else {
            log.logInfo("Reference job '%s' not found", this.referenceJob);
        }
    }

    private Optional<Job<?, ?>> getReferenceJob(final Run<?, ?> run, final FilteredLog log) {
        if (isValidJobName(referenceJob)) {
            log.logInfo("Using configured reference job name" + referenceJob);
            log.logInfo("-> " + referenceJob);
            return jenkins.getJob(referenceJob);
        }
        else {
            Job<?, ?> job = run.getParent();
            ItemGroup<?> topLevel = job.getParent();
            if (topLevel instanceof MultiBranchProject) {
                if (DEFAULT_BRANCH.equals(job.getDisplayName())) {
                    log.logInfo("No reference job obtained since we are on the default branch");
                }
                else {
                    log.logInfo("Obtaining reference job name from toplevel item `%s`", topLevel.getDisplayName());
                    String referenceFromDefaultBranch = job.getParent().getFullName() + "/" + DEFAULT_BRANCH;
                    log.logInfo("-> job name: " + referenceFromDefaultBranch);
                    return jenkins.getJob(referenceFromDefaultBranch);
                }
            }
            return Optional.empty();
        }
    }

    private boolean isValidJobName(final String name) {
        return StringUtils.isNotBlank(name) && !NO_REFERENCE_JOB.equals(name);
    }

    @Override
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
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
            return Messages.Recorder_DisplayName();
        }

        @Override
        public boolean isApplicable(final Class<? extends AbstractProject> jobType) {
            return true;
        }

        private final ModelValidation model = new ModelValidation();

        /**
         * Returns the model with the possible reference jobs.
         *
         * @return the model with the possible reference jobs
         */
        public ComboBoxModel doFillReferenceJobNameItems() {
            return model.getAllJobs();
        }

        /**
         * Performs on-the-fly validation of the reference job.
         *
         * @param referenceJobName
         *         the reference job
         *
         * @return the validation result
         */
        public FormValidation doCheckReferenceJobName(@QueryParameter final String referenceJobName) {
            return model.validateJob(referenceJobName);
        }
    }
}
