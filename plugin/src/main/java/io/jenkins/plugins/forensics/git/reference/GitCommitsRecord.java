package io.jenkins.plugins.forensics.git.reference;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import edu.hm.hafner.util.FilteredLog;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import hudson.model.Run;
import hudson.scm.SCM;
import jenkins.model.RunAction2;

/**
 * Stores all commits for a given build and provides a link to the latest commit. For each {@link SCM} repository a
 * unique {@link GitCommitsRecord} instance will be used.
 *
 * @author Arne Schöntag
 */
@SuppressFBWarnings(value = "SE", justification = "transient field owner ist restored using a Jenkins callback")
public class GitCommitsRecord implements RunAction2, Serializable {
    private static final long serialVersionUID = 8994811233847179343L;

    private transient Run<?, ?> owner;

    /**
     * Key of the repository. The {@link GitCheckoutListener} ensures that a single action will be created for each
     * repository.
     */
    private final String scmKey;
    private final String latestCommit;
    private final List<String> parentCommits;
    private final RecordingType recordingType;
    private final List<String> commits;
    private final List<String> errorMessages;
    private final List<String> infoMessages;

    /** Determines if this record is the starting point or an incremental record that is based on the previous record. */
    enum RecordingType {
        START,
        INCREMENTAL
    }

    /**
     * Creates a new {@link GitCommitsRecord} instance with the specified list of new commits.
     *
     * @param owner
     *         the current build as owner of the Git commits
     * @param scmKey
     *         the ID of the SCM repository
     * @param logger
     *         the logger
     * @param latestCommit
     *         the latest commit (either the head of the new commits or the latest commit from the previous build)
     * @param commits
     *         the new commits in this build (since the previous build)
     * @param recordingType
     *         the recording type that indicates if the number of commits is
     */
    GitCommitsRecord(final Run<?, ?> owner, final String scmKey,
            final FilteredLog logger, final String latestCommit,
            final List<String> commits, final List<String> parentCommits, final RecordingType recordingType) {
        super();

        this.owner = owner;
        this.scmKey = scmKey;
        this.infoMessages = new ArrayList<>(logger.getInfoMessages());
        this.errorMessages = new ArrayList<>(logger.getErrorMessages());
        this.commits = new ArrayList<>(commits);
        this.latestCommit = latestCommit;
        this.parentCommits = parentCommits;
        this.recordingType = recordingType;
    }

    /**
     * Creates a new {@link GitCommitsRecord} instance with the specified list of new commits.
     *
     * @param owner
     *         the current build as owner of the Git commits
     * @param scmKey
     *         the ID of the SCM repository
     * @param logger
     *         the logger
     * @param latestCommit
     *         the latest commit (either the head of the new commits or the latest commit from the previous build)
     * @param commits
     *         the new commits in this build (since the previous build)
     */
    GitCommitsRecord(final Run<?, ?> owner, final String scmKey,
            final FilteredLog logger, final String latestCommit, final List<String> commits, final List<String> parentCommits) {
        this(owner, scmKey, logger, latestCommit, commits, parentCommits, RecordingType.INCREMENTAL);
    }

    /**
     * Creates a new {@link GitCommitsRecord} instance with an empty list of commits.
     *
     * @param owner
     *         the current build as owner of the Git commits
     * @param scmKey
     *         the ID of the SCM repository
     * @param logger
     *         the logger
     * @param latestCommit
     *         the latest commit of the previous build
     */
    GitCommitsRecord(final Run<?, ?> owner, final String scmKey,
            final FilteredLog logger, final String latestCommit, final List<String> parentCommits) {
        this(owner, scmKey, logger, latestCommit, Collections.emptyList(), parentCommits);
    }

    public Run<?, ?> getOwner() {
        return owner;
    }

    public boolean isFirstBuild() {
        return recordingType == RecordingType.START;
    }

    public String getScmKey() {
        return scmKey;
    }

    public String getLatestCommit() {
        return latestCommit;
    }
    
    public List<String> getParentCommits() {
        return parentCommits;
    }

    public List<String> getErrorMessages() {
        return errorMessages;
    }

    public List<String> getInfoMessages() {
        return infoMessages;
    }

    /**
     * Returns the number of new commits.
     *
     * @return the number of new commits
     */
    public int size() {
        return commits.size();
    }

    public int getSize() {
        return size();
    }

    public boolean isNotEmpty() {
        return !commits.isEmpty();
    }

    public boolean isEmpty() {
        return commits.isEmpty();
    }

    public List<String> getCommits() {
        return commits;
    }

    @Override
    public void onAttached(final Run<?, ?> run) {
        this.owner = run;
    }

    @Override
    public void onLoad(final Run<?, ?> run) {
        onAttached(run);
    }

    @Override
    public String getIconFileName() {
        return null;
    }

    @Override
    public String getDisplayName() {
        return Messages.Action_DisplayName();
    }

    @Override
    public String getUrlName() {
        return null;
    }

    @Override
    public String toString() {
        return String.format("Commits in '%s': %d (latest: %s)", owner, size(), getLatestCommit());
    }

    /**
     * Tries to find a reference build using the specified {@link GitCommitsRecord} of the reference job as a starting
     * point.
     *
     * @param referenceCommits
     *         the recorded commits of the build of the reference job that should be used as a starting point for the
     *         search
     * @param maxCommits
     *         maximal number of commits to look at
     * @param skipUnknownCommits
     *         determines whether a build with unknown commits should be skipped or not
     *
     * @return the found reference build or empty if none has been found
     */
    public Optional<Run<?, ?>> getReferencePoint(final GitCommitsRecord referenceCommits,
            final int maxCommits, final boolean skipUnknownCommits) {
        List<String> branchCommits = collectBranchCommits(maxCommits);

        List<String> masterCommits = new ArrayList<>(referenceCommits.getCommits());
        for (Run<?, ?> build = referenceCommits.owner;
                masterCommits.size() < maxCommits && build != null;
                build = build.getPreviousBuild()) {
            List<String> additionalCommits = getCommitsForRepository(build);
            if (!skipUnknownCommits || branchCommits.containsAll(additionalCommits)) {
                masterCommits.addAll(additionalCommits);
                Optional<String> referencePoint = branchCommits.stream().filter(masterCommits::contains).findFirst();
                if (referencePoint.isPresent()) {
                    return Optional.of(build);
                }
            }
        }
        return Optional.empty();
    }

    private List<String> collectBranchCommits(final int maxCommits) {
        List<String> branchCommits = new ArrayList<>(this.getCommits());
        for (Run<?, ?> build = owner;
                branchCommits.size() < maxCommits && build != null;
                build = build.getPreviousBuild()) {
            branchCommits.addAll(getCommitsForRepository(build));
        }
        return branchCommits;
    }

    private List<String> getCommitsForRepository(final Run<?, ?> run) {
        return run.getActions(GitCommitsRecord.class)
                .stream()
                .filter(gc -> this.getScmKey().equals(gc.getScmKey()))
                .findAny()
                .map(GitCommitsRecord::getCommits)
                .orElse(Collections.emptyList());
    }
}
