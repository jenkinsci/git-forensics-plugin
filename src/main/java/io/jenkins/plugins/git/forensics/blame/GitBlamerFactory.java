package io.jenkins.plugins.git.forensics.blame;

import java.util.Optional;

import hudson.Extension;
import hudson.FilePath;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.scm.SCM;

import io.jenkins.plugins.forensics.blame.Blamer;
import io.jenkins.plugins.forensics.blame.BlamerFactory;
import io.jenkins.plugins.forensics.util.FilteredLog;
import io.jenkins.plugins.git.forensics.GitRepositoryValidator;

/**
 * A {@link BlamerFactory} for Git. Handles Git repositories that do not have option ShallowClone set.
 *
 * @author Ullrich Hafner
 */
@Extension
public class GitBlamerFactory extends BlamerFactory {
    static final String INFO_BLAMER_CREATED = "Invoking GitBlamer to obtain SCM blame information for affected files";

    @Override
    public Optional<Blamer> createBlamer(final SCM scm, final Run<?, ?> build,
            final FilePath workTree, final TaskListener listener, final FilteredLog logger) {
        GitRepositoryValidator validator = new GitRepositoryValidator(scm, build, workTree, listener, logger);
        if (validator.isGitRepository()) {
            logger.logInfo(INFO_BLAMER_CREATED);

            return Optional.of(new GitBlamer(validator.createClient(), validator.getHead()));
        }
        return Optional.empty();
    }
}
