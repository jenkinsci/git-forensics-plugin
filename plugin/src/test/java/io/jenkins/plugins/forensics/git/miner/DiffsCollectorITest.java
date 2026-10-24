package io.jenkins.plugins.forensics.git.miner;

import static io.jenkins.plugins.forensics.assertions.Assertions.*;

import edu.hm.hafner.util.FilteredLog;
import edu.hm.hafner.util.TreeStringBuilder;
import io.jenkins.plugins.forensics.git.util.GitITest;
import io.jenkins.plugins.forensics.miner.CommitDiffItem;
import java.io.IOException;
import java.util.List;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.RenameDetector;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.EmptyTreeIterator;
import org.junit.jupiter.api.Test;

/**
 * Tests the class {@link DiffsCollector}.
 *
 * @author Ullrich Hafner
 */
@SuppressWarnings("checkstyle:LambdaBodyLength")
class DiffsCollectorITest extends GitITest {
    private static final String MOVED_FILE = "moved";
    private static final String AUTHOR = "author";
    private static final EmptyTreeIterator NULL_ITERATOR = new EmptyTreeIterator();
    private static final String ATTRIBUTES_FILE = ".gitattributes";
    private static final String SUB_ATTRIBUTES_FILE = "sub/.gitattributes";
    private static final String DISABLE_JSON_DIFFS = "*.json -diff\n";
    private static final String DATA_FILE = "data.json";
    private static final String SUB_DATA_FILE = "sub/data.json";
    private static final String RENAMED_DATA_FILE = "renamed.json";

    /** Verifies that the initial repository contains a single commit. */
    @Test
    void shouldInitializeCounter() {
        runTest((repository, git) -> {
            var head = getHead();
            List<CommitDiffItem> actualCommits = createDiff(repository, git, head, NULL_ITERATOR);
            assertThat(actualCommits).hasSize(1);
            assertThat(actualCommits.get(0))
                    .hasTotalAddedLines(0)
                    .hasTotalDeletedLines(0)
                    .hasId(head)
                    .hasAuthor(AUTHOR)
                    .hasTime(0);
        });
    }

    /** Verifies that adding lines to the same file works. */
    @Test
    void shouldCountAddedLines() {
        var firstCommit = getHead();
        writeFileAsAuthorBar("First Line\nSecond Line\n");
        var secondCommit = getHead();
        writeFileAsAuthorBar("First Line\nSecond Line\nThird Line\n");
        var head = getHead();

        runTest((repository, git) -> {
            List<CommitDiffItem> allDeltas = createDiff(repository, git, head, NULL_ITERATOR);
            assertThat(allDeltas).hasSize(2);
            assertThat(allDeltas.get(0)).hasTotalAddedLines(0).hasTotalDeletedLines(0);
            assertThat(allDeltas.get(1))
                    .hasTotalAddedLines(3)
                    .hasTotalDeletedLines(0)
                    .hasId(head)
                    .hasAuthor(AUTHOR)
                    .hasTime(0);

            List<CommitDiffItem> deltaFirstCommit = createDiff(repository, git, secondCommit, firstCommit);
            assertThat(deltaFirstCommit).hasSize(1);
            assertThat(deltaFirstCommit.get(0))
                    .hasTotalAddedLines(2)
                    .hasTotalDeletedLines(0)
                    .hasId(secondCommit)
                    .hasAuthor(AUTHOR)
                    .hasTime(0);

            List<CommitDiffItem> deltaLastCommit = createDiff(repository, git, head, secondCommit);
            assertThat(deltaLastCommit).hasSize(1);
            assertThat(deltaLastCommit.get(0))
                    .hasTotalAddedLines(1)
                    .hasTotalDeletedLines(0)
                    .hasId(head)
                    .hasAuthor(AUTHOR)
                    .hasTime(0);
        });
    }

