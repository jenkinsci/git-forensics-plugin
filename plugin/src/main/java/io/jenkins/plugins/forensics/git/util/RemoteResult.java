package io.jenkins.plugins.forensics.git.util;

import java.io.Serializable;

/**
 * A result from a remote call on an agent. Wraps the actual result so additional logging information can be returned as
 * well.
 *
 * @param <T>
 *         the serializable result of the remote call
 *
 * @author Ullrich Hafner
 */
public class RemoteResult<T extends Serializable> {
    public RemoteResult() {
    }
}
