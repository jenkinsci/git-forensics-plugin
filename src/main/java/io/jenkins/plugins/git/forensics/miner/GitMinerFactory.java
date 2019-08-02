package io.jenkins.plugins.git.forensics.miner;

import java.io.IOException;
import java.util.Optional;

import org.jenkinsci.plugins.gitclient.GitClient;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.extensions.impl.CloneOption;
import hudson.scm.SCM;

import io.jenkins.plugins.forensics.miner.MinerFactory;
import io.jenkins.plugins.forensics.miner.RepositoryMiner;
import io.jenkins.plugins.forensics.util.FilteredLog;

/**
 * A {@link MinerFactory} for Git. Handles Git repositories that do not have option ShallowClone set.
 *
 * @author Ullrich Hafner
 */
@Extension
public class GitMinerFactory extends MinerFactory {
    static final String INFO_BLAMER_CREATED = "Invoking GitMiner to creates statistics for all available repository files.";
    static final String INFO_SHALLOW_CLONE = "Skipping issues blame since Git has been configured with shallow clone";
    static final String ERROR_BLAMER = "Exception while creating a GitClient instance";

    @Override
    public Optional<RepositoryMiner> createMiner(final SCM scm, final Run<?, ?> build, final FilePath workspace,
            final TaskListener listener, final FilteredLog logger) {
        if (scm instanceof GitSCM) {
            return createMiner((GitSCM) scm, build, workspace, listener, logger);
        }
        logger.logInfo("Skipping miner since SCM '%s' is not of type GitSCM", scm.getType());
        return Optional.empty();
    }

    private Optional<RepositoryMiner> createMiner(final GitSCM git, final Run<?, ?> build,
            final FilePath workspace, final TaskListener listener, final FilteredLog logger) {
        if (isShallow(git)) {
            logger.logInfo(INFO_SHALLOW_CLONE);

            return Optional.empty();
        }

        try {
            EnvVars environment = build.getEnvironment(listener);
            GitClient gitClient = git.createClient(listener, environment, build, workspace);

            logger.logInfo(INFO_BLAMER_CREATED);

            return Optional.of(new GitRepositoryMiner(gitClient));
        }
        catch (IOException | InterruptedException e) {
            logger.logException(e, ERROR_BLAMER);

            return Optional.empty();
        }
    }

    private boolean isShallow(final GitSCM git) {
        CloneOption option = git.getExtensions().get(CloneOption.class);
        if (option != null) {
            return option.isShallow();
        }
        return false;
    }

}
