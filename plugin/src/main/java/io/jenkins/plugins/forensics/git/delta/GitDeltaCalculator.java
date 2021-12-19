package io.jenkins.plugins.forensics.git.delta;

import java.io.IOException;
import java.util.Optional;
import javax.annotation.Nonnull;

import edu.hm.hafner.util.FilteredLog;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import org.jenkinsci.plugins.gitclient.GitClient;

import io.jenkins.plugins.forensics.delta.DeltaCalculator;
import io.jenkins.plugins.forensics.delta.model.Delta;
import io.jenkins.plugins.forensics.git.util.RemoteResultWrapper;

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
    public Optional<Delta> calculateDelta(@Nonnull final String currentCommit, @Nonnull final String referenceCommit,
            @Nonnull final FilteredLog log) {
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
}
