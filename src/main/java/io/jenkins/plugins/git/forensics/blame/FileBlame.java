package io.jenkins.plugins.git.forensics.blame;

import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Stores the repository blames for several lines of a single file.
 *
 * @author Ullrich Hafner
 */
public class FileBlame implements Iterable<Integer>, Serializable {
    private static final long serialVersionUID = -7491390234189584964L;

    static final String EMPTY = "-";

    private final String fileName;
    private final Set<Integer> lines = new HashSet<>();

    private final Map<Integer, String> commitByLine = new HashMap<>();
    private final Map<Integer, String> nameByLine = new HashMap<>();
    private final Map<Integer, String> emailByLine = new HashMap<>();

    /**
     * Creates a new instance of {@link FileBlame}.
     *
     * @param fileName
     *         the name of the file that should be blamed
     */
    public FileBlame(final String fileName) {
        this.fileName = fileName;
    }

    private FileBlame add(final int lineNumber) {
        lines.add(lineNumber);

        return this;
    }

    public Set<Integer> getLines() {
        return lines;
    }

    /**
     * Adds another line number to this request.
     *
     * @param lineNumber
     *         the line number to add
     */
    FileBlame addLineNumber(final int lineNumber) {
        return add(lineNumber);
    }

    public String getFileName() {
        return fileName;
    }

    @Override
    @NonNull
    public Iterator<Integer> iterator() {
        return lines.iterator();
    }

    /**
     * Sets the commit ID for the specified line number.
     *
     * @param lineNumber
     *         the line number
     * @param id
     *         the commit ID
     */
    void setCommit(final int lineNumber, final String id) {
        setInternedStringValue(commitByLine, lineNumber, id);
    }

    /**
     * Returns the commit ID for the specified line.
     *
     * @param line
     *         the affected line
     *
     * @return the commit ID
     */
    public String getCommit(final int line) {
        return getStringValue(commitByLine, line);
    }

    /**
     * Sets the author name for the specified line number.
     *
     * @param lineNumber
     *         the line number
     * @param name
     *         the author name
     */
    void setName(final int lineNumber, final String name) {
        setInternedStringValue(nameByLine, lineNumber, name);
    }

    /**
     * Returns the author name for the specified line.
     *
     * @param line
     *         the affected line
     *
     * @return the author name
     */
    public String getName(final int line) {
        return getStringValue(nameByLine, line);
    }

    /**
     * Sets the email address for the specified line number.
     *
     * @param lineNumber
     *         the line number
     * @param emailAddress
     *         the email address of the author
     */
    void setEmail(final int lineNumber, final String emailAddress) {
        setInternedStringValue(emailByLine, lineNumber, emailAddress);
    }

    /**
     * Returns the author email for the specified line.
     *
     * @param line
     *         the affected line
     *
     * @return the author email
     */
    public String getEmail(final int line) {
        return getStringValue(emailByLine, line);
    }

    private String getStringValue(final Map<Integer, String> map, final int line) {
        if (map.containsKey(line)) {
            return map.get(line);
        }
        return EMPTY;
    }

    private void setInternedStringValue(final Map<Integer, String> map, final int lineNumber, final String value) {
        map.put(lineNumber, value.intern());
        lines.add(lineNumber);
    }

    /**
     * Merges the additional lines of the other {@link FileBlame} instance with the lines of this instance.
     *
     * @param other
     *         the other blames
     *
     * @throws IllegalArgumentException
     *         if the file name of the other instance does not match
     */
    public void merge(final FileBlame other) {
        if (other.getFileName().equals(getFileName())) {
            for (Integer otherLine : other) {
                if (!lines.contains(otherLine)) {
                    lines.add(otherLine);
                    setInternedStringValue(commitByLine, otherLine, other.getCommit(otherLine));
                    setInternedStringValue(nameByLine, otherLine, other.getName(otherLine));
                    setInternedStringValue(emailByLine, otherLine, other.getEmail(otherLine));
                }
            }
        }
        else {
            throw new IllegalArgumentException(
                    String.format("File names must match! This instance: %s, other instance: %s",
                            getFileName(), other.getFileName()));
        }
    }

    @Override
    public String toString() {
        return fileName + " - " + lines;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        FileBlame request = (FileBlame) o;

        if (!fileName.equals(request.fileName)) {
            return false;
        }
        if (!lines.equals(request.lines)) {
            return false;
        }
        if (!commitByLine.equals(request.commitByLine)) {
            return false;
        }
        if (!nameByLine.equals(request.nameByLine)) {
            return false;
        }
        return emailByLine.equals(request.emailByLine);
    }

    @Override
    public int hashCode() {
        int result = fileName.hashCode();
        result = 31 * result + lines.hashCode();
        result = 31 * result + commitByLine.hashCode();
        result = 31 * result + nameByLine.hashCode();
        result = 31 * result + emailByLine.hashCode();
        return result;
    }
}
