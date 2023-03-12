package io.jenkins.plugins.forensics.git.delta;

import java.io.IOException;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;

import edu.hm.hafner.util.FilteredLog;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import hudson.model.FreeStyleProject;
import hudson.model.Run;
import hudson.plugins.git.GitSCM;

import io.jenkins.plugins.forensics.delta.Change;
import io.jenkins.plugins.forensics.delta.ChangeEditType;
import io.jenkins.plugins.forensics.delta.Delta;
import io.jenkins.plugins.forensics.delta.FileChanges;
import io.jenkins.plugins.forensics.delta.FileEditType;
import io.jenkins.plugins.forensics.git.reference.GitReferenceRecorder;
import io.jenkins.plugins.forensics.git.util.GitITest;

import static io.jenkins.plugins.forensics.assertions.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Integration test for the class {@link GitDeltaCalculator}.
 *
 * @author Florian Orendi
 */
class GitDeltaCalculatorITest extends GitITest {
    private static final String EMPTY_SCM_KEY = "";
    private static final String EMPTY_FILE_PATH = "";

    /**
     * The delta result should be empty if there are invalid commits.
     */
    @Test
    void shouldCreateEmptyDeltaIfCommitsAreInvalid() {
        GitDeltaCalculator deltaCalculator = createDeltaCalculator();

        FilteredLog log = createLog();
        assertThat(deltaCalculator.calculateDelta(mock(Run.class), mock(Run.class), EMPTY_SCM_KEY, log)).isEmpty();
    }

    /**
     * The Git diff file should be created properly.
     */
    @Test
    @SuppressFBWarnings(value = "BC_UNCONFIRMED_CAST_OF_RETURN_VALUE", justification = "The cast is confirmed via an assertion")
    void shouldCreateDiffFile() {
        FreeStyleProject job = createJobWithReferenceRecorder();

        GitDeltaCalculator deltaCalculator = createDeltaCalculator();
        FilteredLog log = createLog();

        Run<?, ?> referenceBuild = buildSuccessfully(job);
        String referenceCommit = getHead();
        String fileName = "newFile";
        String content = "content";
        writeFile(fileName, content);
        addFile(fileName);
        commit("test");
        Run<?, ?> build = buildSuccessfully(job);
        String currentCommit = getHead();

        Optional<Delta> result = deltaCalculator.calculateDelta(build, referenceBuild, EMPTY_SCM_KEY, log);
        assertThat(result).isNotEmpty();

        Delta delta = result.get();
        assertThat(delta).hasCurrentCommit(currentCommit);
        assertThat(delta).hasReferenceCommit(referenceCommit);
        assertThat(delta).isInstanceOfSatisfying(GitDelta.class,
                gitDelta -> assertThat(gitDelta.getDiffFile()).isEqualTo(
                        "diff --git a/newFile b/newFile\n"
                                + "new file mode 100644\n"
                                + "index 0000000..6b584e8\n"
                                + "--- /dev/null\n"
                                + "+++ b/newFile\n"
                                + "@@ -0,0 +1 @@\n"
                                + "+content\n"
                                + "\\ No newline at end of file\n"));
    }

    /**
     * An added file should be determined.
     */
    @Test
    void shouldDetermineAddedFile() {
        FreeStyleProject job = createJobWithReferenceRecorder();
        GitDeltaCalculator deltaCalculator = createDeltaCalculator();
        FilteredLog log = createLog();

        Run<?, ?> referenceBuild = buildSuccessfully(job);

        String newFileName = "newFile";
        String content = "added";
        writeFile(newFileName, content);
        addFile(newFileName);
        commit("test");
        Run<?, ?> build = buildSuccessfully(job);

        Optional<Delta> result = deltaCalculator.calculateDelta(build, referenceBuild, EMPTY_SCM_KEY, log);
        assertThat(result).isNotEmpty();

        Delta delta = result.get();
        FileChanges fileChanges = getSingleFileChanges(delta);
        assertThat(fileChanges.getFileName()).isEqualTo(newFileName);
        assertThat(fileChanges.getOldFileName()).isEqualTo(EMPTY_FILE_PATH);
        assertThat(fileChanges.getFileEditType()).isEqualTo(FileEditType.ADD);
        assertThat(fileChanges.getFileContent()).isEqualTo(content);
    }

