package io.jenkins.plugins.forensics.git.reference;

import org.apache.commons.lang3.StringUtils;

import edu.hm.hafner.util.VisibleForTesting;

import hudson.util.ComboBoxModel;
import hudson.util.FormValidation;

import io.jenkins.plugins.forensics.reference.ReferenceRecorder;
import io.jenkins.plugins.util.JenkinsFacade;

/**
 * Validates all properties of a configuration of a reference job.
 *
 * @author Ullrich Hafner
 */
class GitReferenceJobModelValidation {
    private final JenkinsFacade jenkins;

    /** Creates a new descriptor. */
    GitReferenceJobModelValidation() {
        this(new JenkinsFacade());
    }

    @VisibleForTesting
    GitReferenceJobModelValidation(final JenkinsFacade jenkins) {
        super();

        this.jenkins = jenkins;
    }

    /**
     * Returns the model with the possible reference jobs.
     *
     * @return the model with the possible reference jobs
     */
    public ComboBoxModel getAllJobs() {
        ComboBoxModel model = new ComboBoxModel(jenkins.getAllJobNames());
        model.add(0, ReferenceRecorder.NO_REFERENCE_JOB); // make sure that no input is valid
        return model;
    }

    /**
     * Performs on-the-fly validation of the reference job.
     *
     * @param referenceJobName
     *         the reference job
     *
     * @return the validation result
     */
    public FormValidation validateJob(final String referenceJobName) {
        if (ReferenceRecorder.NO_REFERENCE_JOB.equals(referenceJobName)
                || StringUtils.isBlank(referenceJobName)
                || jenkins.getJob(referenceJobName).isPresent()) {
            return FormValidation.ok();
        }
        return FormValidation.error(Messages.FieldValidator_Error_ReferenceJobDoesNotExist());
    }
}
