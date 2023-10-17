package io.jenkins.plugins.forensics.git.blame;

import java.io.File;
import java.io.IOException;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.blame.BlameResult;
import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.jupiter.api.Test;
import org.junitpioneer.jupiter.Issue;
import org.mockito.ArgumentMatchers;

import edu.hm.hafner.util.FilteredLog;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import org.jenkinsci.plugins.gitclient.GitClient;
import hudson.FilePath;
import hudson.plugins.git.GitException;
import hudson.remoting.VirtualChannel;

import io.jenkins.plugins.forensics.blame.Blames;
import io.jenkins.plugins.forensics.blame.FileBlame;
import io.jenkins.plugins.forensics.blame.FileBlame.FileBlameBuilder;
import io.jenkins.plugins.forensics.blame.FileLocations;
import io.jenkins.plugins.forensics.git.blame.GitBlamer.BlameCallback;
import io.jenkins.plugins.forensics.git.blame.GitBlamer.BlameRunner;
import io.jenkins.plugins.forensics.git.blame.GitBlamer.LastCommitRunner;

import static io.jenkins.plugins.forensics.assertions.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests the class {@link GitBlamer}.
 *
 * @author Ullrich Hafner
 */
@SuppressWarnings({"checkstyle:ClassDataAbstractionCoupling", "PMD.ExcessiveImports", "checkstyle:ClassFanOutComplexity"})
class GitBlamerTest {
    private static final String EMAIL = "email";
    private static final String NAME = "name";
    private static final int TIME = 12_345;
    private static final String EMPTY = "-";
    private static final int EMPTY_TIME = 0;
    private static final String HEAD = "HEAD";
    private static final String RELATIVE_PATH = "file.txt";
    private static final FileBlameBuilder BUILDER = new FileBlameBuilder();

    @Test
    void shouldAbortIfHeadCommitIsMissing() {
        GitBlamer blamer = new GitBlamer(createGitClient(), HEAD);

        FilteredLog log = new FilteredLog(StringUtils.EMPTY);
        Blames blames = blamer.blame(new FileLocations(), log);

        assertThat(blames).isEmpty();
        assertThat(log.getErrorMessages()).contains(GitBlamer.NO_HEAD_ERROR);
    }

    @Test
    void shouldAbortIfRefParseThrowsException() throws InterruptedException {
        GitClient gitClient = createGitClient();
        when(gitClient.revParse(HEAD)).thenThrow(new GitException("Error"));

        GitBlamer blamer = new GitBlamer(gitClient, HEAD);

        FilteredLog log = new FilteredLog(StringUtils.EMPTY);
        Blames blames = blamer.blame(new FileLocations(), log);

        assertThat(blames.isEmpty()).isTrue();
        assertThat(log.getErrorMessages()).contains(GitBlamer.NO_HEAD_ERROR);
    }

    @Test
    void shouldAbortIfWithRepositoryThrowsException() throws InterruptedException, IOException {
        GitClient gitClient = createStubbedClientWithException(new IOException("Error"));

        GitBlamer blamer = new GitBlamer(gitClient, HEAD);
        FilteredLog log = new FilteredLog(StringUtils.EMPTY);
        Blames blames = blamer.blame(new FileLocations(), log);

        assertThat(blames.isEmpty()).isTrue();
        assertThat(log.getErrorMessages()).contains(GitBlamer.BLAME_ERROR);
    }

    @Test
    void shouldFinishWithIntermediateResultIfInterrupted() throws InterruptedException, IOException {
        GitClient gitClient = createStubbedClientWithException(new InterruptedException("Error"));

        GitBlamer blamer = new GitBlamer(gitClient, HEAD);
        FilteredLog log = new FilteredLog(StringUtils.EMPTY);
        Blames blames = blamer.blame(new FileLocations(), log);

        assertThat(blames.isEmpty()).isTrue();
        assertThat(log.getErrorMessages()).isEmpty();
    }

    private GitClient createStubbedClientWithException(final Exception exception)
            throws InterruptedException, IOException {
        GitClient gitClient = mock(GitClient.class);

        ObjectId id = mock(ObjectId.class);
        when(gitClient.revParse(HEAD)).thenReturn(id);
        when(gitClient.withRepository(ArgumentMatchers.any())).thenThrow(exception);
        FilePath workTree = createWorkTreeStub();
        when(gitClient.getWorkTree()).thenReturn(workTree);

        return gitClient;
    }

    private FilePath createWorkTreeStub() {
        File mock = mock(File.class);
        when(mock.getPath()).thenReturn("/");
        return new FilePath(mock);
    }

    @Test
    @Issue("JENKINS-55273")
    void shouldNotFailOnExceptions() throws GitAPIException {
        verifyExceptionHandling(NoHeadException.class);
        verifyExceptionHandling(JGitInternalException.class);
    }

