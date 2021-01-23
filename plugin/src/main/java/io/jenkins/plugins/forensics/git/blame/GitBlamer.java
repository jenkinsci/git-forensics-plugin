package io.jenkins.plugins.forensics.git.blame;

import java.io.IOException;
import java.util.Optional;
import java.util.stream.StreamSupport;

import org.eclipse.jgit.api.BlameCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.blame.BlameResult;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;

import edu.hm.hafner.util.FilteredLog;
import edu.hm.hafner.util.VisibleForTesting;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import org.jenkinsci.plugins.gitclient.GitClient;
import hudson.plugins.git.GitException;
import hudson.remoting.VirtualChannel;

import io.jenkins.plugins.forensics.blame.Blamer;
import io.jenkins.plugins.forensics.blame.Blames;
import io.jenkins.plugins.forensics.blame.FileBlame;
import io.jenkins.plugins.forensics.blame.FileBlame.FileBlameBuilder;
import io.jenkins.plugins.forensics.blame.FileLocations;
import io.jenkins.plugins.forensics.git.util.AbstractRepositoryCallback;
import io.jenkins.plugins.forensics.git.util.RemoteResultWrapper;

/**
 * Assigns git blames to warnings. Based on the solution by John Gibson, see JENKINS-6748. This code is intended to run
 * on the agent.
 *
 * @author Lukas Krose
 * @author Ullrich Hafner
 * @see <a href="http://issues.jenkins-ci.org/browse/JENKINS-6748">Issue 6748</a>
 */
// TODO: Whom should we blame if the whole file is marked? Or if a range is marked and multiple authors are in the range
// TODO: Links in commits?
// TODO: Check if we should also create new Jenkins users
// TODO: Blame needs only run for new warnings
@SuppressFBWarnings(value = "SE", justification = "GitClient implementation is Serializable")
class GitBlamer extends Blamer {
    private static final long serialVersionUID = -619059996626444900L;

    static final String NO_HEAD_ERROR = "Could not retrieve HEAD commit, aborting";
    static final String BLAME_ERROR = "Computing blame information failed with an exception:";

    private final GitClient git;
    private final String gitCommit;

    /**
     * Creates a new blamer for Git.
     *
     * @param git
     *         git client
     * @param gitCommit
     *         content of environment variable GIT_COMMIT
     */
    GitBlamer(final GitClient git, final String gitCommit) {
        super();

        this.git = git;
        this.gitCommit = gitCommit;
    }

    @Override
    public Blames blame(final FileLocations locations, final FilteredLog log) {
        Blames blames = new Blames();
        try {
            log.logInfo("Invoking Git blamer to create author and commit information for %d affected files",
                    locations.size());
            log.logInfo("-> GIT_COMMIT env = '%s'", gitCommit);

            ObjectId headCommit = git.revParse(gitCommit);
            if (headCommit == null) {
                log.logError(NO_HEAD_ERROR);
                return blames;
            }

            long nano = System.nanoTime();

            RemoteResultWrapper<Blames> wrapped = git.withRepository(new BlameCallback(locations, blames, headCommit));
            wrapped.getInfoMessages().forEach(log::logInfo);

            log.logInfo("Blaming of authors took %d seconds", 1 + (System.nanoTime() - nano) / 1_000_000_000L);
            return wrapped.getResult();
        }
        catch (IOException exception) {
            log.logException(exception, BLAME_ERROR);
        }
        catch (GitException exception) {
            log.logException(exception, NO_HEAD_ERROR);
        }
        catch (InterruptedException e) {
            // nothing to do, already logged
        }
        return blames;
    }

    /**
     * Starts the blame commands.
     */
    static class BlameCallback extends AbstractRepositoryCallback<RemoteResultWrapper<Blames>> {
        private static final long serialVersionUID = 8794666938104738260L;
        private static final int WHOLE_FILE = 0;

        private final ObjectId headCommit;
        private final FileLocations locations;
        private final Blames blames;

        BlameCallback(final FileLocations locations, final Blames blames, final ObjectId headCommit) {
            super();

            this.locations = locations;
            this.blames = blames;
            this.headCommit = headCommit;
        }

