package io.jenkins.plugins.git.forensics.blame;

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

import io.jenkins.plugins.git.forensics.blame.GitBlamer.BlameCallback;
import io.jenkins.plugins.git.forensics.blame.GitBlamer.BlameRunner;

import static io.jenkins.plugins.plugins.git.forensics.assertions.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests the class {@link GitBlamer}.
 *
 * @author Ullrich Hafner
 */
class GitBlamerTest {
    private static final String EMAIL = "email";
    private static final String NAME = "name";

    @Test
    @Issue("JENKINS-55273")
    void shouldNotFailOnExceptions() throws GitAPIException {
        verifyExceptionHandling(NoHeadException.class);
        verifyExceptionHandling(JGitInternalException.class);
    }

    private void verifyExceptionHandling(final Class<? extends Exception> exception) throws GitAPIException {
        Blames blames = new Blames();
        BlamerInput blamerInput = new BlamerInput();
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
        BlamerInput input = new BlamerInput();
        input.addLine("file", 1);

        Blames blames = new Blames();
        BlameCallback callback = new BlameCallback(input, blames, mock(ObjectId.class));

        BlameResult result = createResult(1);

        createResultForLine(result, 0);

        BlameRunner blameRunner = createBlameRunner(result);
        callback.run("file", blameRunner);

        verifyResult(blames.get("file"), 1);
    }

    @Test
    void shouldMapResultToRequestWithTwoLines() throws GitAPIException {
        BlamerInput input = new BlamerInput();
        input.addLine("file", 1);
        input.addLine("file", 2);

        Blames blames = new Blames();
        BlameCallback callback = new BlameCallback(input, blames, mock(ObjectId.class));

        BlameResult result = createResult(2);

        createResultForLine(result, 0);
        createResultForLine(result, 1);

        BlameRunner blameRunner = createBlameRunner(result);
        callback.run("file", blameRunner);

        verifyResult(blames.get("file"), 1);
        verifyResult(blames.get("file"), 2);
    }

    @Test
    void shouldMapResultToRequestOutOfRange() throws GitAPIException {
        BlamerInput input = new BlamerInput();
        input.addLine("file", 1);
        BlameCallback callback = new BlameCallback(input, new Blames(), mock(ObjectId.class));

        BlameResult result = createResult(2);

        createResultForLine(result, 2);

        BlameRunner blameRunner = createBlameRunner(result);
        FileBlame request = new FileBlame("file").addLineNumber(3);
        callback.run("file", blameRunner);

        assertThat(request.getEmail(3)).isEqualTo("-");
        assertThat(request.getName(3)).isEqualTo("-");
        assertThat(request.getCommit(3)).isEqualTo("-");
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

    private void createResultForLine(final BlameResult result, final int index) {
        int line = index + 1;
        when(result.getSourceAuthor(index)).thenReturn(new PersonIdent(NAME + line, EMAIL + line));
        RevCommit commit = mock(RevCommit.class);
        when(result.getSourceCommit(index)).thenReturn(commit);
    }

    private void verifyResult(final FileBlame request, final int line) {
        assertThat(request.getEmail(line)).isEqualTo(EMAIL + line);
        assertThat(request.getName(line)).isEqualTo(NAME + line);
        assertThat(request.getCommit(line)).isNotBlank(); // final getter
    }
}
