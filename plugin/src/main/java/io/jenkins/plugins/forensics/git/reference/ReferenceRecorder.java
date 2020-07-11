package io.jenkins.plugins.forensics.git.reference;

import org.kohsuke.stapler.DataBoundSetter;
import hudson.model.Run;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Recorder;

/**
 * Recorder for the Intersection Finder.
 *
 * @author Arne Schöntag
 */
@SuppressWarnings("PMD.DataClass")
public abstract class ReferenceRecorder extends Recorder {

    /**
     * String value that indicates that no reference job is given.
     */
    public static final String NO_REFERENCE_JOB = "-";

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
}
