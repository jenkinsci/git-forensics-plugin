package io.jenkins.plugins.git.forensics.reference;

import hudson.model.Run;
import io.jenkins.plugins.forensics.reference.VCSCommit;

import javax.annotation.CheckForNull;
import java.util.List;

/**
 * Action, die Informationen zu den Reversions zusammenfügt und in GitCommitLogs schreibt
 *
 * @author Arne Schöntag
 */
public class GitCommit implements VCSCommit {

    private static final long serialVersionUID = 8994811233847179343L;
    private transient Run<?, ?> run;

//    private final String id;
    private final String NAME = "GitCommit";

    private final GitCommitLog gitCommitLog;

    public GitCommit(final Run<?, ?> run) {
        this.run = run;
//        this.id = id;
//        this.name = name;
        gitCommitLog = new GitCommitLog();
    }

    public void addGitCommitLogs(List<String> revisions) {
        gitCommitLog.getReversions().addAll(revisions);
    }

    public GitCommitLog getGitCommitLog() {
        return gitCommitLog;
    }

    public String getSummary(){
        return gitCommitLog.getReversions().toString();
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
        return NAME;
    }

    @CheckForNull
    @Override
    public String getUrlName() {
        return null;
    }
}
