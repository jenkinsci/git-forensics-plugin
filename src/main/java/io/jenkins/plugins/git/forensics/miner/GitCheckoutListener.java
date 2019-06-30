package io.jenkins.plugins.git.forensics.miner;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;

import edu.umd.cs.findbugs.annotations.Nullable;

import org.jenkinsci.plugins.gitclient.GitClient;
import org.jenkinsci.plugins.gitclient.RepositoryCallback;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.SCMListener;
import hudson.plugins.git.GitSCM;
import hudson.remoting.VirtualChannel;
import hudson.scm.SCM;
import hudson.scm.SCMRevisionState;

import io.jenkins.plugins.forensics.miner.FileStatistics;
import io.jenkins.plugins.forensics.miner.RepositoryStatistics;

/**
 * Listens on Git checkout events to start mining of the repository.
 *
 * @author Ullrich Hafner
 */
@Extension
public class GitCheckoutListener extends SCMListener {
    @Override
    public void onCheckout(final Run<?, ?> build, final SCM scm, final FilePath workspace,
            final TaskListener listener, @Nullable final File changelogFile,
            @Nullable final SCMRevisionState pollingBaseline) throws Exception {

        if (!(scm instanceof GitSCM)) {
            return;
        }

        GitSCM gitSCM = (GitSCM) scm;
        EnvVars environment = build.getEnvironment(listener);
        GitClient gitClient = gitSCM.createClient(listener, environment, build, workspace);

        Instant start = Instant.now();
        RepositoryStatistics statistics = gitClient.withRepository(new GitCommitCall());
        Instant finish = Instant.now();
        int runtime = (int) (Duration.between(start, finish).toMillis() / 1000);
        log(listener, "[Git Forensics] Analyzed history of %d files in %d seconds", statistics.size(), runtime);
        if (statistics.isEmpty()) {
            return;
        }

        List<FileStatistics> sorted = new ArrayList<>(statistics.getFileStatistics());

        sorted.sort(Comparator.comparingInt(FileStatistics::getNumberOfCommits).reversed());
        log(listener, "[Git Forensics] File with most commits (#%d): %s",
                sorted.get(0).getNumberOfCommits(), sorted.get(0).getFileName());

        sorted.sort(Comparator.comparingInt(FileStatistics::getNumberOfAuthors).reversed());
        log(listener, "[Git Forensics] File with most number of authors (#%d): %s",
                sorted.get(0).getNumberOfAuthors(), sorted.get(0).getFileName());

        sorted.sort(Comparator.comparingLong(FileStatistics::getAgeInDays).reversed());
        log(listener, "[Git Forensics] Oldest file (%d days): %s",
                sorted.get(0).getAgeInDays(), sorted.get(0).getFileName());

        sorted.sort(Comparator.comparingLong(FileStatistics::getLastModifiedInDays));
        log(listener, "[Git Forensics] Least recently modified file (%d days): %s",
                sorted.get(0).getLastModifiedInDays(), sorted.get(0).getFileName());
    }

    private void log(final TaskListener listener, final String format, final Object... args) {
        listener.getLogger().println(String.format(format, args));
    }

    /**
     * Writes the Commits since last build into a GitCommit object.
     */
    static class GitCommitCall implements RepositoryCallback<RepositoryStatistics> {
        private static final long serialVersionUID = -3176195534620938744L;

        @Override
        public RepositoryStatistics invoke(final Repository repository, final VirtualChannel channel) throws IOException, InterruptedException {
            Set<String> files = new FilesCollector(repository).findAllFor(repository.resolve(Constants.HEAD));

            return new GitRepositoryMiner(repository).analyze(files);
        }
    }

}
