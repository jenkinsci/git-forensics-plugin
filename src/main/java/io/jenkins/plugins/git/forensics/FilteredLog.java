package io.jenkins.plugins.git.forensics;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.w3c.tidy.Report;

import com.google.errorprone.annotations.FormatMethod;

import static java.lang.String.*;

/**
 * Provides a log file with a limited number of error messages. If the number of errors exceeds this limit, then
 * subsequent error messages will be skipped.
 *
 * @author Ullrich Hafner
 */
public class FilteredLog implements Serializable {
    private static final long serialVersionUID = -8552323621953159904L;

    private static final String SKIPPED_MESSAGE = "  ... skipped logging of %d additional errors ...";
    private static final int DEFAULT_MAX_LINES = 20;

    private final String title;
    private final int maxLines;
    private int lines = 0;

    private final List<String> infoMessages = new ArrayList<>();
    private final List<String> errorMessages = new ArrayList<>();

    /**
     * Creates a new {@link FilteredLog} for a {@link Report}. Number of printed errors: {@link #DEFAULT_MAX_LINES}.
     *
     * @param title
     *         the title of the error messages
     */
    public FilteredLog(final String title) {
        this(title, DEFAULT_MAX_LINES);
    }

    /**
     * Creates a new {@link FilteredLog} for a {@link Report}.
     *
     * @param title
     *         the title of the error messages
     * @param maxLines
     *         the maximum number of lines to log
     */
    public FilteredLog(final String title, final int maxLines) {
        this.title = title;
        this.maxLines = maxLines;
    }

    /**
     * Logs the specified information message. Use this method to log any useful information when composing this log.
     *
     * @param format
     *         A <a href="../util/Formatter.html#syntax">format string</a>
     * @param args
     *         Arguments referenced by the format specifiers in the format string.  If there are more arguments than
     *         format specifiers, the extra arguments are ignored.  The number of arguments is variable and may be
     *         zero.
     */
    @FormatMethod
    public void logInfo(final String format, final Object... args) {
        infoMessages.add(format(format, args));
    }

    /**
     * Logs the specified error message. Use this method to log any error when composing this log.
     *
     * @param format
     *         A <a href="../util/Formatter.html#syntax">format string</a>
     * @param args
     *         Arguments referenced by the format specifiers in the format string.  If there are more arguments than
     *         format specifiers, the extra arguments are ignored.  The number of arguments is variable and may be
     *         zero.
     */
    @FormatMethod
    public void logError(final String format, final Object... args) {
        printTitle();

        if (lines < maxLines) {
            errorMessages.add(format(format, args));
        }
        lines++;
    }

    private void printTitle() {
        if (lines == 0) {
            errorMessages.add(title);
        }
    }

    /**
     * Logs the specified exception. Use this method to log any exception when composing this log.
     *
     * @param exception
     *         the exception to log
     * @param format
     *         A <a href="../util/Formatter.html#syntax">format string</a>
     * @param args
     *         Arguments referenced by the format specifiers in the format string.  If there are more arguments than
     *         format specifiers, the extra arguments are ignored.  The number of arguments is variable and may be
     *         zero.
     */
    @FormatMethod
    public void logException(final Exception exception, final String format, final Object... args) {
        printTitle();

        if (lines < maxLines) {
            errorMessages.add(String.format(format, args));
            errorMessages.addAll(Arrays.asList(ExceptionUtils.getRootCauseStackTrace(exception)));
        }
        lines++;
    }

    /**
     * Returns the total number of errors that have been reported.
     *
     * @return the total number of errors
     */
    public int size() {
        return lines;
    }

    /**
     * Writes a summary message to the reports' error log that denotes the total number of errors that have been
     * reported.
     */
    public void logSummary() {
        if (lines > maxLines) {
            errorMessages.add(String.format(SKIPPED_MESSAGE, lines - maxLines));
        }
    }

    /**
     * Returns the info messages that have been reported since the creation of this set of issues.
     *
     * @return the info messages
     */
    public List<String> getInfoMessages() {
        return Collections.unmodifiableList(infoMessages);
    }

    /**
     * Returns the error messages that have been reported since the creation of this set of issues.
     *
     * @return the error messages
     */
    public List<String> getErrorMessages() {
        return Collections.unmodifiableList(errorMessages);
    }

}