        @Override
        public RemoteResultWrapper<Blames> invoke(final Repository repository, final VirtualChannel channel)
                throws InterruptedException {
            try {
                RemoteResultWrapper<Blames> log = new RemoteResultWrapper<>(blames, "Errors while running Git blame:");
                log.logInfo("-> Git commit ID = '%s'", headCommit.getName());
                log.logInfo("-> Git working tree = '%s'", getWorkTree(repository));

                BlameRunner blameRunner = new BlameRunner(repository, headCommit);
                LastCommitRunner lastCommitRunner = new LastCommitRunner(repository);

                FileBlameBuilder builder = new FileBlameBuilder();
                for (String file : locations.getFiles()) {
                    run(builder, file, blameRunner, lastCommitRunner, log);

                    if (Thread.interrupted()) { // Cancel request by user
                        String message = "Blaming has been interrupted while computing blame information";
                        log.logInfo(message);

                        throw new InterruptedException(message);
                    }
                }

                log.logInfo("-> blamed authors of issues in %d files", blames.size());

                return log;
            }
            finally {
                repository.close();
            }
        }

        /**
         * Runs Git blame for one file.
         *
         * @param builder
         *         the builder to use to create {@link FileBlame} instances
         * @param relativePath
         *         the file to get the blames for (relative path)
         * @param blameRunner
         *         the runner to invoke Git blame
         * @param lastCommitRunner
         *         the runner to find the last commit
         * @param log
         *         the log
         */
        @VisibleForTesting
        void run(final FileBlameBuilder builder, final String relativePath, final BlameRunner blameRunner,
                final LastCommitRunner lastCommitRunner, final FilteredLog log) {
            try {
                BlameResult blame = blameRunner.run(relativePath);
                if (blame == null) {
                    log.logError("- no blame results for file '%s'", relativePath);
                }
                else {
                    for (int line : locations.getLines(relativePath)) {
                        FileBlame fileBlame = builder.build(relativePath);
                        if (line <= 0) {
                            fillWithLastCommit(relativePath, fileBlame, lastCommitRunner);
                        }
                        else if (line <= blame.getResultContents().size()) {
                            fillWithBlameResult(relativePath, fileBlame, blame, line, log);
                        }
                        blames.add(fileBlame);
                    }
                }
            }
            catch (GitAPIException | JGitInternalException exception) {
                log.logException(exception, "- error running git blame on '%s' with revision '%s'",
                        relativePath, headCommit);
            }
            log.logSummary();
        }

        private void fillWithBlameResult(final String fileName, final FileBlame fileBlame, final BlameResult blame,
                final int line, final FilteredLog log) {
            int lineIndex = line - 1; // first line is index 0
            PersonIdent who = blame.getSourceAuthor(lineIndex);
            if (who == null) {
                who = blame.getSourceCommitter(lineIndex);
            }
            if (who == null) {
                log.logError("- no author or committer information found for line %d in file %s",
                        lineIndex, fileName);
            }
            else {
                fileBlame.setName(line, who.getName());
                fileBlame.setEmail(line, who.getEmailAddress());
            }
            RevCommit commit = blame.getSourceCommit(lineIndex);
            if (commit == null) {
                log.logError("- no commit ID and time found for line %d in file %s", lineIndex, fileName);
            }
            else {
                fileBlame.setCommit(line, commit.getName());
                fileBlame.setTime(line, commit.getCommitTime());
            }
        }

        private void fillWithLastCommit(final String relativePath, final FileBlame fileBlame,
                final LastCommitRunner lastCommitRunner) throws GitAPIException {
            Optional<RevCommit> commit = lastCommitRunner.run(relativePath);
            if (commit.isPresent()) {
                RevCommit revCommit = commit.get();
                fileBlame.setCommit(WHOLE_FILE, revCommit.getName());
                PersonIdent who = revCommit.getAuthorIdent();
                if (who == null) {
                    who = revCommit.getCommitterIdent();
                }
                if (who != null) {
                    fileBlame.setName(WHOLE_FILE, who.getName());
                    fileBlame.setEmail(WHOLE_FILE, who.getEmailAddress());
                }
            }
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

        @CheckForNull
        BlameResult run(final String fileName) throws GitAPIException {
            BlameCommand blame = new BlameCommand(repo);
            blame.setFilePath(fileName);
            blame.setStartCommit(headCommit);
            return blame.call();
        }
    }

    /**
     * Executes the Git log command for a given file.
     */
    static class LastCommitRunner {
        private final Repository repo;

        LastCommitRunner(final Repository repo) {
            this.repo = repo;
        }

        Optional<RevCommit> run(final String fileName) throws GitAPIException {
            try (Git git = new Git(repo)) {
                Iterable<RevCommit> commits = git.log().addPath(fileName).call();

                return StreamSupport.stream(commits.spliterator(), false).findFirst();
            }
        }
    }
}