    private void verifyExceptionHandling(final Class<? extends Exception> exception) throws GitAPIException {
        Blames blames = new Blames();
        FileLocations blamerInput = new FileLocations();
        FilteredLog log = new FilteredLog(StringUtils.EMPTY);
        BlameCallback callback = createCallback(blames, blamerInput);

        BlameRunner runner = mock(BlameRunner.class);
        when(runner.run(RELATIVE_PATH)).thenThrow(exception);
        callback.run(BUILDER, RELATIVE_PATH, runner, createLastCommitRunner(), log);

        assertThat(log.getErrorMessages()).hasSize(3);
        assertThat(log.getErrorMessages().get(1)).startsWith(
                "- error running git blame on '" + RELATIVE_PATH + "' with revision");
        assertThat(log.getErrorMessages().get(2)).startsWith(exception.getName());
    }

    @Test
    void shouldMapResultToRequestWithOneLine() throws GitAPIException {
        FileLocations locations = new FileLocations();
        locations.addLine(RELATIVE_PATH, 1);

        Blames blames = new Blames();
        FilteredLog log = new FilteredLog(StringUtils.EMPTY);
        BlameCallback callback = createCallback(blames, locations);

        BlameResult result = createResult(1);

        stubResultForIndex(result, 0);

        callback.run(BUILDER, RELATIVE_PATH, createBlameRunner(result), createLastCommitRunner(), log);

        verifyResult(blames.getBlame(RELATIVE_PATH), 1);
    }

    private LastCommitRunner createLastCommitRunner() {
        return mock(LastCommitRunner.class);
    }

    @Test
    void shouldMapResultToRequestWithTwoLinesOfAbsolutePaths() throws GitAPIException {
        FileLocations locations = new FileLocations();
        locations.addLine(RELATIVE_PATH, 1);
        locations.addLine(RELATIVE_PATH, 2);

        Blames blames = new Blames();
        FilteredLog log = new FilteredLog(StringUtils.EMPTY);
        BlameCallback callback = createCallback(blames, locations);

        BlameResult result = createResult(2);

        stubResultForIndex(result, 0);
        stubResultForIndex(result, 1);

        callback.run(BUILDER, RELATIVE_PATH, createBlameRunner(result), createLastCommitRunner(), log);

        assertThat(blames.contains(RELATIVE_PATH)).isTrue();
        FileBlame blame = blames.getBlame(RELATIVE_PATH);
        verifyResult(blame, 1);
        verifyResult(blame, 2);
    }

    private GitClient createGitClient() {
        GitClient gitClient = mock(GitClient.class);
        when(gitClient.getWorkTree()).thenReturn(new FilePath((VirtualChannel) null, ""));
        return gitClient;
    }

    private BlameCallback createCallback(final Blames blames, final FileLocations blamerInput) {
        return new BlameCallback(blamerInput, blames, mock(ObjectId.class));
    }

    @Test
    void shouldMapResultToRequestOutOfRange() throws GitAPIException {
        FileLocations locations = new FileLocations();
        locations.addLine(RELATIVE_PATH, 1);
        locations.addLine(RELATIVE_PATH, 2);

        Blames blames = new Blames();
        FilteredLog log = new FilteredLog(StringUtils.EMPTY);
        BlameCallback callback = createCallback(blames, locations);

        BlameResult result = createResult(1);
        stubResultForIndex(result, 0);

        BlameRunner blameRunner = createBlameRunner(result);
        LastCommitRunner lastCommitRunner = createLastCommitRunner();

        callback.run(BUILDER, RELATIVE_PATH, blameRunner, lastCommitRunner, log);

        FileBlame blame = blames.getBlame(RELATIVE_PATH);
        verifyResult(blame, 1);

        assertThat(blame.getEmail(3)).isEqualTo(EMPTY);
        assertThat(blame.getName(3)).isEqualTo(EMPTY);
        assertThat(blame.getCommit(3)).isEqualTo(EMPTY);
        assertThat(blame.getTime(3)).isEqualTo(EMPTY_TIME);

        callback.run(BUILDER, "otherFile", blameRunner, lastCommitRunner, log);
        assertThat(log.getErrorMessages()).contains("- no blame results for file 'otherFile'");
    }

    @Test
    void shouldIgnoreMissingCommit() throws GitAPIException {
        FileLocations locations = new FileLocations();
        locations.addLine(RELATIVE_PATH, 1);

        Blames blames = new Blames();
        FilteredLog log = new FilteredLog(StringUtils.EMPTY);
        BlameCallback callback = createCallback(blames, locations);

        BlameResult result = createResult(1);
        when(result.getSourceAuthor(0)).thenReturn(new PersonIdent(NAME, EMAIL));

        callback.run(BUILDER, RELATIVE_PATH, createBlameRunner(result), createLastCommitRunner(), log);

        FileBlame blame = blames.getBlame(RELATIVE_PATH);
        assertThat(blame.getEmail(1)).isEqualTo(EMAIL);
        assertThat(blame.getName(1)).isEqualTo(NAME);
        assertThat(blame.getCommit(1)).isEqualTo(EMPTY);
        assertThat(blame.getTime(1)).isEqualTo(EMPTY_TIME);
    }

