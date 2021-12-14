package io.jenkins.plugins.forensics.git.reference;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import hudson.model.FreeStyleProject;
import hudson.model.Item;
import hudson.util.ComboBoxModel;
import hudson.util.FormValidation;

import io.jenkins.plugins.forensics.git.reference.GitReferenceRecorder.Descriptor;
import io.jenkins.plugins.util.JenkinsFacade;

import static io.jenkins.plugins.forensics.git.assertions.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests the class {@link GitReferenceRecorder}.
 *
 * @author Ullrich Hafner
 */
class GitReferenceRecorderTest {
    private static final String JOB_NAME = "reference";
    private static final FormValidation ERROR = FormValidation.error("error");
    private static final FormValidation OK = FormValidation.ok();

    @Nested
    class DescriptorTest {
        @Test
        void shouldValidateJobName() {
            GitReferenceJobModelValidation model = mock(GitReferenceJobModelValidation.class);
            when(model.validateJob(JOB_NAME)).thenReturn(ERROR, OK);

            JenkinsFacade jenkins = mock(JenkinsFacade.class);

            Descriptor descriptor = new Descriptor(jenkins, model);

            FreeStyleProject project = mock(FreeStyleProject.class);
            assertThat(descriptor.doCheckReferenceJob(project, JOB_NAME)).isEqualTo(OK);
            verifyNoInteractions(model);

            // Now enable permission
            when(jenkins.hasPermission(Item.CONFIGURE, project)).thenReturn(true);
            // first call stub returns ERROR
            assertThat(descriptor.doCheckReferenceJob(project, JOB_NAME)).isEqualTo(ERROR);
            // second call stub returns ERROR
            assertThat(descriptor.doCheckReferenceJob(project, JOB_NAME)).isEqualTo(OK);
        }

        @Test
        void shouldFillModel() {
            GitReferenceJobModelValidation model = mock(GitReferenceJobModelValidation.class);
            ComboBoxModel jobs = new ComboBoxModel();
            jobs.add("A Job");
            when(model.getAllJobs()).thenReturn(jobs);

            JenkinsFacade jenkins = mock(JenkinsFacade.class);

            Descriptor descriptor = new Descriptor(jenkins, model);

            FreeStyleProject project = mock(FreeStyleProject.class);
            assertThat(descriptor.doFillReferenceJobItems(project)).isEqualTo(new ComboBoxModel());
            verifyNoInteractions(model);

            // Now enable permission
            when(jenkins.hasPermission(Item.CONFIGURE, project)).thenReturn(true);
            assertThat(descriptor.doFillReferenceJobItems(project)).isEqualTo(jobs);
        }
    }
}