    /**
     * A modified file should be determined.
     */
    @Test
    void shouldDetermineModifiedFile() {
        FreeStyleProject job = createJobWithReferenceRecorder();
        GitDeltaCalculator deltaCalculator = createDeltaCalculator();
        FilteredLog log = createLog();

        String content = "modified";
        commitFile("test");
        Run<?, ?> referenceBuild = buildSuccessfully(job);
        commitFile(content);
        Run<?, ?> build = buildSuccessfully(job);

        Optional<Delta> result = deltaCalculator.calculateDelta(build, referenceBuild, EMPTY_SCM_KEY, log);
        assertThat(result).isNotEmpty();

        Delta delta = result.get();
        FileChanges fileChanges = getSingleFileChanges(delta);
        assertThat(fileChanges.getFileName()).isEqualTo(INITIAL_FILE);
        assertThat(fileChanges.getOldFileName()).isEqualTo(INITIAL_FILE);
        assertThat(fileChanges.getFileEditType()).isEqualTo(FileEditType.MODIFY);
        assertThat(fileChanges.getFileContent()).isEqualTo(content);
    }

    /**
     * A deleted file should be determined.
     */
    @Test
    void shouldDetermineDeletedFile() {
        FreeStyleProject job = createJobWithReferenceRecorder();
        GitDeltaCalculator deltaCalculator = createDeltaCalculator();
        FilteredLog log = createLog();

        String content = "content";
        commitFile(content);
        Run<?, ?> referenceBuild = buildSuccessfully(job);
        git("rm", INITIAL_FILE);
        commit("test");
        Run<?, ?> build = buildSuccessfully(job);

        Optional<Delta> result = deltaCalculator.calculateDelta(build, referenceBuild, EMPTY_SCM_KEY, log);
        assertThat(result).isNotEmpty();

        Delta delta = result.get();
        FileChanges fileChanges = getSingleFileChanges(delta);
        assertThat(fileChanges.getFileName()).isEqualTo(EMPTY_FILE_PATH);
        assertThat(fileChanges.getOldFileName()).isEqualTo(INITIAL_FILE);
        assertThat(fileChanges.getFileEditType()).isEqualTo(FileEditType.DELETE);
        assertThat(fileChanges.getFileContent()).isEqualTo(content);
    }

    /**
     * A renamed file should be determined.
     */
    @Test
    void shouldDetermineRenamedFile() {
        FreeStyleProject job = createJobWithReferenceRecorder();
        GitDeltaCalculator deltaCalculator = createDeltaCalculator();
        FilteredLog log = createLog();

        String content = "content";
        commitFile(content);
        Run<?, ?> referenceBuild = buildSuccessfully(job);
        git("rm", INITIAL_FILE);
        writeFileAsAuthorFoo(content);
        Run<?, ?> build = buildSuccessfully(job);

        Optional<Delta> result = deltaCalculator.calculateDelta(build, referenceBuild, EMPTY_SCM_KEY, log);
        assertThat(result).isNotEmpty();

        Delta delta = result.get();
        FileChanges fileChanges = getSingleFileChanges(delta);
        assertThat(fileChanges.getFileName()).isEqualTo(ADDITIONAL_FILE);
        assertThat(fileChanges.getOldFileName()).isEqualTo(INITIAL_FILE);
        assertThat(fileChanges.getFileEditType()).isEqualTo(FileEditType.RENAME);
        assertThat(fileChanges.getFileContent()).isEqualTo(content);
    }

