package io.jenkins.plugins.forensics.git.delta;

import edu.hm.hafner.util.FilteredLog;
import io.jenkins.plugins.forensics.delta.model.*;
import io.jenkins.plugins.forensics.git.util.GitITest;
import org.apache.commons.lang3.StringUtils;
import org.junit.Test;

import java.util.List;
import java.util.Optional;

import static io.jenkins.plugins.forensics.assertions.Assertions.assertThat;

/**
 * Integration test for the class {@link GitDeltaCalculator}.
 *
 * @author Florian Orendi
 */
public class GitDeltaCalculatorITest extends GitITest {

    /**
     * The delta result should be empty if there are invalid commits.
     */
    @Test
    public void shouldCreateEmptyDeltaIfCommitsAreInvalid() {
        GitDeltaCalculator deltaCalculator = createDeltaCalculator();

        FilteredLog log = createLog();
        Optional<Delta> delta = deltaCalculator.calculateDelta("", "", log);

        assertThat(delta).isEmpty();
    }

    /**
     * The commit boundaries should be set properly.
     */
    @Test
    public void shouldSetCommitBoundaries() {
        GitDeltaCalculator deltaCalculator = createDeltaCalculator();
        FilteredLog log = createLog();

        final String referenceCommit = getHead();
        commitFile("test");
        final String currentCommit = getHead();

        Optional<Delta> result = deltaCalculator.calculateDelta(currentCommit, referenceCommit, log);
        assertThat(result).isNotEmpty();

        Delta delta = result.get();
        assertThat(delta).hasCurrentCommit(currentCommit);
        assertThat(delta).hasReferenceCommit(referenceCommit);
    }

    /**
     * The Git diff file should be created properly.
     */
    @Test
    public void shouldCreateDiffFile() {
        GitDeltaCalculator deltaCalculator = createDeltaCalculator();
        FilteredLog log = createLog();

        final String referenceCommit = getHead();
        final String fileName = "newFile";
        final String content = "content";
        writeFile(fileName, content);
        addFile(fileName);
        commit("test");
        final String currentCommit = getHead();

        Optional<Delta> result = deltaCalculator.calculateDelta(currentCommit, referenceCommit, log);
        assertThat(result).isNotEmpty();

        Delta delta = result.get();
        assertThat(delta).hasDiffFile("diff --git a/newFile b/newFile\n"
                + "new file mode 100644\n"
                + "index 0000000..6b584e8\n"
                + "--- /dev/null\n"
                + "+++ b/newFile\n"
                + "@@ -0,0 +1 @@\n"
                + "+content\n"
                + "\\ No newline at end of file\n");
    }

    /**
     * An added file should be determined.
     */
    @Test
    public void shouldDetermineAddedFile() {
        GitDeltaCalculator deltaCalculator = createDeltaCalculator();
        FilteredLog log = createLog();

        final String referenceCommit = getHead();

        final String newFileName = "newFile";
        final String content = "added";
        writeFile(newFileName, content);
        addFile(newFileName);
        commit("test");
        final String currentCommit = getHead();

        Optional<Delta> result = deltaCalculator.calculateDelta(currentCommit, referenceCommit, log);
        assertThat(result).isNotEmpty();

        Delta delta = result.get();
        FileChanges fileChanges = getFileChanges(delta);
        assertThat(fileChanges.getFileName()).isEqualTo(newFileName);
        assertThat(fileChanges.getFileEditType()).isEqualTo(FileEditType.ADD);
        assertThat(fileChanges.getFileContent()).isEqualTo(content);
    }

    /**
     * A modified file should be determined.
     */
    @Test
    public void shouldDetermineModifiedFile() {
        GitDeltaCalculator deltaCalculator = createDeltaCalculator();
        FilteredLog log = createLog();

        final String content = "modified";
        commitFile("test");
        final String referenceCommit = getHead();
        commitFile(content);
        final String currentCommit = getHead();

        Optional<Delta> result = deltaCalculator.calculateDelta(currentCommit, referenceCommit, log);
        assertThat(result).isNotEmpty();

        Delta delta = result.get();
        FileChanges fileChanges = getFileChanges(delta);
        assertThat(fileChanges.getFileName()).isEqualTo(GitITest.INITIAL_FILE);
        assertThat(fileChanges.getFileEditType()).isEqualTo(FileEditType.MODIFY);
        assertThat(fileChanges.getFileContent()).isEqualTo(content);
    }

    /**
     * A deleted file should be determined.
     */
    @Test
    public void shouldDetermineDeletedFile() {
        GitDeltaCalculator deltaCalculator = createDeltaCalculator();
        FilteredLog log = createLog();

        final String content = "content";
        commitFile(content);
        final String referenceCommit = getHead();
        git("rm", GitITest.INITIAL_FILE);
        commit("test");
        final String currentCommit = getHead();

        Optional<Delta> result = deltaCalculator.calculateDelta(currentCommit, referenceCommit, log);
        assertThat(result).isNotEmpty();

        Delta delta = result.get();
        FileChanges fileChanges = getFileChanges(delta);
        assertThat(fileChanges.getFileName()).isEqualTo(GitITest.INITIAL_FILE);
        assertThat(fileChanges.getFileEditType()).isEqualTo(FileEditType.DELETE);
        assertThat(fileChanges.getFileContent()).isEqualTo(content);
    }

