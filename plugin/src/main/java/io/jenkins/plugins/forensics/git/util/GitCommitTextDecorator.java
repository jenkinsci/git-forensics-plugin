package io.jenkins.plugins.forensics.git.util;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.jgit.lib.ObjectId;

import io.jenkins.plugins.forensics.util.CommitDecorator;

/**
 * Renders a short version of the specified commit ID.
 *
 * @author Ullrich Hafner
 */
public class GitCommitTextDecorator extends CommitDecorator {
    @Override
    public String asLink(final String id) {
        return asText(id);
    }

    /**
     * Obtains a link for the specified commit ID.
     *
     * @param id
     *         the ID of the commit
     *
     * @return an HTML a tag that contains a link to the commit
     */
    public String asLink(final ObjectId id) {
        return asLink(id.name());
    }

    @Override
    public String asText(final String id) {
        return StringUtils.substring(id, 0, 7);
    }

    /**
     * Renders the commit ID as a human-readable text.
     *
     * @param id
     *         the ID of the commit
     *
     * @return a commit ID as human-readable text
     */
    public String asText(final ObjectId id) {
        return asText(id.name());
    }
}
