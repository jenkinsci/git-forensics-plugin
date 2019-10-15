package io.jenkins.plugins.git.forensics.blame;

import java.io.File;
import java.io.IOException;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.blame.BlameResult;
import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;

import org.jenkinsci.plugins.gitclient.GitClient;
import hudson.FilePath;
import hudson.plugins.git.GitException;
import hudson.remoting.VirtualChannel;

import io.jenkins.plugins.forensics.blame.Blames;
import io.jenkins.plugins.forensics.blame.FileBlame;
import io.jenkins.plugins.forensics.blame.FileLocations;
import io.jenkins.plugins.git.forensics.blame.GitBlamer.BlameCallback;
import io.jenkins.plugins.git.forensics.blame.GitBlamer.BlameRunner;
import io.jenkins.plugins.git.forensics.blame.GitBlamer.LastCommitRunner;

import static io.jenkins.plugins.forensics.assertions.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests the class {@link GitBlamer}.
 *
 * @author Ullrich Hafner
 */
class GitBlamerTest {
    private static final String EMAIL = "email";
    private static final String NAME = "name";
    private static final String EMPTY = "-";
    private static final String HEAD = "HEAD";
    private static final String WORKSPACE = "/workspace";
    private static final String RELATIVE_PATH = "file.txt";
    private static final String ABSOLUTE_PATH = WORKSPACE + "/" + RELATIVE_PATH;

    @Test
    void shouldAbortIfHeadCommitIsMissing() {
        GitBlamer blamer = new GitBlamer(createGitClient(), HEAD);

        Blames blames = blamer.blame(new FileLocations());

        assertThat(blames.isEmpty()).isTrue();
        assertThat(blames.getErrorMessages()).contains(GitBlamer.NO_HEAD_ERROR);
    }

    @Test
    void shouldAbortIfRefParseThrowsException() throws InterruptedException {
        GitClient gitClient = createGitClient();
        when(gitClient.revParse(HEAD)).thenThrow(new GitException());

        GitBlamer blamer = new GitBlamer(gitClient, HEAD);

        Blames blames = blamer.blame(new FileLocations());

        assertThat(blames.isEmpty()).isTrue();
        assertThat(blames.getErrorMessages()).contains(GitBlamer.NO_HEAD_ERROR);
    }

    @Test
    void shouldAbortIfWithRepositoryThrowsException() throws InterruptedException, IOException {
        GitClient gitClient = createStubbedClientWithException(new IOException());

        GitBlamer blamer = new GitBlamer(gitClient, HEAD);
        Blames blames = blamer.blame(new FileLocations());

        assertThat(blames.isEmpty()).isTrue();
        assertThat(blames.getErrorMessages()).contains(GitBlamer.BLAME_ERROR);
    }

    @Test
    void shouldFinishWithIntermediateResultIfInterrupted() throws InterruptedException, IOException {
        GitClient gitClient = createStubbedClientWithException(new InterruptedException());

        GitBlamer blamer = new GitBlamer(gitClient, HEAD);
        Blames blames = blamer.blame(new FileLocations());

        assertThat(blames.isEmpty()).isTrue();
        assertThat(blames.getErrorMessages()).isEmpty();
    }

    private GitClient createStubbedClientWithException(final Exception exception) throws InterruptedException, IOException {
        GitClient gitClient = mock(GitClient.class);

        ObjectId id = mock(ObjectId.class);
        when(gitClient.revParse(HEAD)).thenReturn(id);
        when(gitClient.withRepository(any())).thenThrow(exception);
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
        BlameCallback callback = createCallback(blames, blamerInput);

        BlameRunner runner = mock(BlameRunner.class);
        when(runner.run(RELATIVE_PATH)).thenThrow(exception);
        callback.run(ABSOLUTE_PATH, RELATIVE_PATH, runner, createLastCommitRunner());

        assertThat(blames.getErrorMessages()).hasSize(3);
        assertThat(blames.getErrorMessages().get(1)).startsWith(
                "- error running git blame on '" + ABSOLUTE_PATH + "' with revision");
        assertThat(blames.getErrorMessages().get(2)).startsWith(exception.getName());
    }

    @Test
    void shouldMapResultToRequestWithOneLine() throws GitAPIException {
        FileLocations locations = new FileLocations();
        locations.addLine(ABSOLUTE_PATH, 1);

        Blames blames = new Blames();
        BlameCallback callback = createCallback(blames, locations);

        BlameResult result = createResult(1);

        stubResultForIndex(result, 0);

        callback.run(ABSOLUTE_PATH, RELATIVE_PATH, createBlameRunner(result), createLastCommitRunner()
        );

        verifyResult(blames.getBlame(ABSOLUTE_PATH), 1);
    }

    private LastCommitRunner createLastCommitRunner() {
        return mock(LastCommitRunner.class);
    }

