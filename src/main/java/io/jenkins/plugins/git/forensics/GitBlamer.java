package io.jenkins.plugins.git.forensics;

import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Paths;

import org.eclipse.jgit.api.BlameCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.blame.BlameResult;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;

import edu.umd.cs.findbugs.annotations.Nullable;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import org.jenkinsci.plugins.gitclient.GitClient;
import org.jenkinsci.plugins.gitclient.RepositoryCallback;
import hudson.FilePath;
import hudson.plugins.git.GitException;
import hudson.remoting.VirtualChannel;

/**
 * Assigns git blames to warnings. Based on the solution by John Gibson, see JENKINS-6748. This code is intended to run
 * on the agent.
 *
 * @author Lukas Krose
 * @author Ullrich Hafner
 * @see <a href="http://issues.jenkins-ci.org/browse/JENKINS-6748">Issue 6748</a>
 */
@SuppressFBWarnings(value = "SE", justification = "GitClient implementation is Serializable")
class GitBlamer implements Serializable {
    private static final long serialVersionUID = -619059996626444900L;

    private final GitClient git;
    private final String gitCommit;
    private final FilePath workspace;

    /**
     * Creates a new blamer for Git.
     *
     * @param git
     *         git client
     * @param gitCommit
     *         content of environment variable GIT_COMMIT
     */
    GitBlamer(final GitClient git, final String gitCommit) {
        workspace = git.getWorkTree();
        this.git = git;
        this.gitCommit = gitCommit;
    }

    public Blames blame(final Blames request) {
        try {
            request.logInfo("Invoking Git blamer to create author and commit information for all affected files");
            request.logInfo("GIT_COMMIT env = '%s'", gitCommit);
            request.logInfo("Git working tree = '%s'", git.getWorkTree());

            ObjectId headCommit = git.revParse(gitCommit);
            if (headCommit == null) {
                request.logError("Could not retrieve HEAD commit, aborting");
                return new Blames();
            }
            request.logInfo("Git commit ID = '%s'", headCommit.getName());

            String workspacePath = getWorkspacePath();
            request.logInfo("Job workspace = '%s'", workspacePath);
            return git.withRepository(new BlameCallback(request, headCommit));
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
        private static final long serialVersionUID = 8794666938104738260L;

        private final ObjectId headCommit;
        private final Blames blames;

        BlameCallback(final Blames blames, final ObjectId headCommit) {
            this.blames = blames;
            this.headCommit = headCommit;
        }

        @Override
        public Blames invoke(final Repository repo, final VirtualChannel channel) throws InterruptedException {
            BlameRunner blameRunner = new BlameRunner(repo, headCommit);

            for (BlameRequest request : blames.getRequests()) {
                run(request, blameRunner);
                if (Thread.interrupted()) { // Cancel request by user
                    String message = "Thread was interrupted while computing blame information";
                    blames.logInfo(message);
                    throw new InterruptedException(message);
                }
            }

            blames.logInfo("-> blamed authors of issues in %d files", blames.size());

            return blames;
        }

        /**
         * Runs Git blame for one file.
         *
         * @param request
         *         the request that identifies the file
         * @param blameRunner
         *         the runner to invoke Git
         */
        // FIXME: @VisibleForTesting
        void run(final BlameRequest request, final BlameRunner blameRunner) {
            // FIXME: FilteredLog log = new FilteredLog(report, "Git blame errors:");
            String fileName = request.getFileName();
            try {
                BlameResult blame = blameRunner.run(fileName);
                if (blame == null) {
                    blames.logError("- no blame results for request <%s>.%n", request);
                }
                else {
                    for (int line : request) {
                        int lineIndex = line - 1; // first line is index 0
                        if (lineIndex < blame.getResultContents().size()) {
                            PersonIdent who = blame.getSourceAuthor(lineIndex);
                            if (who == null) {
                                blames.logError("- no author information found for line %d in file %s", lineIndex,
                                        fileName);
                            }
                            else {
                                request.setName(line, who.getName());
                                request.setEmail(line, who.getEmailAddress());
                            }
                            RevCommit commit = blame.getSourceCommit(lineIndex);
                            if (commit == null) {
                                blames.logError("- no commit ID found for line %d in file %s", lineIndex, fileName);
                            }
                            else {
                                request.setCommit(line, commit.getName());
                            }
                        }
                    }
                }
            }
            catch (GitAPIException | JGitInternalException exception) {
                blames.logException(exception, "- error running git blame on '%s' with revision '%s'", fileName,
                        headCommit);
            }
            blames.logSummary();
        }
    }

    /**
     * Executes the Git blame command.
     */
    static class BlameRunner {
        private final Repository repo;
        private final ObjectId headCommit;

        BlameRunner(final Repository repo, final ObjectId headCommit) {
            this.repo = repo;
            this.headCommit = headCommit;
        }

        @Nullable
        BlameResult run(final String fileName) throws GitAPIException {
            BlameCommand blame = new BlameCommand(repo);
            blame.setFilePath(fileName);
            blame.setStartCommit(headCommit);
            return blame.call();
        }
    }
}
