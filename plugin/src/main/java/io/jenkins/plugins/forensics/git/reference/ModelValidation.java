package io.jenkins.plugins.forensics.git.reference;

import org.apache.commons.lang3.StringUtils;

import edu.hm.hafner.util.VisibleForTesting;

import hudson.util.ComboBoxModel;
import hudson.util.FormValidation;

import io.jenkins.plugins.util.JenkinsFacade;

import static io.jenkins.plugins.forensics.git.reference.GitReferenceRecorder.*;

/**
 * Validates all properties of a configuration of a static analysis tool in a job.
 *
 * @author Ullrich Hafner
 */
@SuppressWarnings("PMD.GodClass")
public class ModelValidation {
    private final JenkinsFacade jenkins;

    /** Creates a new descriptor. */
    public ModelValidation() {
        this(new JenkinsFacade());
    }

    @VisibleForTesting
    ModelValidation(final JenkinsFacade jenkins) {
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
        model.add(0, NO_REFERENCE_JOB); // make sure that no input is valid
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
        if (NO_REFERENCE_JOB.equals(referenceJobName)
                || StringUtils.isBlank(referenceJobName)
                || jenkins.getJob(referenceJobName).isPresent()) {
            return FormValidation.ok();
        }
        return FormValidation.error(Messages.FieldValidator_Error_ReferenceJobDoesNotExist());
    }
}
