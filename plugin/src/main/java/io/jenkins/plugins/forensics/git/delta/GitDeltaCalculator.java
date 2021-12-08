package io.jenkins.plugins.forensics.git.delta;

import edu.hm.hafner.util.FilteredLog;
import io.jenkins.plugins.forensics.delta.DeltaCalculator;
import io.jenkins.plugins.forensics.delta.model.Delta;
import io.jenkins.plugins.forensics.git.util.RemoteResultWrapper;
import org.jenkinsci.plugins.gitclient.GitClient;

import java.io.IOException;
import java.util.Optional;

/**
 * A {@link DeltaCalculator} for Git.
 *
 * @author Florian Orendi
 */
public class GitDeltaCalculator extends DeltaCalculator {

    /**
     * Error message when calculating the delta failed.
     */
    static final String DELTA_ERROR = "Computing delta information failed with an exception:";

    private static final long serialVersionUID = -7303579046266608368L;

    /**
     * The used Git client.
     */
    private final GitClient git;

    public GitDeltaCalculator(final GitClient git) {
        super();
        this.git = git;
    }

    @Override
    public Optional<Delta> calculateDelta(final String currentCommit, final String referenceCommit,
                                          final FilteredLog log) {
        try {
            log.logInfo("Invoking Git delta calculator for determining the made changes between the commits with the IDs %s and %s",
                    currentCommit, referenceCommit);

            if (currentCommit != null && !currentCommit.isEmpty() && referenceCommit != null
                    && !referenceCommit.isEmpty()) {
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
