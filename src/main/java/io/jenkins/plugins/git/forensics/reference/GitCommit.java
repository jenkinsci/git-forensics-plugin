package io.jenkins.plugins.git.forensics.reference;

import hudson.model.Run;
import jenkins.model.RunAction2;

import javax.annotation.CheckForNull;
import java.io.Serializable;

/**
 * Action, die Informationen zu den Reversions zusammenfügt und in GitCommitLogs schreibt
 *
 * @author Arne Schöntag
 */
public class GitCommit implements RunAction2, Serializable {

    private transient Run<?, ?> run;

    private final String id;
    private final String name;

    public GitCommit(final Run<?, ?> run, String id, String name) {
        this.run = run;
        this.id = id;
        this.name = name;
    }

    @Override
    public void onAttached(Run<?, ?> run) {
        this.run = run;
    }

    @Override
    public void onLoad(Run<?, ?> run) {
        onAttached(run);
    }

    @CheckForNull
    @Override
    public String getIconFileName() {
        return null;
    }

    @CheckForNull
    @Override
    public String getDisplayName() {
        return name;
    }

    @CheckForNull
    @Override
    public String getUrlName() {
        return null;
    }
}
