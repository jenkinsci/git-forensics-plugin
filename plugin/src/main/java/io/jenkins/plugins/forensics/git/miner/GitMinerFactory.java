package io.jenkins.plugins.forensics.git.miner;

import edu.hm.hafner.util.FilteredLog;

import java.util.Optional;

import hudson.Extension;
import hudson.FilePath;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.scm.SCM;

import io.jenkins.plugins.forensics.git.util.GitRepositoryValidator;
import io.jenkins.plugins.forensics.miner.MinerFactory;
import io.jenkins.plugins.forensics.miner.RepositoryMiner;

/**
 * A {@link MinerFactory} for Git. Handles Git repositories that do not have the option ShallowClone set.
 *
 * @author Ullrich Hafner
 */
@Extension
public class GitMinerFactory extends MinerFactory {
    @Override
    public Optional<RepositoryMiner> createMiner(final SCM scm, final Run<?, ?> build, final FilePath workTree,
            final TaskListener listener, final FilteredLog logger) {
        var validator = new GitRepositoryValidator(scm, build, workTree, listener, logger);
        if (validator.isGitRepository()) {
            logger.logInfo("-> Git miner successfully created in working tree '%s'", workTree);

            return Optional.of(new GitRepositoryMiner(validator.createClient()));
        }
        logger.logInfo("-> Git miner could not be created for SCM '%s' in working tree '%s'", scm, workTree);
        return Optional.empty();
    }
}
