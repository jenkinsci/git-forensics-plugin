package io.jenkins.plugins.forensics.git.util;

import java.io.IOException;
import java.util.Collections;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryBuilder;
import org.junit.Before;
import org.junit.Rule;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import org.jenkinsci.plugins.gitclient.GitClient;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.model.Job;
import hudson.model.Run;
import hudson.model.TaskListener;
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
            sampleRepo.git("checkout", "-b", branch);
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
            GitSCM scm = new GitSCM(
                    GitSCM.createRepoList("file:///" + sampleRepo.getRoot(), null),
                    Collections.emptyList(), false, Collections.emptyList(),
                    null, null, Collections.emptyList());
            @SuppressWarnings("rawtypes")
            Run run = mock(Run.class);
            Job<?, ?> job = mock(Job.class);
            when(run.getParent()).thenReturn(job);

            return scm.createClient(TaskListener.NULL, new EnvVars(), run,
                    new FilePath(sampleRepo.getRoot()));
        }
        catch (IOException | InterruptedException exception) {
            throw new AssertionError(exception);
        }
    }
}