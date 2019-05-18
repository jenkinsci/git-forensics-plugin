package io.jenkins.plugins.git.forensics.reference;

import hudson.Extension;
import hudson.tasks.Recorder;
import org.apache.commons.lang3.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

/**
 * Recorder for finding reference Jobs and intersection Points in the git history.
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

    //TODO call reference classes to find intersection/log git commits.

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
}
