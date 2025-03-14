package io.jenkins.plugins.forensics.git.delta;

import org.apache.commons.lang3.StringUtils;

import edu.hm.hafner.util.FilteredLog;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import java.io.IOException;
import java.io.Serial;
import java.util.Optional;

import org.jenkinsci.plugins.gitclient.GitClient;
import hudson.model.Run;

import io.jenkins.plugins.forensics.delta.Delta;
import io.jenkins.plugins.forensics.delta.DeltaCalculator;
import io.jenkins.plugins.forensics.git.reference.GitCommitsRecord;
import io.jenkins.plugins.forensics.git.util.GitCommitTextDecorator;
import io.jenkins.plugins.forensics.git.util.RemoteResultWrapper;

/**
 * A {@link DeltaCalculator} for Git.
 *
 * @author Florian Orendi
 */
@SuppressFBWarnings(value = "SE", justification = "GitClient implementation is Serializable")
public class GitDeltaCalculator extends DeltaCalculator {
    @Serial
    private static final long serialVersionUID = -7303579046266608368L;
    private static final GitCommitTextDecorator DECORATOR = new GitCommitTextDecorator();

    static final String DELTA_ERROR = "Computing delta information failed with an exception:";
    static final String EMPTY_COMMIT_ERROR = "Calculating the Git code delta is not possible due to an unknown commit ID";

    private final GitClient git;
    private final String scmKey;

    /**
     * Constructor for an instance of {@link DeltaCalculator} which can be used for Git.
     *
     * @param git
     *         The {@link GitClient}
     * @param scmKey
     *         the key of the SCM repository (substring that must be part of the SCM key)
     */
    public GitDeltaCalculator(final GitClient git, final String scmKey) {
        super();

        this.git = git;
        this.scmKey = scmKey;
    }

    @Override
    public Optional<Delta> calculateDelta(final Run<?, ?> build, final Run<?, ?> referenceBuild,
            final String scmKeyFilter, final FilteredLog log) {
        var scm = StringUtils.defaultIfEmpty(scmKeyFilter, scmKey);
        Optional<GitCommitsRecord> buildCommits = GitCommitsRecord.findRecordForScm(build, scm);
        Optional<GitCommitsRecord> referenceCommits = GitCommitsRecord.findRecordForScm(referenceBuild, scm);
        if (buildCommits.isPresent() && referenceCommits.isPresent()) {
            var currentCommit = getLatestCommit(build.getFullDisplayName(), buildCommits.get(), log);
            var referenceCommit = getLatestCommit(referenceBuild.getFullDisplayName(), referenceCommits.get(), log);
            if (!currentCommit.isEmpty() && !referenceCommit.isEmpty()) {
                log.logInfo("-> Invoking Git delta calculator for determining the changes between "
                                + "commits '%s' and '%s' in repository '%s'",
                        DECORATOR.asText(currentCommit), DECORATOR.asText(referenceCommit),
                        git.getWorkTree().getRemote());
                try {
                    RemoteResultWrapper<Delta> wrapped = git.withRepository(
                            new DeltaRepositoryCallback(currentCommit, referenceCommit));
                    wrapped.getInfoMessages().forEach(log::logInfo);
                    wrapped.getErrorMessages().forEach(log::logError);
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
     * Returns the latest commit of the {@link GitCommitsRecord commit record} of a Git repository.
     *
     * @param buildName
     *         the name of the build the commits record corresponds to
     * @param record
     *         the commit record
     * @param log
     *         the log
     *
     * @return the latest commit
     */
    private String getLatestCommit(final String buildName, final GitCommitsRecord record, final FilteredLog log) {
        var latestCommitId = record.getLatestCommit();
        log.logInfo("-> Using commit '%s' as latest commit for build '%s'", DECORATOR.asText(latestCommitId), buildName);
        return latestCommitId;
    }
}
