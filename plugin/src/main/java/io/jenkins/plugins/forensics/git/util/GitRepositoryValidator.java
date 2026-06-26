package io.jenkins.plugins.forensics.git.util;

import edu.hm.hafner.util.FilteredLog;
import edu.hm.hafner.util.VisibleForTesting;

import java.io.IOException;

import org.jenkinsci.plugins.gitclient.GitClient;
import hudson.FilePath;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.plugins.git.GitException;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.extensions.impl.CloneOption;
import hudson.scm.SCM;

/**
 * Inspects a given working tree and determines if this path is a valid Git repository that can be used to run one of
 * the forensics analyzers.
 *
 * @author Ullrich Hafner
 */
public class GitRepositoryValidator {
    /** Info message when a shallow clone is detected and blame/mining is skipped. */
    @VisibleForTesting
    public static final String INFO_SHALLOW_CLONE = "Skipping issues blame since Git has been configured with shallow clone";

    /** Info message when a shallow clone is detected but commit recording is still performed. */
    @VisibleForTesting
    public static final String INFO_SHALLOW_CLONE_COMMIT_RECORDING = "Git has been configured with shallow clone - commit recording will be limited to the available commits";

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
     * Returns whether the specified working tree contains a valid Git repository. Shallow clones are accepted
     * for operations that do not require full history (e.g., commit recording).
     *
     * @return {@code true} if the working tree contains a valid repository (including shallow clones),
     *         {@code false} otherwise
     */
    public boolean isGitRepository() {
        if (scm instanceof GitSCM) {
            return isValidGitRoot((GitSCM) scm, false);
        }
        logger.logInfo("SCM '%s' is not of type GitSCM", scm.getType());
        return false;
    }

    /**
     * Returns whether the specified working tree contains a valid Git repository with full history (no shallow
     * clone). This is required for operations that need full commit history, such as blame analysis and
     * repository mining.
     *
     * @return {@code true} if the working tree contains a valid non-shallow repository, {@code false} otherwise
     */
    public boolean isFullGitRepository() {
        if (scm instanceof GitSCM) {
            return isValidGitRoot((GitSCM) scm, true);
        }
        logger.logInfo("SCM '%s' is not of type GitSCM", scm.getType());
        return false;
    }

    /**
     * Returns whether the Git repository is configured as a shallow clone.
     *
     * @return {@code true} if the repository is a shallow clone, {@code false} otherwise
     */
    public boolean isShallowClone() {
        if (scm instanceof GitSCM) {
            return isShallow((GitSCM) scm);
        }
        return false;
    }

    private boolean isValidGitRoot(final GitSCM git, final boolean rejectShallowClone) {
        if (isShallow(git)) {
            if (rejectShallowClone) {
                logger.logInfo(INFO_SHALLOW_CLONE);
                return false;
            }
            logger.logInfo(INFO_SHALLOW_CLONE_COMMIT_RECORDING);
        }

        try {
            var gitClient = createClient();
            if (gitClient.revParse(getHead()) != null) {
                return true;
            }
        }
        catch (InterruptedException | GitException e) {
            // ignore and skip the working tree
        }

        logger.logInfo("Exception while creating a GitClient instance for work tree '%s'", workTree);
        return false;
    }

    private boolean isShallow(final GitSCM git) {
        var option = git.getExtensions().get(CloneOption.class);

        return option != null && option.isShallow();
    }

    /**
     * Creates a {@link GitClient} using the field values.
     *
     * @return a {@link GitClient}
     */
    public GitClient createClient() {
        try {
            var environment = build.getEnvironment(listener);
            return ((GitSCM) scm).createClient(listener, environment, build, workTree);
        }
        catch (IOException | InterruptedException e) {
            throw new GitException(e);
        }
    }

    /**
     * Returns the GIT_COMMIT environment variable, or 'HEAD' if not set.
     *
     * @return a {@link GitClient}
     */
    public String getHead() {
        try {
            var environment = build.getEnvironment(listener);
            return environment.getOrDefault("GIT_COMMIT", HEAD);
        }
        catch (IOException | InterruptedException e) {
            // ignore
        }
        return HEAD;
    }

    /**
     * Returns the key for the associated SCM.
     *
     * @return the SCM key
     */
    public String getId() {
        return scm.getKey();
    }

    /**
     * Returns the associated SCM.
     *
     * @return the SCM
     */
    public SCM getScm() {
        return scm;
    }
}
