package io.jenkins.plugins.forensics.git.miner;

/**
 * Keeps track of lines of code changes for a file concerning a specific commit.
 *
 * @author Giulia Del Bravo
 */
@SuppressWarnings("PMD.DataClass")
public class CommitFileDelta {
    private final String commitId;

    private int totalAddedLines = 0;
    private int totalDeletedLines = 0;

    /**
     * Creates a new instance of {@link CommitFileDelta}.
     *
     * @param commitId
     *         ID of the commit that contains the changes
     */
    public CommitFileDelta(final String commitId) {
        this.commitId = commitId;
    }

    /**
     * Updates the LocChanges.
     *
     * @param addedLines
     *         the number of added lines
     * @param deletedLines
     *         the number of deleted lines
     */
    public void updateDelta(final int addedLines, final int deletedLines) {
        this.totalAddedLines += addedLines;
        this.totalDeletedLines += deletedLines;
    }

    public int getTotalLoc() {
        return totalAddedLines - totalDeletedLines;
    }

    public int getAddedLines() {
        return totalAddedLines;
    }

    public int getDeletedLines() {
        return totalDeletedLines;
    }

    public String getCommitId() {
        return commitId;
    }

}
