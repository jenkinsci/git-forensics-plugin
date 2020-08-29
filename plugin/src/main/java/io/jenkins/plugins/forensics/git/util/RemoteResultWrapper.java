package io.jenkins.plugins.forensics.git.util;

import java.io.Serializable;

import edu.hm.hafner.util.FilteredLog;

/**
 * A serializable result combined with a logger. Enables remote calls to return a result and a corresponding log.
 *
 * @param <T>
 *         the type of the result
 *
 * @author Ullrich Hafner
 */
public class RemoteResultWrapper<T extends Serializable> extends FilteredLog {
    private static final long serialVersionUID = -6411417555105688927L;

    private final T result;

    /**
     * Creates a new instance of {@link RemoteResultWrapper}.
     *
     * @param title
     *         the title of the error messages
     * @param result
     *         the wrapped result
     */
    public RemoteResultWrapper(final T result, final String title) {
        super(title);

        this.result = result;
    }

    public T getResult() {
        return result;
    }
}
