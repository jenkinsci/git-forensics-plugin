package io.jenkins.plugins.forensics.git.delta;

import edu.hm.hafner.util.FilteredLog;
import edu.hm.hafner.util.PathUtil;

import java.util.Optional;

import hudson.Extension;
import hudson.FilePath;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.scm.SCM;

import io.jenkins.plugins.forensics.delta.DeltaCalculator;
import io.jenkins.plugins.forensics.delta.DeltaCalculatorFactory;
import io.jenkins.plugins.forensics.git.util.GitRepositoryValidator;

/**
 * A {@link DeltaCalculatorFactory} for Git.
 *
 * @author Florian Orendi
 */
@Extension
public class GitDeltaCalculatorFactory extends DeltaCalculatorFactory {
    @Override
    public Optional<DeltaCalculator> createDeltaCalculator(final SCM scm, final Run<?, ?> run, final FilePath workspace,
            final TaskListener listener, final FilteredLog logger) {
        var validator = new GitRepositoryValidator(scm, run, workspace, listener, logger);
        if (validator.isGitRepository()) {
            var client = validator.createClient();
            logger.logInfo("-> Git delta calculator successfully created for SCM '%s' in working tree '%s'",
                    scm, new PathUtil().getAbsolutePath(client.getWorkTree().getRemote()));
            return Optional.of(new GitDeltaCalculator(client, scm.getKey()));
        }
        logger.logInfo("-> Git Delta Calculator could not be created for SCM '%s' in working tree '%s'", scm,
                workspace);
        return Optional.empty();
    }
}
