package io.jenkins.plugins.git.forensics.blame;

import java.io.Serializable;
import java.util.HashSet;
import java.util.NoSuchElementException;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.collections.api.multimap.MutableMultimap;
import org.eclipse.collections.impl.factory.Multimaps;

import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * Defines a request to obtain the blames for a collection of workspace files (and locations inside each file).
 *
 * @author Ullrich Hafner
 */
public class BlamerInput implements Serializable {
    private static final long serialVersionUID = -7884822502506035784L;

    private final MutableMultimap<String, Integer> blamesPerFile = Multimaps.mutable.set.empty();
    private final Set<String> skippedFiles = new HashSet<>();
    private final String workspace;

    /**
     * Creates an empty instance of {@link BlamerInput}.
     */
    public BlamerInput() {
        this(StringUtils.EMPTY);
    }

    /**
     * Creates an empty instance of {@link BlamerInput} that will work on the specified workspace.
     *
     * @param workspace
     *         the workspace to get the Git repository from
     */
    public BlamerInput(final String workspace) {
        this.workspace = normalizeFileName(workspace);
    }

    private String normalizeFileName(@Nullable final String platformFileName) {
        return StringUtils.replace(StringUtils.strip(platformFileName), "\\", "/");
    }

    /**
     * Returns whether there are files with blames in this instance.
     *
     * @return {@code true} if there a no blames available, {@code false} otherwise
     */
    public boolean isEmpty() {
        return blamesPerFile.isEmpty();
    }

    /**
     * Returns the number of files that have been added to this instance.
     *
     * @return number of affected files with blames
     */
    public int size() {
        return blamesPerFile.keySet().size();
    }

    /**
     * Returns whether the specified file already has been added.
     *
     * @param fileName
     *         the name of the file
     *
     * @return {@code true} if the file already has been added, {@code false} otherwise
     */
    public boolean contains(final String fileName) {
        return blamesPerFile.containsKey(fileName);
    }

    public Set<String> getSkippedFiles() {
        return skippedFiles;
    }

    /**
     * Adds a blame request for the specified affected file and line number. This file and line will be processed by Git
     * blame later on.
     *
     * @param fileName
     *         the absolute file name that will be used as a key
     * @param lineStart
     *         the line number to find the blame for
     */
    public void addLine(final String fileName, final int lineStart) {
        if (contains(fileName)) {
            blamesPerFile.put(fileName, lineStart);
        }
        else {
            if (fileName.startsWith(workspace)) {
                String relativeFileName = fileName.substring(workspace.length());
                String cleanFileName = StringUtils.removeStart(relativeFileName, "/");
                blamesPerFile.put(cleanFileName, lineStart);
            }
            else {
                skippedFiles.add(fileName);
            }
        }
    }

    /**
     * Returns the absolute file names of the affected files that will be processed by Git blame.
     *
     * @return the file names
     */
    public Set<String> getFiles() {
        return blamesPerFile.keySet().toSet();
    }

    /**
     * Returns the blames for the specified file.
     *
     * @param fileName
     *         absolute file name
     *
     * @return the blames for that file
     * @throws NoSuchElementException
     *         if the file name is not registered
     */
    public Set<Integer> get(final String fileName) {
        if (contains(fileName)) {
            return blamesPerFile.get(fileName).toSet();
        }
        throw new NoSuchElementException(String.format("No information for file %s stored", fileName));
    }
}