    /** Verifies that deleting lines from the same file works. */
    @Test
    void shouldCountDeletedLines() {
        var firstCommit = getHead();
        writeFileAsAuthorBar("First Line\nSecond Line\n");
        var secondCommit = getHead();
        writeFileAsAuthorBar("First Line\n");
        var head = getHead();

        runTest((repository, git) -> {
            List<CommitDiffItem> allDeltas = createDiff(repository, git, secondCommit, firstCommit);
            assertThat(allDeltas).hasSize(1);
            assertThat(allDeltas.get(0))
                    .hasTotalAddedLines(2)
                    .hasTotalDeletedLines(0)
                    .hasId(secondCommit)
                    .hasAuthor(AUTHOR)
                    .hasTime(0);

            List<CommitDiffItem> deltaLastCommit = createDiff(repository, git, head, secondCommit);
            assertThat(deltaLastCommit).hasSize(1);
            assertThat(deltaLastCommit.get(0))
                    .hasTotalAddedLines(0)
                    .hasTotalDeletedLines(1)
                    .hasId(head)
                    .hasAuthor(AUTHOR)
                    .hasTime(0);
        });
    }

    /** Verifies that deleting multiple non-overlapping line blocks in the same file works. */
    @Test
    void shouldHandleMultipleSections() {
        writeFileAsAuthorBar("1 =====\n2 =====\n3 =====\n4 =====\n5 =====\n");
        var secondCommit = getHead();
        writeFileAsAuthorBar("1 =====\n3 =====\n5 =====\n");
        var head = getHead();

        runTest((repository, git) -> {
            List<CommitDiffItem> allDeltas = createDiff(repository, git, head, secondCommit);
            assertThat(allDeltas).hasSize(1);
            assertThat(allDeltas.get(0))
                    .hasTotalAddedLines(0)
                    .hasTotalDeletedLines(2)
                    .hasId(head)
                    .hasAuthor(AUTHOR)
                    .hasTime(0);
        });
    }

    /** Verifies that removing a file correctly identifies the deleted lines. */
    @Test
    void shouldHandleRemovedFiles() {
        writeFileAsAuthorBar("1 =====\n2 =====\n3 =====\n4 =====\n5 =====\n");
        var initialCommit = getHead();
        git("rm", ADDITIONAL_FILE);
        commit("Removed file");

        runTest((repository, git) -> {
            var head = getHead();
            List<CommitDiffItem> allDeltas = createDiff(repository, git, head, initialCommit);
            assertThat(allDeltas).hasSize(1);
            assertThat(allDeltas.get(0))
                    .hasTotalAddedLines(0)
                    .hasTotalDeletedLines(5)
                    .hasId(head)
                    .hasAuthor(AUTHOR)
                    .hasTime(0);
        });
    }

    /** Verifies that moving a file correctly identifies an entry that contains a reference to the old file name. */
    @Test
    void shouldHandleMovedFiles() {
        writeFileAsAuthorBar("1 =====\n2 =====\n3 =====\n4 =====\n5 =====\n");
        var initialCommit = getHead();
        git("mv", ADDITIONAL_FILE, MOVED_FILE);
        commit("Moved file");

        runTest((repository, git) -> {
            var head = getHead();
            List<CommitDiffItem> allDeltas = createDiff(repository, git, head, initialCommit);
            assertThat(allDeltas).hasSize(1);
            assertThat(allDeltas.get(0))
                    .hasTotalAddedLines(0)
                    .hasTotalDeletedLines(0)
                    .hasId(head)
                    .hasAuthor(AUTHOR)
                    .hasTime(0);
        });
    }

    /** Verifies that moving and changing a file correctly identifies the changed lines in the moved file. */
    @Test
    void shouldHandleMovedAndChangedFiles() {
        writeFileAsAuthorBar(
                "1 =====\n2 =====\n3 =====\n4 =====\n5 =====\n1 =====\n2 =====\n3 =====\n4 =====\n5 =====\n1 =====\n2 =====\n3 =====\n4 =====\n5 =====\n1 =====\n2 =====\n3 =====\n4 =====\n5 =====\n");
        var initialCommit = getHead();
        git("mv", ADDITIONAL_FILE, MOVED_FILE);
        writeFile(
                MOVED_FILE,
                "1 =====\n2a =====\n2 =====\n3 =====\n4 =====\n5 =====\n1 =====\n2 =====\n3 =====\n4 =====\n5 =====\n1 =====\n2 =====\n3 =====\n4 =====\n5 =====\n2 =====\n3 =====\n4 =====\n5 =====\n");
        commit("Moved and changed file");

        verifyMovedAndChangedFile(initialCommit);
    }