    /**
     * Added lines within a specific file should be determined.
     */
    @Test
    void shouldDetermineAddedLines() {
        FreeStyleProject job = createJobWithReferenceRecorder();
        GitDeltaCalculator deltaCalculator = createDeltaCalculator();
        FilteredLog log = createLog();

        String content = "Test\nTest\n";
        String insertedContent = "Test\nInsert1\nInsert2\nTest\n";
        commitFile(content);
        Run<?, ?> referenceBuild = buildSuccessfully(job);
        commitFile(insertedContent);
        Run<?, ?> build = buildSuccessfully(job);

        Optional<Delta> result = deltaCalculator.calculateDelta(build, referenceBuild, EMPTY_SCM_KEY, log);
        assertThat(result).isNotEmpty();

        Delta delta = result.get();
        FileChanges fileChanges = getSingleFileChanges(delta);
        Change change = getSingleChangeOfType(fileChanges, ChangeEditType.INSERT);
        assertThat(change.getEditType()).isEqualTo(ChangeEditType.INSERT);
        assertThat(change.getChangedFromLine()).isEqualTo(1);
        assertThat(change.getChangedToLine()).isEqualTo(1);
        assertThat(change.getFromLine()).isEqualTo(2);
        assertThat(change.getToLine()).isEqualTo(3);
    }

    /**
     * Modified lines within a specific file should be determined.
     */
    @Test
    void shouldDetermineModifiedLines() {
        FreeStyleProject job = createJobWithReferenceRecorder();
        GitDeltaCalculator deltaCalculator = createDeltaCalculator();
        FilteredLog log = createLog();

        String content = "Test\nTest\nTest\nTest";
        String modified = "Test\nModified\nModified2\nTest";
        commitFile(content);
        Run<?, ?> referenceBuild = buildSuccessfully(job);
        commitFile(modified);
        Run<?, ?> build = buildSuccessfully(job);

        Optional<Delta> result = deltaCalculator.calculateDelta(build, referenceBuild, EMPTY_SCM_KEY, log);
        assertThat(result).isNotEmpty();

        Delta delta = result.get();
        FileChanges fileChanges = getSingleFileChanges(delta);
        Change change = getSingleChangeOfType(fileChanges, ChangeEditType.REPLACE);
        assertThat(change.getEditType()).isEqualTo(ChangeEditType.REPLACE);
        assertThat(change.getChangedFromLine()).isEqualTo(2);
        assertThat(change.getChangedToLine()).isEqualTo(3);
        assertThat(change.getFromLine()).isEqualTo(2);
        assertThat(change.getToLine()).isEqualTo(3);
    }

    /**
     * Deleted lines within a specific file should be determined.
     */
    @Test
    void shouldDetermineDeletedLines() {
        FreeStyleProject job = createJobWithReferenceRecorder();
        GitDeltaCalculator deltaCalculator = createDeltaCalculator();
        FilteredLog log = createLog();

        String content = "Test\nTest3\nTest";
        String modified = "Test\nTest";
        commitFile(content);
        Run<?, ?> referenceBuild = buildSuccessfully(job);
        commitFile(modified);
        Run<?, ?> build = buildSuccessfully(job);

        Optional<Delta> result = deltaCalculator.calculateDelta(build, referenceBuild, EMPTY_SCM_KEY, log);
        assertThat(result).isNotEmpty();

        Delta delta = result.get();
        FileChanges fileChanges = getSingleFileChanges(delta);
        Change change = getSingleChangeOfType(fileChanges, ChangeEditType.DELETE);
        assertThat(change.getEditType()).isEqualTo(ChangeEditType.DELETE);
        assertThat(change.getChangedFromLine()).isEqualTo(2);
        assertThat(change.getChangedToLine()).isEqualTo(2);
        assertThat(change.getFromLine()).isEqualTo(1);
        assertThat(change.getToLine()).isEqualTo(1);
    }

