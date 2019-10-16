package io.jenkins.plugins.git.forensics;

import java.io.IOException;

import com.google.common.annotations.VisibleForTesting;

import org.jenkinsci.plugins.gitclient.GitClient;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.plugins.git.GitException;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.extensions.impl.CloneOption;
import hudson.scm.SCM;

import io.jenkins.plugins.forensics.util.FilteredLog;

/**
 * Inspects a given working tree and determines if this path is a valid Git repository that can be used to run one of
 * the forensics analyzers.
 *
 * @author Ullrich Hafner
 */
public class GitRepositoryValidator {
    /** Error message. */
    @VisibleForTesting
    public static final String INFO_SHALLOW_CLONE = "Skipping issues blame since Git has been configured with shallow clone";

    private static final String HEAD = "HEAD";

    private final SCM scm;
    private final Run<?, ?> build;
    private final FilePath workTree;
    private final TaskListener listener;
    private final FilteredLog logger;

    /**
     * Creates a new {@link GitRepositoryValidator}.
     *
     * @param scm
     *         the {@link SCM} to create the blamer for
     * @param build
     *         the current build
     * @param workTree
     *         the working tree to inspect
     * @param listener
     *         a task listener
     * @param logger
     *         a logger to report error messages
     */
    public GitRepositoryValidator(final SCM scm, final Run<?, ?> build,
            final FilePath workTree, final TaskListener listener, final FilteredLog logger) {
        this.scm = scm;
        this.build = build;
        this.workTree = workTree;
        this.listener = listener;
        this.logger = logger;
    }

    /**
     * Returns whether the specified working tree contains a valid Git repository that can be used to run one of the
     * forensics analyzers.
     *
     * @return {@code true} if the working tree contains a valid repository, {@code false} otherwise
     */
    public boolean isGitRepository() {
        if (scm instanceof GitSCM) {
            return createGitBlamer((GitSCM) scm);
        }
        logger.logInfo("SCM '%s' is not of type GitSCM", scm.getType());
        return false;
    }

    private boolean createGitBlamer(final GitSCM git) {
        if (isShallow(git)) {
            logger.logInfo(INFO_SHALLOW_CLONE);

            return false;
        }

        try {
            GitClient gitClient = createClient();
            if (gitClient.revParse(getHead()) != null) {
                return true;
            }
        }
        catch (InterruptedException | GitException e) {
            // ignore and skip working tree
        }

        logger.logInfo("Exception while creating a GitClient instance for work tree '%s'", workTree);
        return false;
    }

    private boolean isShallow(final GitSCM git) {
        CloneOption option = git.getExtensions().get(CloneOption.class);
        if (option != null) {
            return option.isShallow();
        }
        return false;
    }

    public GitClient createClient() {
        try {
            EnvVars environment = build.getEnvironment(listener);
            return ((GitSCM)scm).createClient(listener, environment, build, workTree);
        }
        catch (IOException | InterruptedException e) {
            throw new GitException(e);
        }

    }

    public String getHead() {
        try {
            EnvVars environment = build.getEnvironment(listener);
            return environment.getOrDefault("GIT_COMMIT", HEAD);
        }
        catch (IOException | InterruptedException e) {
            // ignore
        }
        return HEAD;
    }
}