    /** Verifies that changing and moving a file correctly identifies the changed lines in the moved file. */
    @Test
    void shouldHandleChangedAndMovedFiles() {
        writeFileAsAuthorBar(
                "1 =====\n2 =====\n3 =====\n4 =====\n5 =====\n1 =====\n2 =====\n3 =====\n4 =====\n5 =====\n1 =====\n2 =====\n3 =====\n4 =====\n5 =====\n1 =====\n2 =====\n3 =====\n4 =====\n5 =====\n");
        var initialCommit = getHead();
        writeFile(
                ADDITIONAL_FILE,
                "1 =====\n2a =====\n2 =====\n3 =====\n4 =====\n5 =====\n1 =====\n2 =====\n3 =====\n4 =====\n5 =====\n1 =====\n2 =====\n3 =====\n4 =====\n5 =====\n2 =====\n3 =====\n4 =====\n5 =====\n");
        git("mv", ADDITIONAL_FILE, MOVED_FILE);
        commit("Moved and changed file");

        verifyMovedAndChangedFile(initialCommit);
    }

    /** Verifies that files that disable diffs in {@code .gitattributes} are reported without line counts. */
    @Test
    void shouldSkipLineCountsForFilesWithDiffDisabled() {
        writeFile(ATTRIBUTES_FILE, DISABLE_JSON_DIFFS + "*.dat binary\n");
        addFile(ATTRIBUTES_FILE);
        commit("Add attributes");
        var attributesCommit = getHead();
        commitDataAndSourceFiles();
        var head = getHead();

        runTest((repository, git) -> {
            List<CommitDiffItem> allDeltas = createDiff(repository, git, head, attributesCommit);
            assertThat(allDeltas).hasSize(3);
            assertThat(allDeltas)
                    .filteredOn(item -> DATA_FILE.equals(item.getNewPath()))
                    .singleElement()
                    .satisfies(item -> assertThat(item).hasTotalAddedLines(0).hasTotalDeletedLines(0));
            assertThat(allDeltas)
                    .filteredOn(item -> "data.dat".equals(item.getNewPath()))
                    .singleElement()
                    .satisfies(item -> assertThat(item).hasTotalAddedLines(0).hasTotalDeletedLines(0));
            assertThat(allDeltas)
                    .filteredOn(item -> "Source.java".equals(item.getNewPath()))
                    .singleElement()
                    .satisfies(item -> assertThat(item).hasTotalAddedLines(2).hasTotalDeletedLines(0));
        });
    }

    /**
     * Verifies that renaming a file with diffs disabled still reports no line counts. Renamed entries are created by
     * the {@link RenameDetector} by pairing an add and a delete entry, so the attribute has to survive that pairing.
     */
    @Test
    void shouldSkipLineCountsForRenamedFileWithDiffDisabled() {
        commitDiffDisablingAttributes();
        commitDataFile(DATA_FILE, 10, "Add data to rename");
        var initialCommit = getHead();

        git("mv", DATA_FILE, RENAMED_DATA_FILE);
        writeFile(RENAMED_DATA_FILE, createJson(12));
        commit("Rename and extend data");
        var head = getHead();

        runTest((repository, git) -> {
            List<CommitDiffItem> allDeltas = createDiff(repository, git, head, initialCommit);
            assertThat(allDeltas).hasSize(1);
            assertThat(allDeltas.get(0).getOldPath())
                    .as("entry should be detected as a rename, otherwise this test would pass vacuously")
                    .isEqualTo(DATA_FILE);
            assertThat(allDeltas.get(0))
                    .hasTotalAddedLines(0)
                    .hasTotalDeletedLines(0)
                    .hasId(head)
                    .hasAuthor(AUTHOR);
        });
    }