    @Test
    void shouldIgnoreMissingAuthorAndCommitter() throws GitAPIException {
        FileLocations locations = new FileLocations();
        locations.addLine(RELATIVE_PATH, 1);

        Blames blames = new Blames();
        FilteredLog log = new FilteredLog(StringUtils.EMPTY);
        BlameCallback callback = createCallback(blames, locations);

        BlameResult result = createResult(1);
        RevCommit commit = createCommit();
        when(result.getSourceCommit(0)).thenReturn(commit);

        callback.run(BUILDER, RELATIVE_PATH, createBlameRunner(result), createLastCommitRunner(), log);

        FileBlame blame = blames.getBlame(RELATIVE_PATH);
        assertThat(blame.getEmail(1)).isEqualTo(EMPTY);
        assertThat(blame.getName(1)).isEqualTo(EMPTY);
        assertThat(blame.getCommit(1)).isEqualTo(getCommitID());
        assertThat(blame.getTime(1)).isEqualTo(TIME);
    }

    @Test
    void shouldUseCommitterIfAuthorIsMissing() throws GitAPIException {
        FileLocations locations = new FileLocations();
        locations.addLine(RELATIVE_PATH, 1);

        Blames blames = new Blames();
        FilteredLog log = new FilteredLog(StringUtils.EMPTY);
        BlameCallback callback = createCallback(blames, locations);

        BlameResult result = createResult(1);
        RevCommit commit = createCommit(TIME + 1);
        when(result.getSourceCommit(0)).thenReturn(commit);
        when(result.getSourceAuthor(0)).thenReturn(null);
        when(result.getSourceCommitter(0)).thenReturn(new PersonIdent(NAME + 1, EMAIL + 1));

        callback.run(BUILDER, RELATIVE_PATH, createBlameRunner(result), createLastCommitRunner(), log);

        FileBlame blame = blames.getBlame(RELATIVE_PATH);
        verifyResult(blame, 1);
    }

    private RevCommit createCommit() {
        return createCommit(TIME);
    }

    private RevCommit createCommit(final int commitTime) {
        return RevCommit.parse(getRawCommit(commitTime));
    }

    private String getCommitID() {
        return getCommitID(TIME);
    }

    private String getCommitID(final int commitTime) {
        try (ObjectInserter.Formatter fmt = new ObjectInserter.Formatter()) {
            return fmt.idFor(Constants.OBJ_COMMIT, getRawCommit(commitTime)).getName();
        }
    }

    @SuppressFBWarnings(value = "VA_FORMAT_STRING_USES_NEWLINE", justification = "JGit apparently can't parse windows line-endings (\r\n)")
    private byte[] getRawCommit(final int commitTime) {
        return String.format("tree 216785864a817e2c5d9d5b54881a1f153da52096\n"
                        + "author Foo Bar <foo@bar.com> %d +0000\n"
                        + "committer Foo Bar <foo@bar.com> %d +0000\n\n"
                        + "Commit message",
                commitTime,
                commitTime)
                .getBytes();
    }

    private BlameResult createResult(final int size) {
        RawText resultSize = createResultSize(size);
        BlameResult result = mock(BlameResult.class);
        when(result.getResultContents()).thenReturn(resultSize);
        return result;
    }

    private BlameRunner createBlameRunner(final BlameResult result) throws GitAPIException {
        BlameRunner blameRunner = mock(BlameRunner.class);
        when(blameRunner.run(RELATIVE_PATH)).thenReturn(result);
        return blameRunner;
    }

    private RawText createResultSize(final int size) {
        RawText text = mock(RawText.class);
        when(text.size()).thenReturn(size);
        return text;
    }

    private void stubResultForIndex(final BlameResult result, final int index) {
        int line = index + 1;
        when(result.getSourceAuthor(index)).thenReturn(new PersonIdent(NAME + line, EMAIL + line));
        RevCommit commit = createCommit(TIME + line);
        when(result.getSourceCommit(index)).thenReturn(commit);
    }

    private void verifyResult(final FileBlame request, final int line) {
        assertThat(request.getEmail(line)).isEqualTo(EMAIL + line);
        assertThat(request.getName(line)).isEqualTo(NAME + line);
        assertThat(request.getCommit(line)).isEqualTo(getCommitID(TIME + line));
        assertThat(request.getTime(line)).isEqualTo(TIME + line);
    }
}
