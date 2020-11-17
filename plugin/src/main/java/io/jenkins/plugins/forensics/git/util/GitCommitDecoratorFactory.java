package io.jenkins.plugins.forensics.git.util;

import java.util.Optional;

import edu.hm.hafner.util.FilteredLog;

import hudson.Extension;
import hudson.plugins.git.browser.GitRepositoryBrowser;
import hudson.scm.RepositoryBrowser;
import hudson.scm.SCM;

import io.jenkins.plugins.forensics.util.CommitDecorator;
import io.jenkins.plugins.forensics.util.CommitDecoratorFactory;

/**
 * A {@link CommitDecoratorFactory} for Git.
 *
 * @author Ullrich Hafner
 */
@Extension
public class GitCommitDecoratorFactory extends CommitDecoratorFactory {
    @Override
    public Optional<CommitDecorator> createCommitDecorator(final SCM scm, final FilteredLog logger) {
        RepositoryBrowser<?> repositoryBrowser = scm.getEffectiveBrowser();
        if (repositoryBrowser instanceof GitRepositoryBrowser) {
            logger.logInfo("-> Git commit decorator successfully obtained '%s' to render commit links",
                    repositoryBrowser);

            return Optional.of(new GitCommitDecorator((GitRepositoryBrowser) repositoryBrowser));
        }
        logger.logInfo(
                "-> Git commit decorator could not be created for SCM '%s'", scm);
        return Optional.empty();
    }
}
