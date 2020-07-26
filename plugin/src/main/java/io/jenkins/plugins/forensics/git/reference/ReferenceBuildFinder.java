package io.jenkins.plugins.forensics.git.reference;

import java.util.Optional;

import hudson.model.Run;

/**
 * Provides a reference build for a given build. TODO: add detailed description
 *
 * @author Ullrich Hafner
 */
public class ReferenceBuildFinder {
    /**
     * Tries to find a reference build.
     *
     * @param run
     *         the build to find the reference for
     *
     * @return the reference build, if found
     */
    public Optional<Run<?, ?>> find(final Run<?, ?> run) {
        GitReferenceBuild action = run.getAction(GitReferenceBuild.class);
        if (action == null) {
            // TODO: try to find a reference build for Multi Branch pipelines
            return Optional.empty();
        }
        return action.getReferenceBuild();
    }
}
