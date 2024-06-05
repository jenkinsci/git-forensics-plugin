package io.jenkins.plugins.forensics.git.blame;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;
import org.junitpioneer.jupiter.Issue;

import edu.hm.hafner.util.FilteredLog;

import io.jenkins.plugins.forensics.blame.Blames;
import io.jenkins.plugins.forensics.blame.FileBlame;
import io.jenkins.plugins.forensics.blame.FileLocations;
import io.jenkins.plugins.forensics.git.util.GitITest;

import static io.jenkins.plugins.forensics.assertions.Assertions.*;

/**
 * Tests the class {@link GitBlamer}.
 *
 * @author Ullrich Hafner
 */
class GitBlamerITest extends GitITest {
    /**
     * Verifies that the blames are empty if there are no requests defined.
     */
    @Test
    void shouldCreateEmptyBlamesIfRequestIsEmpty() {
        GitBlamer gitBlamer = createBlamer();

        FilteredLog log = createLog();
        Blames blames = gitBlamer.blame(new FileLocations(), log);

        assertThat(blames).isEmpty();
        assertThat(log.getInfoMessages()).contains("-> Git commit ID = '" + getHead() + "'");
        assertThat(log.getInfoMessages()).contains("-> blamed authors of issues in 0 files");
    }

    /**
     * Verifies that the blames are empty if there are no requests defined.
     */
    @Test
    void shouldCreateBlamesIfRequestIsExistingFile() {
        create2RevisionsWithDifferentAuthors();

        FileLocations locations = new FileLocations();
        String absolutePath = ADDITIONAL_FILE;
        locations.addLine(absolutePath, 2);
        locations.addLine(absolutePath, 3);
        locations.addLine(absolutePath, 4);
        locations.addLine(absolutePath, 5);

        GitBlamer gitBlamer = createBlamer();

        FilteredLog log = createLog();
        Blames blames = gitBlamer.blame(locations, log);

        assertThat(blames).hasOnlyFiles(absolutePath);
        assertThat(log.getErrorMessages()).isEmpty();
        assertThat(log.getInfoMessages()).contains("-> blamed authors of issues in 1 files");

        FileBlame request = blames.getBlame(absolutePath);
        assertThat(request).hasFileName(absolutePath);

        assertThatBlameIsEmpty(request, 1);
        assertThatBlameIs(request, 2);
        assertThatBlameIsHeadWith(request, 3);
        assertThatBlameIsHeadWith(request, 4);
        assertThatBlameIs(request, 5);
        assertThatBlameIsEmpty(request, 6);
    }

    /**
     * Verifies that the last committer of the whole file is used if no specific line number is given.
     */
    @Test
    @Issue("JENKINS-59252")
    void shouldAssignLastCommitterIfNoLineNumberIsGiven() {
        create2RevisionsWithDifferentAuthors();

        FileLocations locations = new FileLocations();
        String absolutePath = ADDITIONAL_FILE;
        locations.addLine(absolutePath, 0);

        GitBlamer gitBlamer = createBlamer();

        FilteredLog log = createLog();
        Blames blames = gitBlamer.blame(locations, log);

        assertThat(blames).hasOnlyFiles(absolutePath);
        assertThat(log.getErrorMessages()).isEmpty();
        assertThat(log.getInfoMessages()).contains("-> blamed authors of issues in 1 files");

        FileBlame request = blames.getBlame(absolutePath);
        assertThat(request).hasFileName(absolutePath);

        assertThat(request.getName(0)).isEqualTo(BAR_NAME);
        assertThat(request.getEmail(0)).isEqualTo(BAR_EMAIL);
        assertThat(request.getCommit(0)).isEqualTo(getHead());
    }

    private FilteredLog createLog() {
        return new FilteredLog(StringUtils.EMPTY);
    }

    private GitBlamer createBlamer() {
        return new GitBlamer(createGitClient(), "HEAD");
    }

    private void create2RevisionsWithDifferentAuthors() {
        writeFile(ADDITIONAL_FILE, "OLD\nOLD\nOLD\nOLD\nOLD\nOLD\n");
        git("add", ADDITIONAL_FILE);
        git("config", "user.name", FOO_NAME);
        git("config", "user.email", FOO_EMAIL);
        git("commit", "--message=Init");
        git("rev-parse", "HEAD");

        writeFile(ADDITIONAL_FILE, "OLD\nOLD\nNEW\nNEW\nOLD\nOLD\n");
        git("add", ADDITIONAL_FILE);
        git("config", "user.name", BAR_NAME);
        git("config", "user.email", BAR_EMAIL);
        git("commit", "--message=Change");
        git("rev-parse", "HEAD");
    }

    private void assertThatBlameIsHeadWith(final FileBlame request, final int line) {
        assertThat(request.getName(line)).isEqualTo(BAR_NAME);
        assertThat(request.getEmail(line)).isEqualTo(BAR_EMAIL);
        assertThat(request.getCommit(line)).isEqualTo(getHead());
    }

    private void assertThatBlameIs(final FileBlame request, final int line) {
        assertThat(request.getName(line)).isEqualTo(FOO_NAME);
        assertThat(request.getEmail(line)).isEqualTo(FOO_EMAIL);
        assertThat(request.getCommit(line)).isNotEqualTo(getHead());
    }

    private void assertThatBlameIsEmpty(final FileBlame request, final int line) {
        assertThat(request.getName(line)).isEqualTo("-");
        assertThat(request.getEmail(line)).isEqualTo("-");
        assertThat(request.getCommit(line)).isNotEqualTo(getHead());
    }
}