    /** Verifies that modifying a file with diffs disabled reports no line counts. */
    @Test
    void shouldSkipLineCountsWhenFileWithDiffDisabledIsModified() {
        commitDiffDisablingAttributes();
        commitDataFile(DATA_FILE, 10, "Add data to modify");
        var initialCommit = getHead();

        writeFile(DATA_FILE, createJson(20));
        commit("Modify data");
        var head = getHead();

        runTest((repository, git) -> {
            List<CommitDiffItem> allDeltas = createDiff(repository, git, head, initialCommit);
            assertThat(allDeltas).hasSize(1);
            assertThat(allDeltas.get(0)).hasTotalAddedLines(0).hasTotalDeletedLines(0);
        });
    }

    /** Verifies that deleting a file with diffs disabled reports no line counts. */
    @Test
    void shouldSkipLineCountsWhenFileWithDiffDisabledIsDeleted() {
        commitDiffDisablingAttributes();
        commitDataFile(DATA_FILE, 10, "Add data to delete");
        var initialCommit = getHead();

        git("rm", DATA_FILE);
        commit("Delete data");
        var head = getHead();

        runTest((repository, git) -> {
            List<CommitDiffItem> allDeltas = createDiff(repository, git, head, initialCommit);
            assertThat(allDeltas).hasSize(1);
            assertThat(allDeltas.get(0)).hasTotalAddedLines(0).hasTotalDeletedLines(0);
        });
    }

    /**
     * Verifies that a file that selects a custom diff driver is still counted: only an unset attribute disables the
     * line counts, a set or custom value must not.
     */
    @Test
    void shouldCountLinesForFileWithCustomDiffDriver() {
        writeFile(ATTRIBUTES_FILE, "*.custom diff=someDriver\n");
        addFile(ATTRIBUTES_FILE);
        commit("Add attributes with a custom driver");
        var attributesCommit = getHead();

        writeFile("data.custom", "First Line\nSecond Line\n");
        addFile("data.custom");
        commit("Add data using the custom driver");
        var head = getHead();

        runTest((repository, git) -> {
            List<CommitDiffItem> allDeltas = createDiff(repository, git, head, attributesCommit);
            assertThat(allDeltas).hasSize(1);
            assertThat(allDeltas.get(0)).hasTotalAddedLines(2).hasTotalDeletedLines(0);
        });
    }

    /**
     * Verifies which version of the attributes applies when history is mined: they are resolved from the older of the
     * two compared commits. A file that is added in the very same commit as the attributes file that disables its
     * diffs is therefore still counted, and only the commits after that one skip the line counts.
     *
     * <p>
     * Note that this differs from {@code git diff --numstat}, which resolves the attributes from the working tree and
     * so reports no counts even for that first commit.
     * </p>
     */
    @Test
    void shouldResolveAttributesFromTheOlderCommit() {
        var initialCommit = getHead();

        writeFile(ATTRIBUTES_FILE, DISABLE_JSON_DIFFS);
        writeFile(DATA_FILE, createJson(10));
        addFile(ATTRIBUTES_FILE);
        addFile(DATA_FILE);
        commit("Add attributes and data at once");
        var attributesCommit = getHead();

        writeFile(DATA_FILE, createJson(20));
        commit("Change data once the attributes are in place");
        var head = getHead();

        runTest((repository, git) -> {
            List<CommitDiffItem> beforeAttributes = createDiff(repository, git, attributesCommit, initialCommit);
            assertThat(beforeAttributes).hasSize(2);
            assertThat(beforeAttributes)
                    .filteredOn(item -> DATA_FILE.equals(item.getNewPath()))
                    .singleElement()
                    .satisfies(item -> assertThat(item).hasTotalAddedLines(12).hasTotalDeletedLines(0));

            List<CommitDiffItem> afterAttributes = createDiff(repository, git, head, attributesCommit);
            assertThat(afterAttributes).hasSize(1);
            assertThat(afterAttributes.get(0)).hasTotalAddedLines(0).hasTotalDeletedLines(0);
        });
    }

