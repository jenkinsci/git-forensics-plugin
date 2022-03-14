package io.jenkins.plugins.forensics.git.delta;

import java.io.IOException;
import java.util.Optional;

import edu.hm.hafner.util.FilteredLog;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import org.jenkinsci.plugins.gitclient.GitClient;
import hudson.model.Run;

import io.jenkins.plugins.forensics.delta.DeltaCalculator;
import io.jenkins.plugins.forensics.delta.model.Delta;
import io.jenkins.plugins.forensics.git.reference.GitCommitsRecord;
import io.jenkins.plugins.forensics.git.util.RemoteResultWrapper;
import io.jenkins.plugins.forensics.util.ScmResolver;

/**
 * A {@link DeltaCalculator} for Git.
 *
 * @author Florian Orendi
 */
@SuppressFBWarnings(value = "SE", justification = "GitClient implementation is Serializable")
public class GitDeltaCalculator extends DeltaCalculator {

    static final String DELTA_ERROR = "Computing delta information failed with an exception:";

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
    public Optional<Delta> calculateDelta(@NonNull final String currentCommit, @NonNull final String referenceCommit,
            @NonNull final FilteredLog log) {
        try {
            log.logInfo(
                    "Invoking Git delta calculator for determining the made changes between the commits with the IDs %s and %s",
                    currentCommit, referenceCommit);

            if (!currentCommit.isEmpty() && !referenceCommit.isEmpty()) {
                RemoteResultWrapper<Delta> wrapped = git.withRepository(
                        new DeltaRepositoryCallback(currentCommit, referenceCommit));
                wrapped.getInfoMessages().forEach(log::logInfo);

                return Optional.of(wrapped.getResult());
            }
        }
        catch (IOException | InterruptedException exception) {
            log.logException(exception, DELTA_ERROR);
        }

        return Optional.empty();
    }

    @Override
    public Optional<Delta> calculateDelta(final Run<?, ?> build, final Run<?, ?> referenceBuild,
            final FilteredLog logger) {
        String scm = new ScmResolver().getScm(build).getKey();
        Optional<GitCommitsRecord> buildCommits = GitCommitsRecord.findRecordForScm(build, scm);
        Optional<GitCommitsRecord> referenceCommits = GitCommitsRecord.findRecordForScm(referenceBuild, scm);
        if (buildCommits.isPresent() && referenceCommits.isPresent()) {
            return calculateDelta(buildCommits.get().getLatestCommit(),
                    referenceCommits.get().getLatestCommit(), logger);
        }
        return Optional.empty();
    }
}
