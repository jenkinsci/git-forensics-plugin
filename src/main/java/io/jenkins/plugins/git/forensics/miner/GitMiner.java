package io.jenkins.plugins.git.forensics.miner;

import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Paths;

import org.eclipse.jgit.lib.Repository;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import org.jenkinsci.plugins.gitclient.GitClient;
import org.jenkinsci.plugins.gitclient.RepositoryCallback;
import hudson.FilePath;
import hudson.plugins.git.GitException;
import hudson.remoting.VirtualChannel;

import io.jenkins.plugins.git.forensics.FilteredLog;
import io.jenkins.plugins.git.forensics.blame.Blames;

/**
 *
 */
@SuppressFBWarnings(value = "SE", justification = "GitClient implementation is Serializable")
class GitMiner implements Serializable {
    private static final long serialVersionUID = -619059996626444900L;

    private final GitClient git;
    private final FilePath workspace;

    /**
     * @param git
     *         git client
     */
    GitMiner(final GitClient git) {
        workspace = git.getWorkTree();
        this.git = git;
    }

    public Blames blame(final Blames request) {
        try {
            FilteredLog log = new FilteredLog("Git Miner errors:");
            return git.withRepository(new BlameCallback(log));
        }
        catch (IOException exception) {
            request.logException(exception, "Computing blame information failed with an exception:");
        }
        catch (GitException exception) {
            request.logException(exception, "Can't determine head commit using 'git rev-parse'. Skipping blame.");
        }
        catch (InterruptedException e) {
            // nothing to do, already logged
        }
        return new Blames();
    }

    private String getWorkspacePath() throws IOException {
        return Paths.get(workspace.getRemote()).toAbsolutePath().normalize().toRealPath().toString();
    }

    /**
     * Starts the blame commands.
     */
    static class BlameCallback implements RepositoryCallback<Blames> {
        private static final long serialVersionUID = 305428153127875489L;
        private final FilteredLog log;

        public BlameCallback(final FilteredLog log) {
            this.log = log;
        }

        @Override
        public Blames invoke(final Repository repo, final VirtualChannel channel) throws InterruptedException {
            return null;
        }
    }
}
