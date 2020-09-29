package io.jenkins.plugins.forensics.git.miner;

/**
 * Helper class used to keep track of lines of code changes for a file concerning a specific commit.
 *
 * @author Giulia Del Bravo
 */
@SuppressWarnings("PMD.DataClass")
public class LocChanges {

    private int totalLoc;
    private final String commitId;
    private int totalAddedLines;
    private int deletedLines;

    /**
     * Creates a new instance of {@link LocChanges}.
     *
     * @param commitId
     *         The id of the commit the changes were made.
     */
    public LocChanges(final String commitId) {
        this.commitId = commitId;
        totalLoc = 0;
        totalAddedLines = 0;
        deletedLines = 0;
    }

    /**
     * Updates the LocChanges.
     *
     * @param addedLines
     *         the number of added lines
     * @param removedLines
     *         the number of deleted lines
     */
    public void updateLocChanges(final int addedLines, final int removedLines) {
        totalLoc += addedLines;
        totalLoc -= removedLines;
        this.totalAddedLines += addedLines;
        this.deletedLines += removedLines;
    }

    public int getTotalLoc() {
        return totalLoc;
    }

    public int getTotalAddedLines() {
        return totalAddedLines;
    }

    public int getDeletedLines() {
        return deletedLines;
    }

    public String getCommitId() {
        return commitId;
    }

}
