package io.jenkins.plugins.forensics.git.util;

import org.apache.commons.lang3.StringUtils;

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

    @Override
    public String asText(final String id) {
        return StringUtils.substring(id, 0, 7);
    }
}
