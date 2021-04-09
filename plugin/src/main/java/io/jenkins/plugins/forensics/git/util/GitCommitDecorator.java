package io.jenkins.plugins.forensics.git.util;

import java.io.IOException;
import java.net.URL;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;

import hudson.plugins.git.browser.GitRepositoryBrowser;
import hudson.scm.RepositoryBrowser;

import static j2html.TagCreator.*;

/**
 * A {@link RepositoryBrowser} for Git commits. Since a {@link RepositoryBrowser} has no API to generate links to simple
 * commits, this decorator adds such a functionality for Git. Basically, this implementation delegates to the {@link
 * GitRepositoryBrowser} implementation, if available. Otherwise a plain link will be rendered using a short
 * representation, see {@link #asText(String)}.
 *
 * @author Ullrich Hafner
 */
public class GitCommitDecorator extends GitCommitTextDecorator {
    private final GitRepositoryBrowser browser;

    GitCommitDecorator(final GitRepositoryBrowser browser) {
        super();

        this.browser = browser;
    }

    @Override
    public String asLink(final String id) {
        if (StringUtils.isNotBlank(id)) {
            return createLink(id).orElse(asText(id));
        }
        return id;
    }

    private Optional<String> createLink(final String id) {
        try {
            URL link = browser.getChangeSetLink(id);
            if (link != null) {
                return Optional.of(a().withText(asText(id)).withHref(link.toString()).render());
            }
        }
        catch (IOException exception) {
            // ignore and return nothing
        }
        return Optional.empty();
    }
}
