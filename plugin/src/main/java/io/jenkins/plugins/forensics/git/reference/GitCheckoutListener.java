package io.jenkins.plugins.forensics.git.reference;

import java.io.File;
import java.io.IOException;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;

import edu.hm.hafner.util.FilteredLog;

import hudson.Extension;
import hudson.FilePath;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.SCMListener;
import hudson.scm.SCM;
import hudson.scm.SCMRevisionState;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMRevisionAction;
import jenkins.scm.api.mixin.ChangeRequestSCMRevision;

import io.jenkins.plugins.forensics.git.util.GitCommitDecoratorFactory;
import io.jenkins.plugins.forensics.git.util.GitCommitTextDecorator;
import io.jenkins.plugins.forensics.git.util.GitRepositoryValidator;
import io.jenkins.plugins.forensics.git.util.RemoteResultWrapper;
import io.jenkins.plugins.forensics.util.CommitDecorator;
import io.jenkins.plugins.util.LogHandler;

/**
 * Tracks all commits since the last build and writes them into a {@link GitCommitsRecord} action to be accessed
 * later. This listener is called on every checkout of a Git Repository in a Jenkins build.
 *
 * @author Arne Schöntag
 */
@Extension
public class GitCheckoutListener extends SCMListener {
    private static final GitCommitTextDecorator DECORATOR = new GitCommitTextDecorator();
    private static final String NO_COMMIT_FOUND = StringUtils.EMPTY;

    @Override
    public void onCheckout(final Run<?, ?> build, final SCM scm, final FilePath workspace,
            final TaskListener listener, final File changelogFile, final SCMRevisionState pollingBaseline) {
        FilteredLog logger = new FilteredLog("Git checkout listener errors:");

        String scmKey = scm.getKey();
        if (hasRecordForScm(build, scmKey)) {
            logSkipping(logger, scmKey);
        }
        else {
            GitRepositoryValidator validator = new GitRepositoryValidator(scm, build, workspace, listener, logger);
            if (validator.isGitRepository()) {
                recordNewCommits(build, validator, logger);
            }
        }

        LogHandler logHandler = new LogHandler(listener, "GitCheckoutListener");
        logHandler.log(logger);
    }

    private void logSkipping(final FilteredLog logger, final String scmKey) {
        logger.logInfo("Skipping recording, since SCM '%s' already has been processed", scmKey);
    }

    private boolean hasRecordForScm(final Run<?, ?> build, final String scmKey) {
        return GitCommitsRecord.findRecordForScm(build, scmKey).isPresent();
    }

    private void recordNewCommits(final Run<?, ?> build, final GitRepositoryValidator gitRepository,
            final FilteredLog logger) {
        String id = gitRepository.getId();
        logger.logInfo("Recording commits of '%s'", id);

        String latestRecordedCommit = getLatestCommitOfPreviousBuild(build, id, logger);
        GitCommitsRecord commitsRecord = recordNewCommits(build, gitRepository, logger, latestRecordedCommit);
        if (hasRecordForScm(build, id)) { // In case a parallel step has added the same result in the meanwhile
            logSkipping(logger, id);
        }
        else {
            build.addAction(commitsRecord);
        }
    }

    private String getLatestCommitOfPreviousBuild(final Run<?, ?> build, final String scmKey, final FilteredLog logger) {
        Optional<GitCommitsRecord> record = getPreviousRecord(build, scmKey);
        if (record.isPresent()) {
            GitCommitsRecord previous = record.get();
            logger.logInfo("Found previous build '%s' that contains recorded Git commits", previous.getOwner());
            logger.logInfo("-> Starting recording of new commits since '%s'",
                    DECORATOR.asText(previous.getLatestCommit()));

            return previous.getLatestCommit();
        }
        else {
            logger.logInfo("Found no previous build with recorded Git commits");
            logger.logInfo("-> Starting initial recording of commits");

            return NO_COMMIT_FOUND;
        }
    }

    private GitCommitsRecord recordNewCommits(final Run<?, ?> build, final GitRepositoryValidator gitRepository,
            final FilteredLog logger, final String latestCommit) {
        BuildCommits commits = recordCommitsSincePreviousBuild(latestCommit, isMerge(build), gitRepository, logger);

        CommitDecorator commitDecorator
                = GitCommitDecoratorFactory.findCommitDecorator(gitRepository.getScm(), logger);
        String id = gitRepository.getId();
        if (commits.isEmpty()) {
            logger.logInfo("-> No new commits found");

            return new GitCommitsRecord(build, id, logger, commits, commitDecorator.asLink(latestCommit));
        }
        else {
            if (commits.size() == 1) {
                logger.logInfo("-> Recorded one new commit", commits.size());
            }
            else {
                logger.logInfo("-> Recorded %d new commits", commits.size());
            }
            return new GitCommitsRecord(build, id, logger, commits, commitDecorator.asLink(commits.getLatestCommit()));
        }
    }

    private boolean isMerge(final Run<?, ?> build) {
        // FIXME: see https://issues.jenkins.io/browse/JENKINS-66480?focusedCommentId=412857
        SCMRevisionAction scmRevision = build.getAction(SCMRevisionAction.class);
        if (scmRevision == null) {
            return false;
        }

        SCMRevision revision = scmRevision.getRevision();
        if (revision instanceof ChangeRequestSCMRevision) {
            return ((ChangeRequestSCMRevision<?>) revision).isMerge();
        }
        return false;
    }

    private BuildCommits recordCommitsSincePreviousBuild(final String latestCommitName,
            final boolean isMergeCommit, final GitRepositoryValidator gitRepository, final FilteredLog logger) {
        try {
            RemoteResultWrapper<BuildCommits> resultWrapper = gitRepository.createClient()
                    .withRepository(new GitCommitsCollector(latestCommitName, isMergeCommit));
            logger.merge(resultWrapper);

            return resultWrapper.getResult();
        }
        catch (IOException | InterruptedException exception) {
            logger.logException(exception, "Unable to record commits of git repository '%s'", gitRepository.getId());

            return new BuildCommits(latestCommitName);
        }
    }

    private Optional<GitCommitsRecord> getPreviousRecord(final Run<?, ?> currentBuild, final String scmKey) {
        for (Run<?, ?> build = currentBuild.getPreviousBuild(); build != null; build = build.getPreviousBuild()) {
            Optional<GitCommitsRecord> record = GitCommitsRecord.findRecordForScm(build, scmKey);
            if (record.isPresent()) {
                return record;
            }
        }
        return Optional.empty();
    }
}
