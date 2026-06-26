package io.jenkins.plugins.forensics.git.miner;

import edu.hm.hafner.util.Generated;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collections;
import java.util.IntSummaryStatistics;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;

/**
 * Aggregates the logical indentation levels of all non-blank lines of a single source file into a single
 * statistical summary. Indentation is used as a simple, language-agnostic proxy for the complexity of a file: the
 * more deeply nested the code is, the more "negative space" (leading whitespace) each line tends to have.
 *
 * <p>
 * This metric is described in detail in chapter 3 ("The Evolution of Software") of Adam Tornhill's book
 * <i>Your Code as a Crime Scene</i>, which in turn is based on the research published in Hindle, Godfrey, and
 * Holt, <i>Reading Beside the Lines: Indentation as a Proxy for Complexity Metric</i>, Program Comprehension, 2008.
 * ICPC 2008.
 * </p>
 *
 * <p>
 * Following the recipe from the book, an instance of this class stores, for the lines of a single file:
 * </p>
 * <ul>
 *     <li>{@code n}: the {@link #getNumberOfLines() number of (non-blank) lines}</li>
 *     <li>{@code total}: the {@link #getTotal() sum of the indentation levels} of all lines</li>
 *     <li>{@code mean}: the {@link #getMean() average indentation level} per line</li>
 *     <li>{@code sd}: the {@link #getStandardDeviation() standard deviation} of the indentation levels</li>
 *     <li>{@code max}: the {@link #getMaximum() maximum indentation level} found in the file</li>
 * </ul>
 *
 * <p>
 * Instances of this class are created by {@link IndentationLevelCalculator}, which also defines what a "logical
 * indentation level" actually is.
 * </p>
 *
 * @author Akash Manna
 * @see IndentationLevelCalculator
 */
public final class IndentationLevel implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L; // since 1.x.x

    /** A constant for a file that does not contain any (non-blank) lines. */
    static final IndentationLevel EMPTY = new IndentationLevel(Collections.emptyList());

    private final int numberOfLines;
    private final int total;
    private final int maximum;
    private final double mean;
    private final double standardDeviation;

    /**
     * Creates a new instance of {@link IndentationLevel} by aggregating the specified per-line indentation levels.
     *
     * @param indentationLevelsPerLine
     *         the logical indentation level of every non-blank line of the analyzed file, in any order
     */
    IndentationLevel(final List<Integer> indentationLevelsPerLine) {
        Objects.requireNonNull(indentationLevelsPerLine, "indentationLevelsPerLine must not be null");

        numberOfLines = indentationLevelsPerLine.size();
        if (numberOfLines == 0) {
            total = 0;
            maximum = 0;
            mean = 0.0;
            standardDeviation = 0.0;

            return;
        }

        IntSummaryStatistics statistics = indentationLevelsPerLine.stream()
                .mapToInt(Integer::intValue)
                .summaryStatistics();
        total = (int) statistics.getSum();
        maximum = statistics.getMax();
        mean = statistics.getAverage();

        double sumOfSquaredDeviations = indentationLevelsPerLine.stream()
                .mapToDouble(level -> Math.pow(level - mean, 2))
                .sum();
        standardDeviation = Math.sqrt(sumOfSquaredDeviations / numberOfLines);
    }

    /**
     * Returns the number of non-blank lines that have been used to compute this summary. Blank (or
     * whitespace-only) lines are not considered, since they do not carry any indentation signal.
     *
     * @return the number of analyzed lines, i.e. {@code n} in the original recipe
     */
    public int getNumberOfLines() {
        return numberOfLines;
    }

    /**
     * Returns the sum of the indentation levels of all analyzed lines.
     *
     * @return the total indentation, i.e. {@code total} in the original recipe
     */
    public int getTotal() {
        return total;
    }

    /**
     * Returns the highest indentation level that has been found in any of the analyzed lines.
     *
     * @return the maximum indentation level, i.e. {@code max} in the original recipe
     */
    public int getMaximum() {
        return maximum;
    }

    /**
     * Returns the average indentation level of the analyzed lines.
     *
     * @return the mean indentation level, i.e. {@code mean} in the original recipe, or {@code 0.0} if there are
     *         no analyzed lines
     */
    public double getMean() {
        return mean;
    }

    /**
     * Returns the (population) standard deviation of the indentation levels of the analyzed lines. A high standard
     * deviation together with a high mean is a strong indicator of a file that mixes very simple and excessively
     * nested code, which is a typical refactoring candidate.
     *
     * @return the standard deviation, i.e. {@code sd} in the original recipe, or {@code 0.0} if there are no
     *         analyzed lines
     */
    public double getStandardDeviation() {
        return standardDeviation;
    }

    /**
     * Returns whether this summary does not contain any analyzed lines, e.g. because the file was empty, contained
     * only blank lines, or could not be read.
     *
     * @return {@code true} if there are no analyzed lines, {@code false} otherwise
     */
    public boolean isEmpty() {
        return numberOfLines == 0;
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
        var that = (IndentationLevel) o;
        return numberOfLines == that.numberOfLines
                && total == that.total
                && maximum == that.maximum
                && Double.compare(that.mean, mean) == 0
                && Double.compare(that.standardDeviation, standardDeviation) == 0;
    }

    @Override
    @Generated
    public int hashCode() {
        return Objects.hash(numberOfLines, total, maximum, mean, standardDeviation);
    }

    @Override
    @Generated
    public String toString() {
        return new StringJoiner(", ", IndentationLevel.class.getSimpleName() + "[", "]")
                .add("numberOfLines=" + numberOfLines)
                .add("total=" + total)
                .add("maximum=" + maximum)
                .add("mean=" + mean)
                .add("standardDeviation=" + standardDeviation)
                .toString();
    }
}