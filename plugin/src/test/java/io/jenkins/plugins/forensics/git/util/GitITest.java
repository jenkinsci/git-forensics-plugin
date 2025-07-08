package io.jenkins.plugins.forensics.git.util;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.jenkinsci.plugins.gitclient.GitClient;
import org.jenkinsci.plugins.gitclient.RepositoryCallback;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Job;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.plugins.git.BranchSpec;
import hudson.plugins.git.GitSCM;
import hudson.util.StreamTaskListener;

import io.jenkins.plugins.util.IntegrationTestWithJenkinsPerTest;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Base class for Git integration tests. Provides a Git repository that will b e initialized for each test.
 *
 * @author Ullrich Hafner
 */
@SuppressWarnings("IllegalCatch")
public abstract class GitITest extends IntegrationTestWithJenkinsPerTest {
    /** File name of a source file that will be modified by two authors. */
    protected static final String ADDITIONAL_FILE = "source.txt";

    /** Initial file in the Git repository. */
    protected static final String INITIAL_FILE = "file";

    /** Author 1 name. */
    protected static final String FOO_NAME = "Foo";
    /** Author 1 email. */
    protected static final String FOO_EMAIL = "foo@jenkins.io";
    /** Author 2 name. */
    protected static final String BAR_NAME = "Bar";
    /** Author 2 email. */
    protected static final String BAR_EMAIL = "bar@jenkins.io";

    /** Initial branch used in Git repository. */
    protected static final String INITIAL_BRANCH = "main";

    private GitRepository gitRepository;

    @BeforeAll
    static void allowLocalCheckouts() {
        GitSCM.ALLOW_LOCAL_CHECKOUT = true;
    }

    @BeforeEach
    void initializeGitRepository(@TempDir final File baseDirectory) {
        gitRepository = new GitRepository(baseDirectory);
        gitRepository.init();
        gitRepository.git("config", "--global", "core.longpaths", "true");
        checkoutNewBranch(INITIAL_BRANCH);
    }

    /**
     * Returns the object ID of the current HEAD commit.
     *
     * @return the head commit
     */
    protected ObjectId getHeadCommit() {
        return ObjectId.fromString(getHead());
    }

    /**
     * Returns the commit ID of the current HEAD commit.
     *
     * @return the head commit (as String)
     */
    protected String getHead() {
        return getGitRepository().head();
    }

    /**
     * Writes a file to repository.
     *
     * @param relativePath
     *         relative path ({@code /}-separated)
     * @param content
     *         the text content of the file
     */
    protected void writeFile(final String relativePath, final String content) {
        getGitRepository().write(relativePath, content);
    }

    protected void checkout(final String branch) {
        getGitRepository().git("checkout", branch);
    }

    protected void checkoutNewBranch(final String branch) {
        getGitRepository().git("switch", "-C", branch);
    }

    protected void addFile(final String additionalSourceFile) {
        getGitRepository().git("add", additionalSourceFile);
    }

    protected void commit(final String message) {
        getGitRepository().git("commit", "--all", "--message=" + message);
    }

    /**
     * Merges the passed branch into the current branch without fast-forwarding. This creates an additional merge
     * commit.
     *
     * @param branch
     *         The branch to be merged
     */
    protected void mergeWithoutFastForwarding(final String branch) {
        git("merge", "--no-ff", branch);
    }

    /**
     * Merges the passed branch into the current branch using fast-forwarding.
     *
     * @param branch
     *         The branch to be merged
     */
    protected void mergeWithFastForwarding(final String branch) {
        git("merge", "--ff", branch);
    }

    protected void git(final String... commands) {
        getGitRepository().git(commands);
    }

    /**
     * Change the content of the repository file using the name and email of author 1.
     *
     * @param content
     *         the new content of the file
     */
    protected void writeFileAsAuthorFoo(final String content) {
        commitFile(ADDITIONAL_FILE, content, FOO_NAME, FOO_EMAIL, "Foo");
    }

    /**
     * Change the content of the repository file using the name and email of author 2.
     *
     * @param content
     *         the new content of the file
     */
    protected void writeFileAsAuthorBar(final String content) {
        commitFile(ADDITIONAL_FILE, content, BAR_NAME, BAR_EMAIL, "Bar");
    }

    /**
     * Commits a file with a specific name and message and a random content.
     *
     * @param fileName
     *         The file name
     * @param message
     *         The message
     */
    protected void writeFileWithNameAsAuthorFoo(final String fileName, final String message) {
        commitFile(fileName, UUID.randomUUID().toString(), FOO_NAME, FOO_EMAIL, message);
    }

    /**
     * Commits a file.
     *
     * @param file
     *         The file name
     * @param content
     *         The file content
     * @param authorName
     *         The author name
     * @param authorEmail
     *         The author email
     * @param message
     *         the commit message
     */
    private void commitFile(final String file, final String content, final String authorName, final String authorEmail,
            final String message) {
        writeFile(file, content);
        git("add", file);
        git("config", "user.name", authorName);
        git("config", "user.email", authorEmail);
        git("commit", "--message=%s".formatted(message));
    }

