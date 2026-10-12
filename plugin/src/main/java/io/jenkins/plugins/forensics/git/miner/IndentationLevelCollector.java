package io.jenkins.plugins.forensics.git.miner;

import org.eclipse.jgit.errors.LargeObjectException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;

import edu.hm.hafner.util.FilteredLog;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Collects the {@link IndentationLevel} metric for each regular (non-binary) file in a Git commit.
 *
 * <p>Unlike {@link DiffsCollector}, which analyzes only modified lines, this collector reads the complete
 * contents of each file because indentation is a property of the entire file. Binary, unreadable, or oversized
 * files are skipped, and the reason is recorded in the provided {@link FilteredLog}.</p>
 *
 * @author Akash Manna
 * @see IndentationLevelCalculator
 */
public class IndentationLevelCollector {
    /**
     * The maximum number of bytes that will be read into memory for a single file. Files larger than this limit are
     * skipped, in order to avoid excessive memory usage when mining large (e.g. binary or generated) files.
     */
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024L; // 5 MB

    /** The number of leading bytes that are inspected by the binary-file heuristic. */
    private static final int BINARY_DETECTION_BUFFER_SIZE = 8000;

    private final IndentationLevelCalculator calculator = new IndentationLevelCalculator();

    /**
     * Collects the {@link IndentationLevel} for every regular file that is part of the specified commit.
     *
     * @param repository
     *         the repository to read the file contents from
     * @param commitId
     *         the ID of the commit whose snapshot should be analyzed
     * @param logger
     *         the logger used to report skipped or unreadable files
     *
     * @return a mapping of repository-relative file path to the {@link IndentationLevel} of that file; iteration
     *         order follows the order in which the files are visited in the tree
     */
    public Map<String, IndentationLevel> collect(final Repository repository, final ObjectId commitId,
            final FilteredLog logger) {
        Map<String, IndentationLevel> indentationLevelsByFile = new LinkedHashMap<>();

        try (var revWalk = new RevWalk(repository); var reader = repository.newObjectReader()) {
            RevCommit commit = revWalk.parseCommit(commitId);
            RevTree tree = commit.getTree();

            try (var treeWalk = new TreeWalk(repository, reader)) {
                treeWalk.addTree(tree);
                treeWalk.setRecursive(true);

                while (treeWalk.next()) {
                    if (!isRegularFile(treeWalk.getFileMode(0))) {
                        continue;
                    }

                    String path = treeWalk.getPathString();
                    ObjectId blobId = treeWalk.getObjectId(0);

                    readIndentationLevel(reader, path, blobId, logger)
                            .ifPresent(level -> indentationLevelsByFile.put(path, level));
                }
            }
        }
        catch (IOException exception) {
            logger.logException(exception,
                    "Can't compute indentation levels for commit '%s'", commitId.getName());
        }

        return indentationLevelsByFile;
    }

    private Optional<IndentationLevel> readIndentationLevel(final ObjectReader reader, final String path,
            final ObjectId blobId, final FilteredLog logger) {
        try {
            ObjectLoader loader = reader.open(blobId);
            if (loader.getSize() > MAX_FILE_SIZE) {
                logger.logInfo("Skipping file '%s': size %d bytes exceeds the limit of %d bytes",
                        path, loader.getSize(), MAX_FILE_SIZE);

                return Optional.empty();
            }

            byte[] content = loader.getCachedBytes(Math.toIntExact(MAX_FILE_SIZE));
            if (isBinary(content)) {
                return Optional.empty();
            }

            String text = new String(content, StandardCharsets.UTF_8);

            return Optional.of(calculator.compute(text));
        }
        catch (MissingObjectException exception) {
            logger.logException(exception, "Can't find content of file '%s'", path);
        }
        catch (LargeObjectException exception) {
            logger.logInfo("Skipping file '%s': %s", path, exception.getMessage());
        }
        catch (IOException exception) {
            logger.logException(exception, "Can't read content of file '%s'", path);
        }

        return Optional.empty();
    }

    /**
     * Returns whether the specified file mode represents a regular (non-symlink, non-submodule) file, i.e. a file
     * whose content can meaningfully be analyzed for indentation.
     *
     * @param fileMode
     *         the file mode as reported by the tree walk
     *
     * @return {@code true} if the file is a regular file (executable or not), {@code false} otherwise
     */
    private boolean isRegularFile(final FileMode fileMode) {
        return fileMode.equals(FileMode.REGULAR_FILE) || fileMode.equals(FileMode.EXECUTABLE_FILE);
    }

    /**
     * Applies the same heuristic that Git itself uses to distinguish text from binary files: a file is considered
     * binary if a {@code NUL} byte is found within the first {@link #BINARY_DETECTION_BUFFER_SIZE} bytes.
     *
     * @param content
     *         the bytes to inspect
     *
     * @return {@code true} if the content looks like a binary file, {@code false} otherwise
     */
    private boolean isBinary(final byte[] content) {
        int limit = Math.min(content.length, BINARY_DETECTION_BUFFER_SIZE);
        for (int i = 0; i < limit; i++) {
            if (content[i] == 0) {
                return true;
            }
        }
        return false;
    }
}