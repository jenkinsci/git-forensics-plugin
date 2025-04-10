package io.jenkins.plugins.forensics.git.delta;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;
import org.junitpioneer.jupiter.Issue;

import edu.hm.hafner.util.FilteredLog;

import java.io.IOException;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;

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
import io.jenkins.plugins.forensics.reference.ReferenceBuild;

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

    private static final String GIT_FORENSICS_URL = "https://github.com/jenkinsci/git-forensics-plugin.git";
    private static final String GIT_FORENSICS_COMMIT = "86503e8bc0374e05e2cd32ed3bb8b4435d5fd757";
    private static final String DIFF_OUTPUT = """
            diff --git a/newFile b/newFile
            new file mode 100644
            index 0000000..6b584e8
            --- /dev/null
            +++ b/newFile
            @@ -0,0 +1 @@
            +content
            \\ No newline at end of file
            """;

    /**
     * Creates a pipeline that checks if the delta computation works as expected when the main is one commit and build
     * ahead of the feature branch.
     *
     * <pre>
     * {@code
     * M:  [M1]#1 - [M2]#2
     *       \
     *   F:  [F1]#1}
     * </pre>
     */
    @Test
    void shouldFindDeltaEvenWhenTheMainBranchEvolves() {
        var mainBranch = createPipeline(MAIN);
        mainBranch.setDefinition(asStage(createLocalGitCheckout(MAIN)));

        Run<?, ?> mainBuild = buildSuccessfully(mainBranch);

        var mainHash = getHead();
        var featureHash = createFeatureBranchAndAddCommits();
        addAdditionalFileTo(MAIN);

        buildAgain(mainBranch);

        var featureBranch = createPipeline(FEATURE);
        featureBranch.setDefinition(asStage(createLocalGitCheckout(FEATURE),
                "discoverGitReferenceBuild(referenceJob: '" + MAIN + "')"));

        Run<?, ?> featureBuild = buildSuccessfully(featureBranch);
        assertThat(featureBuild.getNumber()).isEqualTo(1);

        assertThat(featureBuild.getAction(ReferenceBuild.class)).isNotNull()
                .hasOwner(featureBuild)
                .hasReferenceBuildId(mainBuild.getExternalizableId())
                .hasReferenceBuild(Optional.of(mainBuild))
                .hasMessages("Configured reference job: 'main'",
                        "Found reference build '#1' for target branch");

        var log = createLog();
        var deltaCalculator = createDeltaCalculator();
        deltaCalculator.calculateDelta(featureBuild, mainBuild, EMPTY_SCM_KEY, log);
        assertThat(log.getErrorMessages()).isEmpty();
        assertThat(log.getInfoMessages()).contains(String.format(
                "-> Using commit '%7.7s' as latest commit for build 'main #1'", mainHash));
        assertThat(log.getInfoMessages()).contains(String.format(
                "-> Using commit '%7.7s' as latest commit for build 'feature #1'", featureHash));
        assertThat(log.getInfoMessages()).contains(
                "-> 1 files contain changes",
                "-> Creating the Git diff file",
                "-> Git code delta successfully calculated"
        );
    }

    @Test
    @Issue("JENKINS-73297")
    void shouldShowErrorIfCommitIsNotFound() {
        var job = createPipeline();
        job.setDefinition(asStage("checkout([$class: 'GitSCM', "
                + "branches: [[name: '" + GIT_FORENSICS_COMMIT + "' ]],\n"
                + "userRemoteConfigs: [[url: '" + GIT_FORENSICS_URL + "']],\n"
                + "extensions: [[$class: 'RelativeTargetDirectory', \n"
                + "            relativeTargetDir: '" + GIT_FORENSICS_COMMIT + "']]])"));

        var build = buildSuccessfully(job);
        var deltaCalculator = createDeltaCalculator();

        var log = createLog();
        var result = deltaCalculator.calculateDelta(build, build, EMPTY_SCM_KEY, log);
        assertThat(result).isNotEmpty();

        assertThat(log.getInfoMessages()).anyMatch(s ->
                s.contains(
                        "-> Invoking Git delta calculator for determining the changes between commits '86503e8' and '86503e8'"));
        assertThat(log.getErrorMessages())
                .contains("Could not find the specified commit - is the SCM parameter correctly set?",
                        "org.eclipse.jgit.errors.MissingObjectException: Missing unknown 86503e8bc0374e05e2cd32ed3bb8b4435d5fd757");
    }

    @Test
    void shouldCreateEmptyDeltaIfCommitsAreInvalid() {
        var deltaCalculator = createDeltaCalculator();

        var log = createLog();
        assertThat(deltaCalculator.calculateDelta(mock(Run.class), mock(Run.class), EMPTY_SCM_KEY, log)).isEmpty();
    }

    @Test
    void shouldCreateDiffFile() {
        var job = createJobWithReferenceRecorder();
        var referenceBuild = buildSuccessfully(job);

        var referenceCommit = getHead();
        var fileName = "newFile";
        var content = "content";
        writeFile(fileName, content);
        addFile(fileName);
        commit("test");

        var build = buildSuccessfully(job);
        var currentCommit = getHead();

        var log = createLog();
        var deltaCalculator = createDeltaCalculator();
        var result = deltaCalculator.calculateDelta(build, referenceBuild, EMPTY_SCM_KEY, log);
        assertThat(result).isNotEmpty();

        var delta = result.get();
        assertThat(delta).hasCurrentCommit(currentCommit);
        assertThat(delta).hasReferenceCommit(referenceCommit);
        assertThat(delta).isInstanceOfSatisfying(GitDelta.class,
                gitDelta -> assertThat(gitDelta.getDiffFile()).isEqualTo(DIFF_OUTPUT));
        assertThat(delta.getFileChangesMap().values()).hasSize(1)
                .first().satisfies(fileChanges ->
                        assertThat(fileChanges.getModifiedLines()).containsExactly(1));
    }

    @Test
    void shouldDetermineAddedFile() {
        var job = createJobWithReferenceRecorder();
        var referenceBuild = buildSuccessfully(job);

        var newFileName = "newFile";
        var content = "added";
        writeFile(newFileName, content);
        addFile(newFileName);
        commit("test");

        var build = buildSuccessfully(job);

        var log = createLog();
        var deltaCalculator = createDeltaCalculator();
        var result = deltaCalculator.calculateDelta(build, referenceBuild, EMPTY_SCM_KEY, log);

        assertThat(result).isNotEmpty();

        var fileChanges = getSingleFileChanges(result.get());
        assertThat(fileChanges.getModifiedLines()).containsExactly(1);
        assertThat(fileChanges.getFileName()).isEqualTo(newFileName);
        assertThat(fileChanges.getOldFileName()).isEqualTo(EMPTY_FILE_PATH);
        assertThat(fileChanges.getFileEditType()).isEqualTo(FileEditType.ADD);
        assertThat(fileChanges.getFileContent()).isEqualTo(content);
    }

    @Test
    void shouldDetermineModifiedFile() {
        var job = createJobWithReferenceRecorder();

        var content = "modified";
        commitFile("test");

        var referenceBuild = buildSuccessfully(job);
        commitFile(content);

        var build = buildSuccessfully(job);

        var log = createLog();
        var deltaCalculator = createDeltaCalculator();
        var result = deltaCalculator.calculateDelta(build, referenceBuild, EMPTY_SCM_KEY, log);
        assertThat(result).isNotEmpty();

        var fileChanges = getSingleFileChanges(result.get());
        assertThat(fileChanges.getModifiedLines()).containsExactly(1);
        assertThat(fileChanges.getFileName()).isEqualTo(INITIAL_FILE);
        assertThat(fileChanges.getOldFileName()).isEqualTo(INITIAL_FILE);
        assertThat(fileChanges.getFileEditType()).isEqualTo(FileEditType.MODIFY);
        assertThat(fileChanges.getFileContent()).isEqualTo(content);
    }

    @Test
    void shouldDetermineDeletedFile() {
        var job = createJobWithReferenceRecorder();

        var content = "content";
        commitFile(content);
        var referenceBuild = buildSuccessfully(job);
        git("rm", INITIAL_FILE);
        commit("test");

        var build = buildSuccessfully(job);

        var log = createLog();
        var deltaCalculator = createDeltaCalculator();
        var result = deltaCalculator.calculateDelta(build, referenceBuild, EMPTY_SCM_KEY, log);
        assertThat(result).isNotEmpty();

        var fileChanges = getSingleFileChanges(result.get());
        assertThat(fileChanges.getModifiedLines()).isEmpty();
        assertThat(fileChanges.getFileName()).isEqualTo(EMPTY_FILE_PATH);
        assertThat(fileChanges.getOldFileName()).isEqualTo(INITIAL_FILE);
        assertThat(fileChanges.getFileEditType()).isEqualTo(FileEditType.DELETE);
        assertThat(fileChanges.getFileContent()).isEqualTo(content);
    }

    @Test
    void shouldDetermineRenamedFile() {
        var job = createJobWithReferenceRecorder();

        var content = "content";
        commitFile(content);
        var referenceBuild = buildSuccessfully(job);
        git("rm", INITIAL_FILE);
        writeFileAsAuthorFoo(content);

        var build = buildSuccessfully(job);

        var log = createLog();
        var deltaCalculator = createDeltaCalculator();
        var result = deltaCalculator.calculateDelta(build, referenceBuild, EMPTY_SCM_KEY, log);
        assertThat(result).isNotEmpty();

        var fileChanges = getSingleFileChanges(result.get());
        assertThat(fileChanges.getModifiedLines()).isEmpty();
        assertThat(fileChanges.getFileName()).isEqualTo(ADDITIONAL_FILE);
        assertThat(fileChanges.getOldFileName()).isEqualTo(INITIAL_FILE);
        assertThat(fileChanges.getFileEditType()).isEqualTo(FileEditType.RENAME);
        assertThat(fileChanges.getFileContent()).isEqualTo(content);
    }

    @Test
    void shouldDetermineAddedLines() {
        var job = createJobWithReferenceRecorder();

        var content = "Test\nTest\n";
        var insertedContent = "Test\nInsert1\nInsert2\nTest\n";
        commitFile(content);
        var referenceBuild = buildSuccessfully(job);
        commitFile(insertedContent);

        var build = buildSuccessfully(job);

        var log = createLog();
        var deltaCalculator = createDeltaCalculator();
        var result = deltaCalculator.calculateDelta(build, referenceBuild, EMPTY_SCM_KEY, log);
        assertThat(result).isNotEmpty();

        var fileChanges = getSingleFileChanges(result.get());
        assertThat(fileChanges.getModifiedLines()).containsExactly(2, 3);

        var change = getSingleChangeOfType(fileChanges, ChangeEditType.INSERT);
        assertThat(change.getEditType()).isEqualTo(ChangeEditType.INSERT);
        assertThat(change.getChangedFromLine()).isEqualTo(1);
        assertThat(change.getChangedToLine()).isEqualTo(1);
        assertThat(change.getFromLine()).isEqualTo(2);
        assertThat(change.getToLine()).isEqualTo(3);
    }

    @Test
    void shouldDetermineModifiedLines() {
        var job = createJobWithReferenceRecorder();

        var content = "Test\nTest\nTest\nTest";
        var modified = "Test\nModified\nModified2\nTest";
        commitFile(content);
        var referenceBuild = buildSuccessfully(job);
        commitFile(modified);

        var build = buildSuccessfully(job);

        var log = createLog();
        var deltaCalculator = createDeltaCalculator();
        var result = deltaCalculator.calculateDelta(build, referenceBuild, EMPTY_SCM_KEY, log);
        assertThat(result).isNotEmpty();

        var fileChanges = getSingleFileChanges(result.get());
        assertThat(fileChanges.getModifiedLines()).containsExactly(2, 3);

        var change = getSingleChangeOfType(fileChanges, ChangeEditType.REPLACE);
        assertThat(change.getEditType()).isEqualTo(ChangeEditType.REPLACE);
        assertThat(change.getChangedFromLine()).isEqualTo(2);
        assertThat(change.getChangedToLine()).isEqualTo(3);
        assertThat(change.getFromLine()).isEqualTo(2);
        assertThat(change.getToLine()).isEqualTo(3);
    }

    @Test
    void shouldDetermineDeletedLines() {
        var job = createJobWithReferenceRecorder();

        var content = "Test\nTest3\nTest";
        var modified = "Test\nTest";
        commitFile(content);
        var referenceBuild = buildSuccessfully(job);
        commitFile(modified);

        var build = buildSuccessfully(job);

        var log = createLog();
        var deltaCalculator = createDeltaCalculator();
        var result = deltaCalculator.calculateDelta(build, referenceBuild, EMPTY_SCM_KEY, log);
        assertThat(result).isNotEmpty();

        var delta = result.get();
        var fileChanges = getSingleFileChanges(delta);
        assertThat(fileChanges.getModifiedLines()).isEmpty();

        var change = getSingleChangeOfType(fileChanges, ChangeEditType.DELETE);
        assertThat(change.getEditType()).isEqualTo(ChangeEditType.DELETE);
        assertThat(change.getChangedFromLine()).isEqualTo(2);
        assertThat(change.getChangedToLine()).isEqualTo(2);
        assertThat(change.getFromLine()).isEqualTo(1);
        assertThat(change.getToLine()).isEqualTo(1);
    }

    @Test
    void shouldDetermineAllChangeTypesTogether() {
        var job = createJobWithReferenceRecorder();

        var content = "Test1\nTest2\nTest3\nTest4";
        var newContent = "Modified\nTest2\nInserted\nTest3";
        commitFile(content);
        var referenceBuild = buildSuccessfully(job);
        commitFile(newContent);

        var build = buildSuccessfully(job);

        var log = createLog();
        var deltaCalculator = createDeltaCalculator();
        var result = deltaCalculator.calculateDelta(build, referenceBuild, EMPTY_SCM_KEY, log);
        assertThat(result).isNotEmpty();

        var fileChanges = getSingleFileChanges(result.get());
        assertThat(fileChanges.getModifiedLines()).containsExactly(1, 3);

        var replace = getSingleChangeOfType(fileChanges, ChangeEditType.REPLACE);
        assertThat(replace.getEditType()).isEqualTo(ChangeEditType.REPLACE);
        assertThat(replace.getChangedFromLine()).isEqualTo(1);
        assertThat(replace.getChangedToLine()).isEqualTo(1);
        assertThat(replace.getFromLine()).isEqualTo(1);
        assertThat(replace.getToLine()).isEqualTo(1);

        var insert = getSingleChangeOfType(fileChanges, ChangeEditType.INSERT);
        assertThat(insert.getEditType()).isEqualTo(ChangeEditType.INSERT);
        assertThat(insert.getChangedFromLine()).isEqualTo(2);
        assertThat(insert.getChangedToLine()).isEqualTo(2);
        assertThat(insert.getFromLine()).isEqualTo(3);
        assertThat(insert.getToLine()).isEqualTo(3);

        var delete = getSingleChangeOfType(fileChanges, ChangeEditType.DELETE);
        assertThat(delete.getEditType()).isEqualTo(ChangeEditType.DELETE);
        assertThat(delete.getChangedFromLine()).isEqualTo(4);
        assertThat(delete.getChangedToLine()).isEqualTo(4);
        assertThat(delete.getFromLine()).isEqualTo(4);
        assertThat(delete.getToLine()).isEqualTo(4);
    }

    /**
     * Gets the first {@link Change} of a specific {@link ChangeEditType} within a file when there is only a single
     * change and checks if the found values are proper.
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
     * checks if the found values are properly stored.
     *
     * @param delta
     *         The code delta
     *
     * @return the found changes.
     */
    private FileChanges getSingleFileChanges(final Delta delta) {
        Collection<FileChanges> fileChanges = delta.getFileChangesMap().values();
        assertThat(fileChanges).hasSize(1);

        Optional<FileChanges> firstChanges = fileChanges.stream().findFirst();
        assertThat(firstChanges).isNotEmpty();
        return firstChanges.get();
    }

    /**
     * Creates and commits a file with a fixed name and with the given file content.
     *
     * @param content
     *         the file content
     */
    private void commitFile(final String content) {
        writeFile(INITIAL_FILE, content);
        git("add", INITIAL_FILE);
        git("config", "user.name", FOO_NAME);
        git("config", "user.email", FOO_EMAIL);
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
        return new GitDeltaCalculator(createGitClient(), StringUtils.EMPTY);
    }

    /**
     * Creates a {@link FreeStyleProject} which contains a {@link GitReferenceRecorder} within the publisher list.
     *
     * @return the created project
     */
    private FreeStyleProject createJobWithReferenceRecorder() {
        try {
            var job = createFreeStyleProject();
            job.setScm(new GitSCM(getRepositoryRoot()));
            job.getPublishersList().add(new GitReferenceRecorder());
            return job;
        }
        catch (IOException exception) {
            throw new AssertionError(exception);
        }
    }
}