    /**
     * Returns the absolute path of the working tree.
     *
     * @return absolute path to the working tree (normalized with Unix file separators).
     */
    protected String getRepositoryRoot() {
        return AbstractRepositoryCallback.getAbsolutePath(getGitRepository().getBaseDirectory());
    }

    /**
     * Creates a {@link GitClient} that uses the sample repository.
     *
     * @return a {@link GitClient}
     */
    protected GitClient createGitClient() {
        try {
            var scm = createGitScm("file:///" + getGitRepository().getBaseDirectory(), Collections.emptyList());
            Run<?, ?> run = mock(Run.class);
            Job<?, ?> job = mock(Job.class);
            when(run.getParent()).thenAnswer(i -> job);

            return scm.createClient(TaskListener.NULL, new EnvVars(), run,
                    new FilePath(getGitRepository().getBaseDirectory()));
        }
        catch (IOException | InterruptedException exception) {
            throw new AssertionError(exception);
        }
    }

    /**
     * Creates a new {@link GitSCM} instance.
     *
     * @param url
     *         URL of the repository (file or HTTP)
     * @param branches
     *         a list of branches
     *
     * @return the SCM
     */
    protected GitSCM createGitScm(final String url, final List<BranchSpec> branches) {
        return new GitSCM(GitSCM.createRepoList(url, null),
                branches, null, null, Collections.emptyList());
    }

    /**
     * Runs the specified lambda as a test case in the context of the preconfigured Git repository.
     *
     * @param testCase
     *         the test case to run
     */
    protected void runTest(final GitTestCase testCase) {
        try {
            var gitClient = createGitClient();
            gitClient.withRepository((RepositoryCallback<Void>) (repository, virtualChannel) -> {
                try (var git = new Git(repository)) {
                    testCase.run(repository, git);
                }
                catch (GitAPIException exception) {
                    // ignore
                }
                return null; // not needed
            });
        }
        catch (IOException | InterruptedException exception) {
            throw new AssertionError(exception);
        }
    }

    public GitRepository getGitRepository() {
        return gitRepository;
    }

    protected String getGitRepositoryPath() {
        return getGitRepository().getBaseDirectory().getAbsolutePath();
    }

    /**
     * A test case that runs in the preconfigured Git repository of this integration test.
     */
    @FunctionalInterface
    public interface GitTestCase {
        /**
         * Runs the test case.
         *
         * @param repository
         *         the repository
         * @param git
         *         Git API wrapper
         *
         * @throws GitAPIException
         *         in case of an error using the Git API
         * @throws IOException
         *         in case of an IO error
         */
        void run(Repository repository, Git git) throws GitAPIException, IOException;
    }

    /**
     * A Git repository for integration tests.
     */
    private static class GitRepository {
        private final File baseDirectory;

        GitRepository(final File baseDirectory) {
            this.baseDirectory = baseDirectory;
        }

        private void run(final String... cmds) {
            try {
                TaskListener listener = StreamTaskListener.fromStdout();

                int commandExitCode = new Launcher.LocalLauncher(listener).launch()
                        .cmds(cmds)
                        .pwd(baseDirectory)
                        .stdout(listener)
                        .join();

                assertThat(commandExitCode).as(Arrays.toString(cmds) + " failed with error code").isZero();
            }
            catch (IOException | InterruptedException exception) {
                throw new AssertionError(exception);
            }
        }

        void write(final String relativePath, final String content) {
            try {
                FileUtils.write(new File(this.baseDirectory, relativePath), content, StandardCharsets.UTF_8);
            }
            catch (IOException exception) {
                throw new AssertionError(exception);
            }
        }

        void git(final String... cmds) {
            List<String> args = new ArrayList<>();
            args.add("git");
            args.addAll(Arrays.asList(cmds));

            run(args.toArray(new String[0]));
        }

        @SuppressWarnings("checkstyle:IllegalCatch")
        void init() {
            try {
                run("git", "version");
                git("init", "--template=");
                write("file", "");
                git("add", "file");
                git("config", "user.name", "Git SampleRepoRule");
                git("config", "user.email", "gits@mplereporule");
                git("commit", "--message=init");
            }
            catch (Exception exception) {
                throw new AssertionError(exception);
            }
        }

        @SuppressFBWarnings("BC")
        String head() {
            try (var repository = new RepositoryBuilder().setWorkTree(baseDirectory).build()) {
                return repository
                        .resolve("HEAD")
                        .name();
            }
            catch (IOException exception) {
                throw new AssertionError(exception);
            }
        }

        File getBaseDirectory() {
            return this.baseDirectory;
        }
    }
}
