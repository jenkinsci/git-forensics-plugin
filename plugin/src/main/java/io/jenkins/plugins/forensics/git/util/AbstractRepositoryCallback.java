package io.jenkins.plugins.forensics.git.util;

import org.eclipse.jgit.dircache.InvalidPathException;
import org.eclipse.jgit.lib.Repository;

import java.io.File;
import java.io.IOException;
import java.io.Serial;
import java.nio.file.LinkOption;

import org.jenkinsci.plugins.gitclient.RepositoryCallback;

/**
 * Code that gets executed on the machine where a Git working directory is local and {@link
 * org.eclipse.jgit.lib.Repository} object is accessible.
 *
 * @param <T>
 *         the type of the return value
 *
 * @author Ullrich Hafner
 */
public abstract class AbstractRepositoryCallback<T> implements RepositoryCallback<T> {
    @Serial
    private static final long serialVersionUID = -5059963457602091209L;

    private static final String SLASH = "/";
    private static final String BACK_SLASH = "\\";

    /**
     * Returns the root directory of the repository working tree. This path is absolute and normalized using the UNIX
     * path separator.
     *
     * @param repository
     *         the repository
     *
     * @return the absolute path to the working tree
     */
    public static String getWorkTree(final Repository repository) {
        return getAbsolutePath(repository.getWorkTree());
    }

    /**
     * Returns an absolute path for a given file in the Git repository, normalized using Unix path separators.
     *
     * @param absolute
     *         absolute file name
     *
     * @return the absolute path to the working tree
     */
    public static String getAbsolutePath(final File absolute) {
        return makeUnixPath(resolve(absolute));
    }

    private static String resolve(final File absolute) {
        try {
            return absolute.toPath()
                    .toAbsolutePath()
                    .normalize()
                    .toRealPath(LinkOption.NOFOLLOW_LINKS)
                    .toString();
        }
        catch (IOException | InvalidPathException exception) {
            return absolute.toString();
        }
    }

    private static String makeUnixPath(final String fileName) {
        return fileName.replace(BACK_SLASH, SLASH);
    }
}