    /**
     * Added, modified and deleted lines within a specific file should be determined properly together.
     */
    @Test
    void shouldDetermineAllChangeTypesTogether() {
        FreeStyleProject job = createJobWithReferenceRecorder();
        GitDeltaCalculator deltaCalculator = createDeltaCalculator();
        FilteredLog log = createLog();

        String content = "Test1\nTest2\nTest3\nTest4";
        String newContent = "Modified\nTest2\nInserted\nTest3";
        commitFile(content);
        Run<?, ?> referenceBuild = buildSuccessfully(job);
        commitFile(newContent);
        Run<?, ?> build = buildSuccessfully(job);

        Optional<Delta> result = deltaCalculator.calculateDelta(build, referenceBuild, EMPTY_SCM_KEY, log);
        assertThat(result).isNotEmpty();

        Delta delta = result.get();
        FileChanges fileChanges = getSingleFileChanges(delta);

        Change replace = getSingleChangeOfType(fileChanges, ChangeEditType.REPLACE);
        assertThat(replace.getEditType()).isEqualTo(ChangeEditType.REPLACE);
        assertThat(replace.getChangedFromLine()).isEqualTo(1);
        assertThat(replace.getChangedToLine()).isEqualTo(1);
        assertThat(replace.getFromLine()).isEqualTo(1);
        assertThat(replace.getToLine()).isEqualTo(1);

        Change insert = getSingleChangeOfType(fileChanges, ChangeEditType.INSERT);
        assertThat(insert.getEditType()).isEqualTo(ChangeEditType.INSERT);
        assertThat(insert.getChangedFromLine()).isEqualTo(2);
        assertThat(insert.getChangedToLine()).isEqualTo(2);
        assertThat(insert.getFromLine()).isEqualTo(3);
        assertThat(insert.getToLine()).isEqualTo(3);

        Change delete = getSingleChangeOfType(fileChanges, ChangeEditType.DELETE);
        assertThat(delete.getEditType()).isEqualTo(ChangeEditType.DELETE);
        assertThat(delete.getChangedFromLine()).isEqualTo(4);
        assertThat(delete.getChangedToLine()).isEqualTo(4);
        assertThat(delete.getFromLine()).isEqualTo(4);
        assertThat(delete.getToLine()).isEqualTo(4);
    }

    /**
     * Gets the first {@link Change} of a specific {@link ChangeEditType} within a file when there is only a single
     * change and checks if the found values are properly.
     *
     * @param fileChanges
     *         The changes within a file
     * @param type
     *         The change type
     *
     * @return the first found change
     */
    private Change getSingleChangeOfType(final FileChanges fileChanges, final ChangeEditType type) {
        Set<Change> changes = fileChanges.getChanges().get(type);
        assertThat(changes).isNotEmpty();
        assertThat(changes.size()).isEqualTo(1);
        return changes.stream().findFirst().get();
    }

    /**
     * Gets the first {@link FileChanges} of a calculated code {@link Delta}, when exactly a single file has changed and
     * checks if the found values are properly.
     *
     * @param delta
     *         The code delta
     *
     * @return the found changes.
     */
    private FileChanges getSingleFileChanges(final Delta delta) {
        final Collection<FileChanges> fileChangesList = delta.getFileChangesMap().values();
        assertThat(fileChangesList.size()).isEqualTo(1);
        Optional<FileChanges> fileChanges = fileChangesList.stream().findFirst();
        assertThat(fileChanges).isNotEmpty();
        return fileChanges.get();
    }

    /**
     * Creates and commits a file with a fixed name and with the passed file content.
     *
     * @param content
     *         The file contend
     */
    private void commitFile(final String content) {
        writeFile(INITIAL_FILE, content);
        git("add", INITIAL_FILE);
        git("config", "user.name", GitITest.FOO_NAME);
        git("config", "user.email", GitITest.FOO_EMAIL);
        git("commit", "--message=Test");
    }

    /**
     * Creates a {@link FilteredLog}.
     *
     * @return the created log
     */
    private FilteredLog createLog() {
        return new FilteredLog(StringUtils.EMPTY);
    }

    /**
     * Creates a {@link GitDeltaCalculator}.
     *
     * @return the created delta calculator
     */
    private GitDeltaCalculator createDeltaCalculator() {
        return new GitDeltaCalculator(createGitClient());
    }

    /**
     * Creates a {@link FreeStyleProject} which contains a {@link GitReferenceRecorder} within the publishers list.
     *
     * @return the created project
     */
    private FreeStyleProject createJobWithReferenceRecorder() {
        try {
            FreeStyleProject job = createFreeStyleProject();
            job.setScm(new GitSCM(getRepositoryRoot()));
            job.getPublishersList().add(new GitReferenceRecorder());
            return job;
        }
        catch (IOException exception) {
            throw new AssertionError(exception);
        }
    }
}
