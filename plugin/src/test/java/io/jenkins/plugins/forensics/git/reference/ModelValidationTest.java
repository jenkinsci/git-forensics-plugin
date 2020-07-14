package io.jenkins.plugins.forensics.git.reference;

import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import hudson.model.Job;
import hudson.util.ComboBoxModel;
import hudson.util.FormValidation.Kind;

import io.jenkins.plugins.util.JenkinsFacade;

import static io.jenkins.plugins.forensics.git.assertions.SoftAssertions.*;
import static io.jenkins.plugins.forensics.git.reference.GitReferenceRecorder.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests the class {@link ModelValidation}.
 *
 * @author Arne Schöntag
 * @author Stephan Plöderl
 * @author Ullrich Hafner
 */
class ModelValidationTest {
    @Test
    void doFillReferenceJobItemsShouldBeNotEmpty() {
        JenkinsFacade jenkins = mock(JenkinsFacade.class);
        when(jenkins.getAllJobNames()).thenReturn(new HashSet<>());

        ModelValidation model = new ModelValidation(jenkins);

        assertThat(model.getAllJobs()).containsExactly(NO_REFERENCE_JOB);
    }

    @Test
    void doCheckReferenceJobShouldBeOkWithValidValues() {
        JenkinsFacade jenkins = mock(JenkinsFacade.class);
        Job<?, ?> job = mock(Job.class);
        String jobName = "referenceJob";
        when(jenkins.getJob(jobName)).thenReturn(Optional.of(job));
        ModelValidation model = new ModelValidation(jenkins);

        assertSoftly(softly -> {
            softly.assertThat(model.validateJob(jobName).kind).isEqualTo(Kind.OK);
            softly.assertThat(model.validateJob(NO_REFERENCE_JOB).kind).isEqualTo(Kind.OK);
            softly.assertThat(model.validateJob("").kind).isEqualTo(Kind.OK);
        });
    }

    @Test
    void doCheckReferenceJobShouldBeNOkWithInvalidValue() {
        String referenceJob = "referenceJob";
        JenkinsFacade jenkins = mock(JenkinsFacade.class);
        when(jenkins.getJob(referenceJob)).thenReturn(Optional.empty());
        ModelValidation model = new ModelValidation(jenkins);

        assertThat(model.validateJob(referenceJob).kind).isEqualTo(Kind.ERROR);
        assertThat(model.validateJob(referenceJob).getLocalizedMessage()).isEqualTo(
                "There is no such job - maybe the job has been renamed?");
    }

    @Test
    void shouldContainEmptyJobPlaceHolder() {
        JenkinsFacade jenkins = mock(JenkinsFacade.class);
        ModelValidation model = new ModelValidation(jenkins);
        ComboBoxModel actualModel = model.getAllJobs();

        assertThat(actualModel).hasSize(1);
        assertThat(actualModel).containsExactly(NO_REFERENCE_JOB);
    }

    @Test
    void shouldContainSingleElementAndPlaceHolder() {
        JenkinsFacade jenkins = mock(JenkinsFacade.class);
        Job<?, ?> job = mock(Job.class);
        String name = "Job Name";
        when(jenkins.getFullNameOf(job)).thenReturn(name);
        when(jenkins.getAllJobNames()).thenReturn(Collections.singleton(name));

        ModelValidation model = new ModelValidation(jenkins);

        ComboBoxModel actualModel = model.getAllJobs();

        assertThat(actualModel).hasSize(2);
        assertThat(actualModel).containsExactly(NO_REFERENCE_JOB, name);
    }
}
