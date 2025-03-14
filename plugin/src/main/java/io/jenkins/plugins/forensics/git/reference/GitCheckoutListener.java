package io.jenkins.plugins.forensics.git.reference;

import org.apache.commons.lang3.StringUtils;

import edu.hm.hafner.util.FilteredLog;

import java.io.File;
import java.io.IOException;
import java.util.Optional;

import hudson.Extension;
import hudson.FilePath;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.SCMListener;
import hudson.scm.SCM;
import hudson.scm.SCMRevisionState;

import io.jenkins.plugins.forensics.git.util.GitCommitDecoratorFactory;
import io.jenkins.plugins.forensics.git.util.GitCommitTextDecorator;
import io.jenkins.plugins.forensics.git.util.GitRepositoryValidator;
import io.jenkins.plugins.forensics.git.util.RemoteResultWrapper;
import io.jenkins.plugins.util.LogHandler;

/**
 * Tracks all the commits since the last build and writes them into a {@link GitCommitsRecord} action to be accessed
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
        var logger = new FilteredLog("Git checkout listener errors:");

        var scmKey = scm.getKey();
        if (hasRecordForScm(build, scmKey)) {
            logSkipping(logger, scmKey);
        }
        else {
            var validator = new GitRepositoryValidator(scm, build, workspace, listener, logger);
            if (validator.isGitRepository()) {
                recordNewCommits(build, validator, logger);
            }
        }

        var logHandler = new LogHandler(listener, "GitCheckoutListener");
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
        var id = gitRepository.getId();
        logger.logInfo("Recording commits of '%s'", id);

        var latestRecordedCommit = getLatestCommitOfPreviousBuild(build, id, logger);
        var commitsRecord = recordNewCommits(build, gitRepository, logger, latestRecordedCommit);
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
            var previous = record.get();
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
        var commits = recordCommitsSincePreviousBuild(latestCommit, gitRepository, logger);
        var calculatedLatestCommit = commits.getMergeOrLatestCommit();

        var id = gitRepository.getId();
        if (commits.isEmpty()) {
            logger.logInfo("-> No new commits found");
        }
        else if (commits.size() == 1) {
            logger.logInfo("-> Recorded one new commit", commits.size());
        }
        else {
            logger.logInfo("-> Recorded %d new commits", commits.size());
        }
        if (commits.hasMerge()) {
            logger.logInfo("-> The latest commit '%s' is a merge commit", calculatedLatestCommit);
        }

        var commitDecorator = GitCommitDecoratorFactory.findCommitDecorator(gitRepository.getScm(), logger);
        return new GitCommitsRecord(build, id, logger, commits, commitDecorator.asLink(calculatedLatestCommit));
    }

    private BuildCommits recordCommitsSincePreviousBuild(final String latestCommitName,
            final GitRepositoryValidator gitRepository, final FilteredLog logger) {
        try {
            RemoteResultWrapper<BuildCommits> resultWrapper = gitRepository.createClient()
                    .withRepository(new GitCommitsCollector(latestCommitName));
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
