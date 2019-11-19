package io.jenkins.plugins.forensics.git.blame;

import java.util.Optional;

import hudson.Extension;
import hudson.FilePath;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.scm.SCM;

import io.jenkins.plugins.forensics.blame.Blamer;
import io.jenkins.plugins.forensics.blame.BlamerFactory;
import io.jenkins.plugins.forensics.git.util.GitRepositoryValidator;
import io.jenkins.plugins.forensics.util.FilteredLog;

/**
 * A {@link BlamerFactory} for Git. Handles Git repositories that do not have option ShallowClone set.
 *
 * @author Ullrich Hafner
 */
@Extension
public class GitBlamerFactory extends BlamerFactory {
    @Override
    public Optional<Blamer> createBlamer(final SCM scm, final Run<?, ?> build,
            final FilePath workTree, final TaskListener listener, final FilteredLog logger) {
        GitRepositoryValidator validator = new GitRepositoryValidator(scm, build, workTree, listener, logger);
        if (validator.isGitRepository()) {
            logger.logInfo("-> Git blamer successfully created in working tree '%s'", workTree);

            return Optional.of(new GitBlamer(validator.createClient(), validator.getHead()));
        }
        logger.logInfo("-> Git blamer could not be created for SCM '%s' in working tree '%s'", scm, workTree);
        return Optional.empty();
    }
}
