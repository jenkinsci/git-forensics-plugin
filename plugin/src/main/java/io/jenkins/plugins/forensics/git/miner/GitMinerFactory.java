package io.jenkins.plugins.forensics.git.miner;

import java.util.Optional;

import edu.hm.hafner.util.FilteredLog;

import hudson.Extension;
import hudson.FilePath;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.scm.SCM;

import io.jenkins.plugins.forensics.git.util.GitRepositoryValidator;
import io.jenkins.plugins.forensics.miner.MinerFactory;
import io.jenkins.plugins.forensics.miner.RepositoryMiner;
import io.jenkins.plugins.forensics.miner.BuildExtractor;

/**
 * A {@link MinerFactory} for Git. Handles Git repositories that do not have option ShallowClone set.
 *
 * @author Ullrich Hafner
 */
@Extension
public class GitMinerFactory extends MinerFactory {
    @Override
    public Optional<RepositoryMiner> createMiner(final SCM scm, final Run<?, ?> build, final FilePath workTree,
            final TaskListener listener, final FilteredLog logger) {
        GitRepositoryValidator validator = new GitRepositoryValidator(scm, build, workTree, listener, logger);
        if (validator.isGitRepository()) {
            logger.logInfo("-> Git miner successfully created in working tree '%s'", workTree);
            String latestCommitId = BuildExtractor.previousBuildStatistics(build).getLatestCommitId();
            return Optional.of(
                    new GitRepositoryMiner(validator.createClient(), latestCommitId));

        }
        logger.logInfo("-> Git miner could not be created for SCM '%s' in working tree '%s'", scm, workTree);
        return Optional.empty();
    }
}
