package io.jenkins.plugins.forensics.git.delta;

import java.util.Map;
import java.util.Objects;

import io.jenkins.plugins.forensics.delta.Delta;
import io.jenkins.plugins.forensics.delta.FileChanges;

/**
 * A Git specific extension of {@link Delta}.
 *
 * @author Florian Orendi
 */
public class GitDelta extends Delta {
    private static final long serialVersionUID = 4075956106966630282L;

    /**
     * The Diff-File which has been created by Git and wraps up all made changes between two commits.
     */
    private final String diffFile;

    /**
     * Constructor for a delta instance which wraps code changes between the two passed commits.
     *
     * @param currentCommit
     *         the currently processed commit
     * @param referenceCommit
     *         the reference commit
     * @param fileChanges
     *         the map which contains the changes for modified files, mapped by the file ID.
     * @param diffFile
     *         the Diff-File which has been created by Git and wraps up all made changes between two commits.
     */
    public GitDelta(final String currentCommit, final String referenceCommit,
            final Map<String, FileChanges> fileChanges, final String diffFile) {
        super(currentCommit, referenceCommit, fileChanges);
        this.diffFile = diffFile;
    }

    public String getDiffFile() {
        return diffFile;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        GitDelta gitDelta = (GitDelta) o;
        return Objects.equals(diffFile, gitDelta.diffFile);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), diffFile);
    }
}