    /**
     * Added lines within a specific file should be determined.
     */
    @Test
    public void shouldDetermineAddedLines() {
        GitDeltaCalculator deltaCalculator = createDeltaCalculator();
        FilteredLog log = createLog();

        final String content = "Test\nTest\n";
        commitFile(content);
        final String referenceCommit = getHead();
        commitFile(content + content);
        final String currentCommit = getHead();

        Optional<Delta> result = deltaCalculator.calculateDelta(currentCommit, referenceCommit, log);
        assertThat(result).isNotEmpty();

        Delta delta = result.get();
        FileChanges fileChanges = getFileChanges(delta);
        Change change = getSingleChangeOfType(fileChanges, ChangeEditType.INSERT);
        assertThat(change.getEditType()).isEqualTo(ChangeEditType.INSERT);
        assertThat(change.getFromLine()).isEqualTo(3);
        assertThat(change.getToLine()).isEqualTo(4);
    }

    /**
     * Modified lines within a specific file should be determined.
     */
    @Test
    public void shouldDetermineModifiedLines() {
        GitDeltaCalculator deltaCalculator = createDeltaCalculator();
        FilteredLog log = createLog();

        final String content = "Test\nTest\n";
        final String modified = "Test\nModified\n";
        commitFile(content);
        final String referenceCommit = getHead();
        commitFile(modified);
        final String currentCommit = getHead();

        Optional<Delta> result = deltaCalculator.calculateDelta(currentCommit, referenceCommit, log);
        assertThat(result).isNotEmpty();

        Delta delta = result.get();
        FileChanges fileChanges = getFileChanges(delta);
        Change change = getSingleChangeOfType(fileChanges, ChangeEditType.REPLACE);
        assertThat(change.getEditType()).isEqualTo(ChangeEditType.REPLACE);
        assertThat(change.getFromLine()).isEqualTo(2);
        assertThat(change.getToLine()).isEqualTo(2);
    }

    /**
     * Deleted lines within a specific file should be determined.
     */
    @Test
    public void shouldDetermineDeletedLines() {
        GitDeltaCalculator deltaCalculator = createDeltaCalculator();
        FilteredLog log = createLog();

        final String content = "Test\nTest\n";
        final String modified = "Test\n";
        commitFile(content);
        final String referenceCommit = getHead();
        commitFile(modified);
        final String currentCommit = getHead();

        Optional<Delta> result = deltaCalculator.calculateDelta(currentCommit, referenceCommit, log);
        assertThat(result).isNotEmpty();

        Delta delta = result.get();
        FileChanges fileChanges = getFileChanges(delta);
        Change change = getSingleChangeOfType(fileChanges, ChangeEditType.DELETE);
        assertThat(change.getEditType()).isEqualTo(ChangeEditType.DELETE);
        assertThat(change.getFromLine()).isEqualTo(2);
        assertThat(change.getToLine()).isEqualTo(2);
    }

    /**
     * Added, modified and deleted lines within a specific file should be determined properly together.
     */
    @Test
    public void shouldDetermineAllChangeTypesTogether() {
        GitDeltaCalculator deltaCalculator = createDeltaCalculator();
        FilteredLog log = createLog();

        final String content = "Test1\nTest2\nTest3\nTest4";
        final String newContent = "Modified\nTest2\nInserted\nTest3";
        commitFile(content);
        final String referenceCommit = getHead();
        commitFile(newContent);
        final String currentCommit = getHead();

        Optional<Delta> result = deltaCalculator.calculateDelta(currentCommit, referenceCommit, log);
        assertThat(result).isNotEmpty();

        Delta delta = result.get();
        FileChanges fileChanges = getFileChanges(delta);

        Change replace = getSingleChangeOfType(fileChanges, ChangeEditType.REPLACE);
        assertThat(replace.getEditType()).isEqualTo(ChangeEditType.REPLACE);
        assertThat(replace.getFromLine()).isEqualTo(1);
        assertThat(replace.getToLine()).isEqualTo(1);

        Change insert = getSingleChangeOfType(fileChanges, ChangeEditType.INSERT);
        assertThat(insert.getEditType()).isEqualTo(ChangeEditType.INSERT);
        assertThat(insert.getFromLine()).isEqualTo(3);
        assertThat(insert.getToLine()).isEqualTo(3);

        Change delete = getSingleChangeOfType(fileChanges, ChangeEditType.DELETE);
        assertThat(delete.getEditType()).isEqualTo(ChangeEditType.DELETE);
        assertThat(delete.getFromLine()).isEqualTo(4);
        assertThat(delete.getToLine()).isEqualTo(4);
    }

    /**
     * Gets the first {@link Change} of a specific {@link ChangeEditType} within a file when there is only a single change
     * and checks if the found values are properly.
     *
     * @param fileChanges The changes within a file
     * @param type        The change type
     * @return the first found change
     */
    private Change getSingleChangeOfType(final FileChanges fileChanges, final ChangeEditType type) {
        List<Change> changes = fileChanges.getChanges().get(type);
        assertThat(changes).isNotNull().isNotEmpty();
        assertThat(changes.size()).isEqualTo(1);
        return changes.get(0);
    }

    /**
     * Gets the {@link FileChanges} of a calculated code {@link Delta}.
     *
     * @param delta The code delta
     * @return the found changes.
     */
    private FileChanges getFileChanges(final Delta delta) {
        Optional<FileChanges> fileChanges = delta.getFileChanges().values().stream().findFirst();
        assertThat(fileChanges).isNotEmpty();
        return fileChanges.get();
    }

    /**
     * Creates and commits a file with a fixed name and with the passed file content.
     *
     * @param content The file contend
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
}