    @Test
    void shouldMapResultToRequestWithTwoLinesOfAbsolutePaths() throws GitAPIException {
        FileLocations locations = new FileLocations();
        locations.addLine(ABSOLUTE_PATH, 1);
        locations.addLine(ABSOLUTE_PATH, 2);

        Blames blames = new Blames();
        BlameCallback callback = createCallback(blames, locations);

        BlameResult result = createResult(2);

        stubResultForIndex(result, 0);
        stubResultForIndex(result, 1);

        callback.run(ABSOLUTE_PATH, RELATIVE_PATH, createBlameRunner(result), createLastCommitRunner()
        );

        assertThat(blames.contains(ABSOLUTE_PATH)).isTrue();
        FileBlame blame = blames.getBlame(ABSOLUTE_PATH);
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
        locations.addLine(ABSOLUTE_PATH, 1);
        locations.addLine(ABSOLUTE_PATH, 2);

        Blames blames = new Blames();
        BlameCallback callback = createCallback(blames, locations);

        BlameResult result = createResult(1);
        stubResultForIndex(result, 0);

        BlameRunner blameRunner = createBlameRunner(result);
        LastCommitRunner lastCommitRunner = createLastCommitRunner();

        callback.run(ABSOLUTE_PATH, RELATIVE_PATH, blameRunner, lastCommitRunner);

        FileBlame blame = blames.getBlame(ABSOLUTE_PATH);
        verifyResult(blame, 1);

        assertThat(blame.getEmail(3)).isEqualTo(EMPTY);
        assertThat(blame.getName(3)).isEqualTo(EMPTY);
        assertThat(blame.getCommit(3)).isEqualTo(EMPTY);

        callback.run("otherFile", "otherFile", blameRunner, lastCommitRunner);
        assertThat(blames.getErrorMessages()).contains("- no blame results for file 'otherFile'");
    }

    @Test
    void shouldIgnoreMissingCommit() throws GitAPIException {
        FileLocations locations = new FileLocations();
        locations.addLine(ABSOLUTE_PATH, 1);

        Blames blames = new Blames();
        BlameCallback callback = createCallback(blames, locations);

        BlameResult result = createResult(1);
        when(result.getSourceAuthor(0)).thenReturn(new PersonIdent(NAME, EMAIL));

        callback.run(ABSOLUTE_PATH, RELATIVE_PATH, createBlameRunner(result), createLastCommitRunner()
        );

        FileBlame blame = blames.getBlame(ABSOLUTE_PATH);
        assertThat(blame.getEmail(1)).isEqualTo(EMAIL);
        assertThat(blame.getName(1)).isEqualTo(NAME);
        assertThat(blame.getCommit(1)).isEqualTo(EMPTY);
    }

    @Test
    void shouldIgnoreMissingAuthorAndCommitter() throws GitAPIException {
        FileLocations locations = new FileLocations();
        locations.addLine(ABSOLUTE_PATH, 1);

        Blames blames = new Blames();
        BlameCallback callback = createCallback(blames, locations);

        BlameResult result = createResult(1);
        RevCommit commit = mock(RevCommit.class);
        when(result.getSourceCommit(0)).thenReturn(commit);

        callback.run(ABSOLUTE_PATH, RELATIVE_PATH, createBlameRunner(result), createLastCommitRunner()
        );

        FileBlame blame = blames.getBlame(ABSOLUTE_PATH);
        assertThat(blame.getEmail(1)).isEqualTo(EMPTY);
        assertThat(blame.getName(1)).isEqualTo(EMPTY);
        assertThat(blame.getCommit(1)).isNotBlank().isNotEqualTo(EMPTY);
    }

    @Test
    void shouldUseCommitterIfAuthorIsMissing() throws GitAPIException {
        FileLocations locations = new FileLocations();
        locations.addLine(ABSOLUTE_PATH, 1);

        Blames blames = new Blames();
        BlameCallback callback = createCallback(blames, locations);

        BlameResult result = createResult(1);
        RevCommit commit = mock(RevCommit.class);
        when(result.getSourceCommit(0)).thenReturn(commit);
        when(result.getSourceCommitter(0)).thenReturn(new PersonIdent(NAME + 1, EMAIL + 1));

        callback.run(ABSOLUTE_PATH, RELATIVE_PATH, createBlameRunner(result), createLastCommitRunner()
        );

        FileBlame blame = blames.getBlame(ABSOLUTE_PATH);
        verifyResult(blame, 1);
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
        RevCommit commit = mock(RevCommit.class);
        when(result.getSourceCommit(index)).thenReturn(commit);
    }

    private void verifyResult(final FileBlame request, final int line) {
        assertThat(request.getEmail(line)).isEqualTo(EMAIL + line);
        assertThat(request.getName(line)).isEqualTo(NAME + line);
        assertThat(request.getCommit(line)).isNotBlank().isNotEqualTo(EMPTY); // final getter
    }
}
