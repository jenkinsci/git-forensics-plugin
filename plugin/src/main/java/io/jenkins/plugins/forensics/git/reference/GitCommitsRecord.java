package io.jenkins.plugins.forensics.git.reference;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.eclipse.jgit.lib.ObjectId;

import edu.hm.hafner.util.FilteredLog;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import hudson.model.Run;
import hudson.scm.SCM;
import jenkins.model.RunAction2;

import io.jenkins.plugins.forensics.git.util.GitCommitTextDecorator;

/**
 * Stores all commits for a given build and provides a link to the latest commit. For each {@link SCM} repository a
 * unique {@link GitCommitsRecord} instance will be used.
 *
 * @author Arne Schöntag
 */
@SuppressFBWarnings(value = "SE", justification = "transient field owner is restored using a Jenkins callback")
public class GitCommitsRecord implements RunAction2, Serializable {
    private static final long serialVersionUID = 8994811233847179343L;

    /**
     * Tries to find a {@link GitCommitsRecord} for the specified SCM.
     *
     * @param build
     *         the build to search for corresponding {@link GitCommitsRecord} actions
     * @param scmKey
     *         the ID of the SCM repository
     *
     * @return the commits record, if found
     */
    public static Optional<GitCommitsRecord> findRecordForScm(final Run<?, ?> build, final String scmKey) {
        return build.getActions(GitCommitsRecord.class)
                .stream().filter(record -> record.getScmKey().contains(scmKey)).findAny();
    }

    private transient Run<?, ?> owner;

    /**
     * Key of the repository. The {@link GitCheckoutListener} ensures that a single action will be created for each
     * repository.
     */
    // TODO: maybe it makes sense to move these values to a business object that is not loaded every time
    private final String scmKey;
    private final String latestCommit;
    private final RecordingType recordingType;
    private final String latestCommitLink;
    private final String targetParentCommit;
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
     * @param commits
     *         the latest commits in this build (since the previous build)
     * @param latestCommitLink
     *         hyperlink to the latest commit
     */
    public GitCommitsRecord(final Run<?, ?> owner, final String scmKey, final FilteredLog logger,
            final BuildCommits commits, final String latestCommitLink) {
        this.owner = owner;
        this.scmKey = scmKey;
        this.infoMessages = new ArrayList<>(logger.getInfoMessages());
        this.errorMessages = new ArrayList<>(logger.getErrorMessages());
        this.latestCommit = commits.getLatestCommit();
        this.latestCommitLink = latestCommitLink;
        this.commits = new ArrayList<>(commits.getCommits());
        this.recordingType = commits.getRecordingType();
        targetParentCommit = commits.getTarget().name();
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

    public String getLatestCommitLink() {
        return latestCommitLink;
    }

    public String getTargetParentCommit() {
        return targetParentCommit;
    }

    /**
     * Determines if the commits contain a merge commit with the target branch.
     *
     * @return {@code true} if the commits contain a merge commit with the target branch
     */
    public boolean hasTargetParentCommit() {
        return !ObjectId.zeroId().name().equals(targetParentCommit);
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

    /**
     * Returns {@code true} if the specified commit is part of the commits.
     *
     * @param commit
     *         the commit to search for
     *
     * @return {@code true} if the commits contain the specified commit, {@code false} otherwise
     */
    public boolean contains(final String commit) {
        return commits.contains(commit);
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
        return getReferencePoint(referenceCommits, maxCommits, skipUnknownCommits, new FilteredLog("UNUSED"));
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
     * @param logger
     *         the logger
     *
     * @return the found reference build or empty if none has been found
     */
    Optional<Run<?, ?>> getReferencePoint(final GitCommitsRecord referenceCommits, final int maxCommits,
            final boolean skipUnknownCommits, final FilteredLog logger) {
        GitCommitTextDecorator textDecorator = new GitCommitTextDecorator();
        List<String> branchCommits = collectBranchCommits(maxCommits);
        logger.logInfo("-> detected %d commits in current branch (last one: '%s')",
                branchCommits.size(), getHeadCommitOf(branchCommits, textDecorator));
        List<String> targetCommits = new ArrayList<>();
        Run<?, ?> build = referenceCommits.owner;
        for (; targetCommits.size() < maxCommits && build != null;
                build = build.getPreviousBuild()) {
            List<String> additionalCommits = getCommitsForRepository(build);
            logger.logInfo("-> adding %d commits from build '%s' of reference job (last one: '%s')",
                    additionalCommits.size(), build.getDisplayName(),
                    getHeadCommitOf(additionalCommits, textDecorator));

            if (!skipUnknownCommits || branchCommits.containsAll(additionalCommits)) {
                if (skipUnknownCommits) {
                    logger.logInfo("-> all commits of target branch are part of the current build");
                }
                else {
                    logger.logInfo("-> ignoring if some of the commits are not part of the current branch build");
                }
                targetCommits.addAll(additionalCommits);
                Optional<String> referencePoint = branchCommits.stream().filter(targetCommits::contains).findFirst();
                if (referencePoint.isPresent()) {
                    logger.logInfo("-> found a matching commit in current branch and target branch: '%s'",
                            textDecorator.asText(referencePoint.get()));
                    return Optional.of(build);
                }
                logger.logInfo("-> no matching commit found yet, continuing with commits of previous build of '%s'",
                        build.getDisplayName());
            }
            else {
                logger.logInfo("-> not all commits of target branch are part of the collected reference builds yet");
            }
        }
        if (build == null) {
            logger.logInfo("-> stopping commit search since we reached the first build of the reference job");
        }
        if (targetCommits.size() >= maxCommits) {
            logger.logInfo("-> stopping commit search since the #commits of the target builds is %d and the limit `maxCommits` has been set to %d",
                    targetCommits.size(), maxCommits);
        }
        return Optional.empty();
    }

    private String getHeadCommitOf(final List<String> additionalCommits, final GitCommitTextDecorator textDecorator) {
        return additionalCommits.isEmpty() ? "-" : textDecorator.asText(additionalCommits.get(0));
    }

    private List<String> collectBranchCommits(final int maxCommits) {
        List<String> branchCommits = new ArrayList<>();
        for (Run<?, ?> build = owner;
                branchCommits.size() < maxCommits && build != null;
                build = build.getPreviousBuild()) {
            branchCommits.addAll(getCommitsForRepository(build));
        }
        return branchCommits;
    }

    private List<String> getCommitsForRepository(final Run<?, ?> run) {
        return findRecordForScm(run, getScmKey())
                .map(GitCommitsRecord::getCommits)
                .orElse(Collections.emptyList());
    }
}
