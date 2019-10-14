package io.jenkins.plugins.git.forensics.blame;

import java.io.IOException;
import java.nio.file.LinkOption;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.stream.StreamSupport;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.jgit.api.BlameCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.blame.BlameResult;
import org.eclipse.jgit.dircache.InvalidPathException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;

import com.google.common.annotations.VisibleForTesting;

import edu.umd.cs.findbugs.annotations.Nullable;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import org.jenkinsci.plugins.gitclient.GitClient;
import org.jenkinsci.plugins.gitclient.RepositoryCallback;
import hudson.FilePath;
import hudson.plugins.git.GitException;
import hudson.remoting.VirtualChannel;

import io.jenkins.plugins.forensics.blame.Blamer;
import io.jenkins.plugins.forensics.blame.Blames;
import io.jenkins.plugins.forensics.blame.FileBlame;
import io.jenkins.plugins.forensics.blame.FileLocations;

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
    private final FilePath workTree;

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

        workTree = git.getWorkTree();
        this.git = git;
        this.gitCommit = gitCommit;
    }

    @Override
    public Blames blame(final FileLocations locations) {
        Blames blames = new Blames();
        try {
            blames.logInfo("Invoking Git blamer to create author and commit information for %d affected files",
                    locations.size());
            blames.logInfo("GIT_COMMIT env = '%s'", gitCommit);

            ObjectId headCommit = git.revParse(gitCommit);
            if (headCommit == null) {
                blames.logError(NO_HEAD_ERROR);
                return blames;
            }
            blames.logInfo("Git commit ID = '%s'", headCommit.getName());

            String workTreeResolvedPath = getWorkTreePath();
            blames.logInfo("Git working tree = '%s'", workTreeResolvedPath);

            long nano = System.nanoTime();
            Blames filledBlames = git.withRepository(new BlameCallback(workTreeResolvedPath, locations, blames, headCommit));
            filledBlames.logInfo("Blaming of authors took %d seconds", 1 + (System.nanoTime() - nano) / 1_000_000_000L);
            return filledBlames;
        }
        catch (IOException exception) {
            blames.logException(exception, BLAME_ERROR);
        }
        catch (GitException exception) {
            blames.logException(exception, NO_HEAD_ERROR);
        }
        catch (InterruptedException e) {
            // nothing to do, already logged
        }
        return blames;
    }

    private String getWorkTreePath() {
        try {
            return Paths.get(workTree.getRemote())
                    .toAbsolutePath()
                    .normalize()
                    .toRealPath(LinkOption.NOFOLLOW_LINKS)
                    .toString();
        }
        catch (IOException | InvalidPathException exception) {
            return workTree.getRemote();
        }
    }

    /**
     * Starts the blame commands.
     */
    static class BlameCallback implements RepositoryCallback<Blames> {
        private static final long serialVersionUID = 8794666938104738260L;
        private static final int WHOLE_FILE = 0;
        private static final String SLASH = "/";

        private final ObjectId headCommit;
        private final String workTree;
        private final FileLocations locations;
        private final Blames blames;

        BlameCallback(final String workTree, final FileLocations locations, final Blames blames,
                final ObjectId headCommit) {
            this.workTree = workTree;
            this.locations = locations;
            this.blames = blames;
            this.headCommit = headCommit;
        }

        @Override
        public Blames invoke(final Repository repository, final VirtualChannel channel) throws InterruptedException {
            try {
                BlameRunner blameRunner = new BlameRunner(repository, headCommit);

                for (String file : locations.getFiles()) {
                    run(file, blameRunner, new LastCommitRunner(repository));

                    if (Thread.interrupted()) { // Cancel request by user
                        String message = "Blaming has been interrupted while computing blame information";
                        blames.logInfo(message);

                        throw new InterruptedException(message);
                    }
                }

                blames.logInfo("-> blamed authors of issues in %d files", blames.size());

                return blames;
            }
            finally {
                repository.close();
            }
        }

       /**
         * Runs Git blame for one file.
         *
         * @param fileName
         *         the file to get the blames for
         * @param blameRunner
         *         the runner to invoke Git blame
         * @param lastCommitRunner
         *         the runner to find the last commit
         */
        @VisibleForTesting
        void run(final String fileName, final BlameRunner blameRunner, final LastCommitRunner lastCommitRunner) {
            if (fileName.startsWith(workTree)) {
                try {
                    BlameResult blame = blameRunner.run(relative(fileName));
                    if (blame == null) {
                        blames.logError("- no blame results for file '%s'", fileName);
                    }
                    else {
                        for (int line : locations.getLines(fileName)) {
                            FileBlame fileBlame = new FileBlame(fileName);
                            if (line <= 0) {
                                fillWithLastCommit(fileName, fileBlame, lastCommitRunner);
                            }
                            else if (line <= blame.getResultContents().size()) {
                                fillWithBlameResult(fileName, fileBlame, blame, line);
                            }
                            blames.add(fileBlame);
                        }
                    }
                }
                catch (GitAPIException | JGitInternalException exception) {
                    blames.logException(exception, "- error running git blame on '%s' with revision '%s'",
                            fileName, headCommit);
                }
                blames.logSummary();
            }
            else {
                blames.logError("- skipping file '%s' (outside of work tree)", fileName);
            }
        }

        private String relative(final String fileName) {
            return fileName.replaceFirst(workTree + SLASH, StringUtils.EMPTY);
        }

        private void fillWithBlameResult(final String fileName, final FileBlame fileBlame, final BlameResult blame,
                final int line) {
            int lineIndex = line - 1; // first line is index 0
            PersonIdent who = blame.getSourceAuthor(lineIndex);
            if (who == null) {
                who = blame.getSourceCommitter(lineIndex);
            }
            if (who == null) {
                blames.logError("- no author or committer information found for line %d in file %s",
                        lineIndex, fileName);
            }
            else {
                fileBlame.setName(line, who.getName());
                fileBlame.setEmail(line, who.getEmailAddress());
            }
            RevCommit commit = blame.getSourceCommit(lineIndex);
            if (commit == null) {
                blames.logError("- no commit ID found for line %d in file %s", lineIndex, fileName);
            }
            else {
                fileBlame.setCommit(line, commit.getName());
            }
        }

        private void fillWithLastCommit(final String fileName, final FileBlame fileBlame,
                final LastCommitRunner lastCommitRunner) throws GitAPIException {
            Optional<RevCommit> commit = lastCommitRunner.run(relative(fileName));
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

        @Nullable
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