    /**
     * Verifies that an attributes file in a subdirectory applies to that directory only, so an equally named file in
     * the root directory is still counted.
     */
    @Test
    void shouldSkipLineCountsForAttributesInSubDirectoryOnly() {
        writeFile(SUB_ATTRIBUTES_FILE, DISABLE_JSON_DIFFS);
        addFile(SUB_ATTRIBUTES_FILE);
        commit("Add attributes in a sub directory");
        var attributesCommit = getHead();

        writeFile(SUB_DATA_FILE, createJson(10));
        writeFile(DATA_FILE, createJson(10));
        addFile(SUB_DATA_FILE);
        addFile(DATA_FILE);
        commit("Add data in both directories");
        var head = getHead();

        runTest((repository, git) -> {
            List<CommitDiffItem> allDeltas = createDiff(repository, git, head, attributesCommit);
            assertThat(allDeltas).hasSize(2);
            assertThat(allDeltas)
                    .filteredOn(item -> SUB_DATA_FILE.equals(item.getNewPath()))
                    .singleElement()
                    .satisfies(item -> assertThat(item).hasTotalAddedLines(0).hasTotalDeletedLines(0));
            assertThat(allDeltas)
                    .filteredOn(item -> DATA_FILE.equals(item.getNewPath()))
                    .singleElement()
                    .satisfies(item -> assertThat(item).hasTotalAddedLines(12).hasTotalDeletedLines(0));
        });
    }

    private void commitDiffDisablingAttributes() {
        writeFile(ATTRIBUTES_FILE, DISABLE_JSON_DIFFS);
        addFile(ATTRIBUTES_FILE);
        commit("Add attributes that disable diffs");
    }

    private void commitDataFile(final String fileName, final int entries, final String message) {
        writeFile(fileName, createJson(entries));
        addFile(fileName);
        commit(message);
    }

    private String createJson(final int entries) {
        var json = new StringBuilder("{\n");
        for (int i = 0; i < entries; i++) {
            json.append("\"key-").append(i).append("\": ").append(i).append(",\n");
        }
        return json.append("}\n").toString();
    }

    private void commitDataAndSourceFiles() {
        writeFile(DATA_FILE, createJson(2));
        writeFile("data.dat", "1\n2\n3\n");
        writeFile("Source.java", "First Line\nSecond Line\n");
        addFile(DATA_FILE);
        addFile("data.dat");
        addFile("Source.java");
        commit("Add data and source");
    }

    private void verifyMovedAndChangedFile(final String initialCommit) {
        runTest((repository, git) -> {
            var head = getHead();
            List<CommitDiffItem> allDeltas = createDiff(repository, git, head, initialCommit);
            assertThat(allDeltas).hasSize(1);
            assertThat(allDeltas.get(0))
                    .hasTotalAddedLines(1)
                    .hasTotalDeletedLines(1)
                    .hasId(head)
                    .hasAuthor(AUTHOR)
                    .hasTime(0);
        });
    }

    private List<CommitDiffItem> createDiff(
            final Repository repository, final Git git, final String newCommit, final String oldCommit)
            throws IOException {
        AbstractTreeIterator toTree =
                CommitAnalyzer.createTreeIteratorFor(oldCommit, repository, new FilteredLog("empty"));
        return createDiff(repository, git, newCommit, toTree);
    }

    private List<CommitDiffItem> createDiff(
            final Repository repository, final Git git, final String newCommit, final AbstractTreeIterator toTree) {
        var collector = new DiffsCollector();
        return collector.getDiffsForCommit(
                repository,
                git,
                new CommitDiffItem(newCommit, AUTHOR, 0),
                toTree,
                new TreeStringBuilder(),
                new FilteredLog("Errors"));
    }
}
