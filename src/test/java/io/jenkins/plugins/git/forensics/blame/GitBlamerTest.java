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

import io.jenkins.plugins.forensics.blame.Blames;
import io.jenkins.plugins.forensics.blame.FileBlame;
import io.jenkins.plugins.forensics.blame.FileLocations;
import io.jenkins.plugins.git.forensics.blame.GitBlamer.BlameCallback;
import io.jenkins.plugins.git.forensics.blame.GitBlamer.BlameRunner;

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

    @Test
    void shouldAbortIfHeadCommitIsMissing() {
        GitClient gitClient = mock(GitClient.class);
        GitBlamer blamer = new GitBlamer(gitClient, HEAD);

        Blames blames = blamer.blame(new FileLocations());

        assertThat(blames).isEmpty();
        assertThat(blames).hasErrorMessages(GitBlamer.NO_HEAD_ERROR);
    }

    @Test
    void shouldAbortIfRefParseThrowsException() throws InterruptedException {
        GitClient gitClient = mock(GitClient.class);
        GitBlamer blamer = new GitBlamer(gitClient, HEAD);

        when(gitClient.revParse(HEAD)).thenThrow(new GitException());
        Blames blames = blamer.blame(new FileLocations());

        assertThat(blames).isEmpty();
        assertThat(blames).hasErrorMessages(GitBlamer.NO_HEAD_ERROR);
    }

    @Test
    void shouldAbortIfWithRepositoryThrowsException() throws InterruptedException, IOException {
        GitClient gitClient = mock(GitClient.class);

        ObjectId id = mock(ObjectId.class);
        when(gitClient.revParse(HEAD)).thenReturn(id);
        when(gitClient.withRepository(any())).thenThrow(new IOException());
        FilePath workTree = createWorkTreeStub();
        when(gitClient.getWorkTree()).thenReturn(workTree);

        GitBlamer blamer = new GitBlamer(gitClient, HEAD);
        Blames blames = blamer.blame(new FileLocations());

        assertThat(blames).isEmpty();
        assertThat(blames).hasErrorMessages(GitBlamer.BLAME_ERROR);
    }

    private FilePath createWorkTreeStub() {
        File mock = mock(File.class);
        when(mock.getPath()).thenReturn("/");
        return new FilePath(mock);
    }

    @Test
    void shouldFinishWithIntermediateResultIfInterrupted() throws InterruptedException, IOException {
        GitClient gitClient = mock(GitClient.class);

        ObjectId id = mock(ObjectId.class);
        when(gitClient.revParse(HEAD)).thenReturn(id);
        when(gitClient.withRepository(any())).thenThrow(new InterruptedException());
        FilePath workTree = createWorkTreeStub();
        when(gitClient.getWorkTree()).thenReturn(workTree);

        GitBlamer blamer = new GitBlamer(gitClient, HEAD);
        Blames blames = blamer.blame(new FileLocations());

        assertThat(blames).isEmpty();
        assertThat(blames).hasNoErrorMessages();
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
        BlameCallback callback = new BlameCallback(blamerInput, blames, mock(ObjectId.class));

        BlameRunner runner = mock(BlameRunner.class);
        when(runner.run("exception")).thenThrow(exception);
        callback.run("exception", runner);

        assertThat(blames.getErrorMessages()).hasSize(3);
        assertThat(blames.getErrorMessages().get(1)).startsWith(
                "- error running git blame on 'exception' with revision");
        assertThat(blames.getErrorMessages().get(2)).startsWith(exception.getName());
    }

    @Test
    void shouldMapResultToRequestWithOneLine() throws GitAPIException {
        FileLocations input = new FileLocations();
        input.addLine("file", 1);

        Blames blames = new Blames();
        BlameCallback callback = new BlameCallback(input, blames, mock(ObjectId.class));

        BlameResult result = createResult(1);

        stubResultForIndex(result, 0);

        BlameRunner blameRunner = createBlameRunner(result);
        callback.run("file", blameRunner);

        verifyResult(blames.get("file"), 1);
    }

    @Test
    void shouldMapResultToRequestWithTwoLines() throws GitAPIException {
        FileLocations input = new FileLocations();
        input.addLine("file", 1);
        input.addLine("file", 2);

        Blames blames = new Blames();
        BlameCallback callback = new BlameCallback(input, blames, mock(ObjectId.class));

        BlameResult result = createResult(2);

        stubResultForIndex(result, 0);
        stubResultForIndex(result, 1);

        BlameRunner blameRunner = createBlameRunner(result);
        callback.run("file", blameRunner);

        FileBlame blame = blames.get("file");
        verifyResult(blame, 1);
        verifyResult(blame, 2);
    }

    @Test
    void shouldMapResultToRequestOutOfRange() throws GitAPIException {
        FileLocations input = new FileLocations();
        input.addLine("file", 1);
        input.addLine("file", 2);

        Blames blames = new Blames();
        BlameCallback callback = new BlameCallback(input, blames, mock(ObjectId.class));

        BlameResult result = createResult(1);
        stubResultForIndex(result, 0);

        BlameRunner blameRunner = createBlameRunner(result);
        callback.run("file", blameRunner);

        FileBlame blame = blames.get("file");
        verifyResult(blame, 1);

        assertThat(blame.getEmail(3)).isEqualTo(EMPTY);
        assertThat(blame.getName(3)).isEqualTo(EMPTY);
        assertThat(blame.getCommit(3)).isEqualTo(EMPTY);

        callback.run("otherFile", blameRunner);
        assertThat(blames).hasErrorMessages("- no blame results for file <otherFile>");
    }

    @Test
    void shouldIgnoreMissingCommit() throws GitAPIException {
        FileLocations input = new FileLocations();
        input.addLine("file", 1);

        Blames blames = new Blames();
        BlameCallback callback = new BlameCallback(input, blames, mock(ObjectId.class));

        BlameResult result = createResult(1);
        when(result.getSourceAuthor(0)).thenReturn(new PersonIdent(NAME, EMAIL));

        BlameRunner blameRunner = createBlameRunner(result);
        callback.run("file", blameRunner);

        FileBlame blame = blames.get("file");
        assertThat(blame.getEmail(1)).isEqualTo(EMAIL);
        assertThat(blame.getName(1)).isEqualTo(NAME);
        assertThat(blame.getCommit(1)).isEqualTo(EMPTY);
    }

    @Test
    void shouldIgnoreMissingAuthorAndCommitter() throws GitAPIException {
        FileLocations input = new FileLocations();
        input.addLine("file", 1);

        Blames blames = new Blames();
        BlameCallback callback = new BlameCallback(input, blames, mock(ObjectId.class));

        BlameResult result = createResult(1);
        RevCommit commit = mock(RevCommit.class);
        when(result.getSourceCommit(0)).thenReturn(commit);

        BlameRunner blameRunner = createBlameRunner(result);
        callback.run("file", blameRunner);

        FileBlame blame = blames.get("file");
        assertThat(blame.getEmail(1)).isEqualTo(EMPTY);
        assertThat(blame.getName(1)).isEqualTo(EMPTY);
        assertThat(blame.getCommit(1)).isNotBlank().isNotEqualTo(EMPTY);
    }

    @Test
    void shouldUseCommitterIfAuthorIsMissing() throws GitAPIException {
        FileLocations input = new FileLocations();
        input.addLine("file", 1);

        Blames blames = new Blames();
        BlameCallback callback = new BlameCallback(input, blames, mock(ObjectId.class));

        BlameResult result = createResult(1);
        RevCommit commit = mock(RevCommit.class);
        when(result.getSourceCommit(0)).thenReturn(commit);
        when(result.getSourceCommitter(0)).thenReturn(new PersonIdent(NAME + 1, EMAIL + 1));

        BlameRunner blameRunner = createBlameRunner(result);
        callback.run("file", blameRunner);

        FileBlame blame = blames.get("file");
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
        when(blameRunner.run("file")).thenReturn(result);
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
        assertThat(request.getCommit(line)).isNotBlank().isNotEqualTo(EMPTY);// final getter
    }
}
