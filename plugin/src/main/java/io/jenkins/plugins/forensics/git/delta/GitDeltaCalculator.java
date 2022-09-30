package io.jenkins.plugins.forensics.git.delta;

import java.io.IOException;
import java.util.Optional;

import edu.hm.hafner.util.FilteredLog;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import org.jenkinsci.plugins.gitclient.GitClient;
import hudson.model.Run;

import io.jenkins.plugins.forensics.delta.DeltaCalculator;
import io.jenkins.plugins.forensics.delta.model.Delta;
import io.jenkins.plugins.forensics.git.reference.GitCommitsRecord;
import io.jenkins.plugins.forensics.git.util.RemoteResultWrapper;

/**
 * A {@link DeltaCalculator} for Git.
 *
 * @author Florian Orendi
 */
@SuppressFBWarnings(value = "SE", justification = "GitClient implementation is Serializable")
public class GitDeltaCalculator extends DeltaCalculator {

    static final String DELTA_ERROR = "Computing delta information failed with an exception:";
    static final String EMPTY_COMMIT_ERROR = "Calculating the Git code delta is not possible due to an unknown commit ID";

    private static final long serialVersionUID = -7303579046266608368L;

    private final GitClient git;

    /**
     * Constructor for an instance of {@link DeltaCalculator} which can be used for Git.
     *
     * @param git
     *         The {@link GitClient}
     */
    public GitDeltaCalculator(final GitClient git) {
        super();
        this.git = git;
    }

    @Override
    public Optional<Delta> calculateDelta(final Run<?, ?> build, final Run<?, ?> referenceBuild,
            final String scmKeyFilter, final FilteredLog log) {
        Optional<GitCommitsRecord> buildCommits = GitCommitsRecord.findRecordForScm(build, scmKeyFilter);
        Optional<GitCommitsRecord> referenceCommits = GitCommitsRecord.findRecordForScm(referenceBuild, scmKeyFilter);
        if (buildCommits.isPresent() && referenceCommits.isPresent()) {
            String currentCommit = getLatestCommit(build.getFullDisplayName(), buildCommits.get(), log);
            String referenceCommit = getLatestCommit(referenceBuild.getFullDisplayName(), referenceCommits.get(), log);
            if (!currentCommit.isEmpty() && !referenceCommit.isEmpty()) {
                log.logInfo(
                        "-> Invoking Git delta calculator for determining the made changes between the commits with the IDs '%s' and '%s'",
                        currentCommit, referenceCommit);
                try {
                    RemoteResultWrapper<Delta> wrapped = git.withRepository(
                            new DeltaRepositoryCallback(currentCommit, referenceCommit));
                    wrapped.getInfoMessages().forEach(log::logInfo);
                    return Optional.of(wrapped.getResult());
                }
                catch (IOException | InterruptedException exception) {
                    log.logException(exception, DELTA_ERROR);
                    return Optional.empty();
                }
            }
        }
        log.logError(EMPTY_COMMIT_ERROR);
        return Optional.empty();
    }

    /**
     * Returns the latest commit of the {@link GitCommitsRecord commits record} of a Git repository.
     *
     * @param buildName
     *         The name of the build the commits record corresponds to
     * @param record
     *         The commits record
     * @param log
     *         The log
     *
     * @return the latest commit
     */
    private String getLatestCommit(final String buildName, final GitCommitsRecord record, final FilteredLog log) {
        String latestCommitId = record.getLatestCommit();
        log.logInfo("-> Using commit '%s' as latest commit for build '%s'", latestCommitId, buildName);
        return latestCommitId;
    }
}
