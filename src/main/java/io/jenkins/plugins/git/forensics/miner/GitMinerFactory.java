package io.jenkins.plugins.git.forensics.miner;

import java.util.Optional;

import hudson.Extension;
import hudson.FilePath;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.scm.SCM;

import io.jenkins.plugins.forensics.miner.MinerFactory;
import io.jenkins.plugins.forensics.miner.RepositoryMiner;
import io.jenkins.plugins.forensics.util.FilteredLog;
import io.jenkins.plugins.git.forensics.GitRepositoryValidator;

/**
 * A {@link MinerFactory} for Git. Handles Git repositories that do not have option ShallowClone set.
 *
 * @author Ullrich Hafner
 */
@Extension
public class GitMinerFactory extends MinerFactory {
    static final String INFO_MINER_CREATED = "Invoking GitMiner to creates statistics for all available repository files.";

    @Override
    public Optional<RepositoryMiner> createMiner(final SCM scm, final Run<?, ?> build, final FilePath workTree,
            final TaskListener listener, final FilteredLog logger) {
        GitRepositoryValidator validator = new GitRepositoryValidator(scm, build, workTree, listener, logger);
        if (validator.isGitRepository()) {
            logger.logInfo(INFO_MINER_CREATED);

            return Optional.of(new GitRepositoryMiner(validator.createClient()));
        }
        return Optional.empty();
    }
}
