package io.jenkins.plugins.forensics.git.miner;

import edu.hm.hafner.util.Generated;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;
import java.util.StringJoiner;

/**
 * Represents the temporal coupling between two files in a Git repository. Two files are temporally coupled if they are
 * frequently changed together in the same commit. This metric is described in "Your Code as a Crime Scene" by Adam
 * Tornhill (page 72) and reveals hidden architectural dependencies: files that are always modified together likely
 * share responsibilities and should potentially be merged or refactored.
 *
 * <p>The coupling strength is measured by two values:
 * <ul>
 *   <li>{@code coChanges}: the absolute number of commits in which both files appeared together</li>
 *   <li>{@code couplingRatio}: the ratio of co-changes to the total commits of the less-frequently-committed file,
 *       expressed as a value between 0.0 and 1.0 (1.0 = always changed together)</li>
 * </ul>
 *
 * @author Akash Manna
 */
public class TemporalCoupling implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private final String leftFile;
    private final String rightFile;
    private final int coChanges;
    private final double couplingRatio;

    /**
     * Creates a new {@link TemporalCoupling} instance.
     *
     * @param leftFile
     *         the path of the first file
     * @param rightFile
     *         the path of the second file
     * @param coChanges
     *         the number of commits in which both files appeared together
     * @param couplingRatio
     *         the coupling ratio (co-changes / min(commits of leftFile, commits of rightFile))
     */
    public TemporalCoupling(final String leftFile, final String rightFile,
            final int coChanges, final double couplingRatio) {
        this.leftFile = leftFile;
        this.rightFile = rightFile;
        this.coChanges = coChanges;
        this.couplingRatio = couplingRatio;
    }

    /**
     * Returns the path of the first file in this coupling pair.
     *
     * @return the path of the first file
     */
    public String getLeftFile() {
        return leftFile;
    }

    /**
     * Returns the path of the second file in this coupling pair.
     *
     * @return the path of the second file
     */
    public String getRightFile() {
        return rightFile;
    }

    /**
     * Returns the number of commits in which both files were changed together.
     *
     * @return the absolute co-change count
     */
    public int getCoChanges() {
        return coChanges;
    }

    /**
     * Returns the coupling ratio: the fraction of the less-frequently-committed file's commits in which the other
     * file also appeared. A value of 1.0 means the two files are always changed together.
     *
     * @return the coupling ratio in [0.0, 1.0]
     */
    public double getCouplingRatio() {
        return couplingRatio;
    }

    @Override
    @Generated
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        var that = (TemporalCoupling) o;
        return coChanges == that.coChanges
                && Double.compare(that.couplingRatio, couplingRatio) == 0
                && leftFile.equals(that.leftFile)
                && rightFile.equals(that.rightFile);
    }

    @Override
    @Generated
    public int hashCode() {
        return Objects.hash(leftFile, rightFile, coChanges, couplingRatio);
    }

    @Override
    @Generated
    public String toString() {
        return new StringJoiner(", ", TemporalCoupling.class.getSimpleName() + "[", "]")
                .add("leftFile='" + leftFile + "'")
                .add("rightFile='" + rightFile + "'")
                .add("coChanges=" + coChanges)
                .add("couplingRatio=" + couplingRatio)
                .toString();
    }
}