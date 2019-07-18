package io.jenkins.plugins.git.forensics.blame;

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

import io.jenkins.plugins.forensics.blame.Blamer;
import io.jenkins.plugins.forensics.blame.BlamerFactory;
import io.jenkins.plugins.forensics.util.FilteredLog;

/**
 * A {@link BlamerFactory} gor Git. Handles Git repositories that do not have option ShallowClone set.
 *
 * @author Ullrich Hafner
 */
@Extension
public class GitBlamerFactory extends BlamerFactory {
    @Override
    public Optional<Blamer> createBlamer(final SCM scm, final Run<?, ?> build,
            final FilePath workspace, final TaskListener listener, final FilteredLog logger) {
        try {
            GitSCM gitSCM = asGit(scm);
            if (isShallow(gitSCM)) {
                logger.logError("Skipping issues blame since Git has been configured with shallow clone");

                return Optional.empty();
            }

            EnvVars environment = build.getEnvironment(listener);
            GitClient gitClient = gitSCM.createClient(listener, environment, build, workspace);
            String gitCommit = environment.getOrDefault("GIT_COMMIT", "HEAD");

            return Optional.of(new GitBlamer(gitClient, gitCommit));
        }
        catch (IOException | InterruptedException e) {
            logger.logException(e, "Exception while creating a GitClient instance");
        }
        return Optional.empty();
    }

    private boolean isShallow(final GitSCM git) {
        CloneOption option = git.getExtensions().get(CloneOption.class);
        if (option != null) {
            return option.isShallow();
        }
        return false;
    }

    private GitSCM asGit(final SCM scm) {
        return (GitSCM) scm;
    }
}
