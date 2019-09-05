package io.jenkins.plugins.git.forensics.reference;

import hudson.model.Run;
import io.jenkins.plugins.forensics.reference.VCSCommit;

import java.util.List;

/**
 * Action, which writes the information of the reversions into GitCommitLogs.
 *
 * @author Arne Schöntag
 */
@SuppressWarnings("unused")
public class GitCommit implements VCSCommit {

    private static final long serialVersionUID = 8994811233847179343L;
    private transient Run<?, ?> run;

    private static final String NAME = "GitCommit";

    private final GitCommitLog gitCommitLog;

    public GitCommit(final Run<?, ?> run) {
        this.run = run;
        gitCommitLog = new GitCommitLog();
    }

    public void addGitCommitLogs(final List<String> revisions) {
        gitCommitLog.getReversions().addAll(revisions);
    }

    public GitCommitLog getGitCommitLog() {
        return gitCommitLog;
    }

    public String getSummary() {
        return gitCommitLog.getReversions().toString();
    }

    @Override
    public void onAttached(final Run<?, ?> run) {
        this.run = run;
    }

    @Override
    public void onLoad(final Run<?, ?> run) {
        onAttached(run);
    }

    @Override
    public String getIconFileName() {
        return null;
    }

    @Override
    public String getDisplayName() {
        return NAME;
    }

    @Override
    public String getUrlName() {
        return null;
    }
}
