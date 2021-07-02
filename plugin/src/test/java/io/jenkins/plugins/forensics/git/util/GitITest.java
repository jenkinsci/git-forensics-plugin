package io.jenkins.plugins.forensics.git.util;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryBuilder;
import org.junit.Before;
import org.junit.Rule;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import org.jenkinsci.plugins.gitclient.GitClient;
import org.jenkinsci.plugins.gitclient.RepositoryCallback;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.model.Job;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.plugins.git.BranchSpec;
import hudson.plugins.git.GitSCM;
import jenkins.plugins.git.GitSampleRepoRule;

import io.jenkins.plugins.util.IntegrationTestWithJenkinsPerTest;

import static org.mockito.Mockito.*;

/**
 * Base class for Git integration tests. Provides a Git repository that will b e initialized for each test.
 *
 * @author Ullrich Hafner
 */
@SuppressWarnings({"IllegalCatch", "PMD.SignatureDeclareThrowsException"})
public class GitITest extends IntegrationTestWithJenkinsPerTest {
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

    /** Git repository in a temporary folder. */
    @Rule
    public GitSampleRepoRule sampleRepo = new GitSampleRepoRule();

    /**
     * Initializes the Git repository.
     *
     * @throws Exception
     *         if the initialization fails
     */
    @Before
    public void init() throws Exception {
        sampleRepo.init();
        checkoutNewBranch(INITIAL_BRANCH);
    }

    /**
     * Returns the object ID of the current HEAD commit.
     *
     * @return the head commit
     */
    protected ObjectId getHeadCommit() {
        try {
            return ObjectId.fromString(getHead());
        }
        catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    /**
     * Returns the commit ID of the current HEAD commit.
     *
     * @return the head commit (as String)
     */
    protected String getHead() {
        try {
            return sampleRepo.head();
        }
        catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    /**
     * Returns the sample repository.
     *
     * @return the sample repository
     */
    @SuppressFBWarnings("BC")
    protected Repository createRepository() {
        try {
            RepositoryBuilder repositoryBuilder = new RepositoryBuilder();
            return repositoryBuilder.setWorkTree(sampleRepo.getRoot()).setMustExist(true).build();
        }
        catch (IOException exception) {
            throw new AssertionError(exception);
        }
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
        try {
            sampleRepo.write(relativePath, content);
        }
        catch (IOException exception) {
            throw new AssertionError(exception);
        }
    }

    protected void checkout(final String branch) {
        try {
            sampleRepo.git("checkout", branch);
        }
        catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    protected void checkoutNewBranch(final String branch) {
        try {
            sampleRepo.git("switch", "-C", branch);
        }
        catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    protected void addFile(final String additionalSourceFile) {
        try {
            sampleRepo.git("add", additionalSourceFile);
        }
        catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    protected void commit(final String message) {
        try {
            sampleRepo.git("commit", "--all", "--message=" + message);
        }
        catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    /**
     * Writes a file to repository.
     *
     * @param commands
     *         git commands and parameters
     */
    protected void git(final String... commands) {
        try {
            sampleRepo.git(commands);
        }
        catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    /**
     * Change the content of the repository file using the name and email of author 1.
     *
     * @param content
     *         the new content of the file
     */
    protected void writeFileAsAuthorFoo(final String content) {
        writeFile(ADDITIONAL_FILE, content);
        git("add", ADDITIONAL_FILE);
        git("config", "user.name", FOO_NAME);
        git("config", "user.email", FOO_EMAIL);
        git("commit", "--message=Foo");
    }

    /**
     * Change the content of the repository file using the name and email of author 2.
     *
     * @param content
     *         the new content of the file
     */
    protected void writeFileAsAuthorBar(final String content) {
        writeFile(ADDITIONAL_FILE, content);
        git("add", ADDITIONAL_FILE);
        git("config", "user.name", BAR_NAME);
        git("config", "user.email", BAR_EMAIL);
        git("commit", "--message=Bar");
    }

    /**
     * Returns the absolute path of the working tree.
     *
     * @return absolute path to the working tree (normalized with Unix file separators).
     */
    protected String getRepositoryRoot() {
        return AbstractRepositoryCallback.getAbsolutePath(sampleRepo.getRoot());
    }

    /**
     * Creates a {@link GitClient} that uses the sample repository.
     *
     * @return a {@link GitClient}
     */
    protected GitClient createGitClient() {
        try {
            GitSCM scm = createGitScm("file:///" + sampleRepo.getRoot(), Collections.emptyList());
            Run<?, ?> run = mock(Run.class);
            Job<?, ?> job = mock(Job.class);
            when(run.getParent()).thenAnswer(i -> job);

            return scm.createClient(TaskListener.NULL, new EnvVars(), run,
                    new FilePath(sampleRepo.getRoot()));
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
            GitClient gitClient = createGitClient();
            gitClient.withRepository((RepositoryCallback<Void>) (repository, virtualChannel) -> {
                try (Git git = new Git(repository)) {
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
}
