package io.jenkins.plugins.forensics.git.miner;

import java.util.ArrayList;
import java.util.List;

/**
 * Computes the {@link IndentationLevel} metric for the textual content of a single file.
 *
 * <p>
 * The algorithm follows the recipe described in chapter 3 of Adam Tornhill's book <i>Your Code as a Crime
 * Scene</i>: for every non-blank line of a file, the <i>logical indentation level</i> of the leading whitespace is
 * determined, where one logical indentation level corresponds to either a single tab character or four consecutive
 * space characters at the start of the line. Blank (or whitespace-only) lines are skipped entirely, since they do
 * not carry any indentation signal. The per-line levels are then aggregated by {@link IndentationLevel}.
 * </p>
 *
 * <p>
 * Indentation correlates with the shape of the code: deeply nested {@code if}/{@code for}/{@code while} blocks push
 * the following lines further to the right. Since the analysis only looks at the "negative space" of a file rather
 * than its actual tokens, it is completely language-agnostic and very cheap to compute, which makes it a useful,
 * lightweight complement to more precise (but language-specific) complexity metrics such as cyclomatic complexity.
 * </p>
 *
 * @author Akash Manna
 * @see IndentationLevel
 */
public class IndentationLevelCalculator {
    /** The number of consecutive leading space characters that count as a single logical indentation level. */
    static final int SPACES_PER_LEVEL = 4;

    /**
     * Computes the {@link IndentationLevel} for the specified lines of a file.
     *
     * @param lines
     *         the lines of the file, in their original order; must not be {@code null}, but may be empty
     *
     * @return the aggregated indentation level for the specified lines
     */
    public IndentationLevel compute(final Iterable<String> lines) {
        List<Integer> levelsOfNonBlankLines = new ArrayList<>();
        for (String line : lines) {
            if (isBlank(line)) {
                continue;
            }
            levelsOfNonBlankLines.add(countIndentationLevel(line));
        }
        return new IndentationLevel(levelsOfNonBlankLines);
    }

    /**
     * Computes the {@link IndentationLevel} for the specified textual content of a file. The content is split into
     * lines using {@code \n}, {@code \r\n}, or {@code \r} as line separator.
     *
     * @param content
     *         the full textual content of the file; may be {@code null} or empty, in which case an
     *         {@link IndentationLevel#isEmpty() empty} result is returned
     *
     * @return the aggregated indentation level for the specified content
     */
    public IndentationLevel compute(final String content) {
        if (content == null || content.isEmpty()) {
            return compute(List.of());
        }
        return compute(List.of(content.split("\r\n|\r|\n", -1)));
    }

    private boolean isBlank(final String line) {
        return line == null || line.isBlank();
    }

    /**
     * Determines the logical indentation level of a single line, i.e. the number of complete indentation units
     * (one tab, or four consecutive spaces) found at the start of the line, before the first non-whitespace
     * character (or the end of the line) is reached.
     *
     * <p>
     * Tabs and spaces are counted independently: a run of leading spaces that is shorter than
     * {@link #SPACES_PER_LEVEL} does not contribute a level, and a tab character does not "absorb" any pending
     * spaces that preceded it (each is counted using its own rule).
     * </p>
     *
     * @param line
     *         a single, non-blank line of a file
     *
     * @return the logical indentation level of the line, i.e. a value {@code >= 0}
     */
    int countIndentationLevel(final String line) {
        int pendingSpaces = 0;
        int level = 0;

        for (int i = 0; i < line.length(); i++) {
            char character = line.charAt(i);
            if (character == '\t') {
                level++;
                pendingSpaces = 0;
            }
            else if (character == ' ') {
                pendingSpaces++;
                if (pendingSpaces == SPACES_PER_LEVEL) {
                    level++;
                    pendingSpaces = 0;
                }
            }
            else {
                break; // the leading whitespace prefix of the line ends here
            }
        }

        return level;
    }
}