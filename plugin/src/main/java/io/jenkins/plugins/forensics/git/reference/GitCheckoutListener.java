package io.jenkins.plugins.forensics.git.reference;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

import edu.hm.hafner.util.FilteredLog;

import org.jenkinsci.plugins.gitclient.GitClient;
import org.jenkinsci.plugins.gitclient.RepositoryCallback;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.SCMListener;
import hudson.remoting.VirtualChannel;
import hudson.scm.SCM;
import hudson.scm.SCMRevisionState;

import io.jenkins.plugins.forensics.git.reference.GitCommitsRecord.RecordingType;
import io.jenkins.plugins.forensics.git.util.GitRepositoryValidator;
import io.jenkins.plugins.util.LogHandler;

/**
 * Tracks all commits since the last build and writes them into a {@link GitCommitsRecord} action to be accessed
 * later. This listener is called on every checkout of a Git Repository in a Jenkins build.
 *
 * @author Arne Schöntag
 */
@Extension
public class GitCheckoutListener extends SCMListener {
    @Override
    public void onCheckout(final Run<?, ?> build, final SCM scm, final FilePath workspace,
            final TaskListener listener, final File changelogFile, final SCMRevisionState pollingBaseline) {
        FilteredLog logger = new FilteredLog("Git checkout listener errors:");

        String scmKey = scm.getKey();
        if (hasRecordForScm(build, scmKey)) {
            logger.logInfo("Skipping recording, since SCM '%s' already has been processed", scmKey);
        }
        else {
            GitRepositoryValidator validator = new GitRepositoryValidator(scm, build, workspace, listener, logger);
            if (validator.isGitRepository()) {
                recordNewCommits(build, validator.createClient(), scmKey, logger);
            }
        }

        LogHandler logHandler = new LogHandler(listener, "GitCheckoutListener");
        logHandler.log(logger);
    }

    private boolean hasRecordForScm(final Run<?, ?> build, final String scmKey) {
        return findRecordForScm(build, scmKey).isPresent();
    }

    private Optional<GitCommitsRecord> findRecordForScm(final Run<?, ?> build, final String scmKey) {
        return build.getActions(GitCommitsRecord.class)
                .stream().filter(record -> scmKey.equals(record.getScmKey())).findAny();
    }

    private void recordNewCommits(final Run<?, ?> build, final GitClient gitClient,
            final String scmKey, final FilteredLog logger) {
        logger.logInfo("Recording commits of '%s'", scmKey);

        String latestRecordedCommit = getLatestRecordedCommit(build, scmKey, logger);
        GitCommitsRecord commitsRecord = recordNewCommits(build, gitClient, scmKey, logger, latestRecordedCommit);
        build.addAction(commitsRecord);
    }

    private String getLatestRecordedCommit(final Run<?, ?> build, final String scmKey, final FilteredLog logger) {
        Optional<GitCommitsRecord> record = getPreviousRecord(build, scmKey);
        if (record.isPresent()) {
            GitCommitsRecord previous = record.get();
            logger.logInfo("Found previous build '%s' that contains recorded Git commits", previous.getOwner());
            logger.logInfo("-> Starting recording of new commits since '%s'", previous.getLatestCommit());
            return previous.getLatestCommit();
        }
        else {
            logger.logInfo("Found no previous build with recorded Git commits");
            logger.logInfo("-> Starting initial recording of commits");
            return StringUtils.EMPTY;
        }
    }

    private GitCommitsRecord recordNewCommits(final Run<?, ?> build, final GitClient gitClient,
            final String scmKey, final FilteredLog logger, final String latestCommit) {
        List<RevCommit> revCommits = recordCommitsSincePreviousBuild(latestCommit, gitClient, scmKey, logger);
        List<String> parentCommits = new ArrayList<>();
        if(revCommits.isEmpty()) {
            parentCommits.add(findParentCommit(gitClient));
        } else {
            for(RevCommit revCommit : revCommits) {
                String parentCommit = StringUtils.EMPTY;
                if(revCommit.getParentCount() > 0) {
                    parentCommit = revCommit.getParent(0).getName();
                }
                parentCommits.add(parentCommit);
            }
        }
        List<String> commits = revCommits.stream().map(RevCommit::getName).collect(Collectors.toList());
        
        if (commits.isEmpty()) {
            logger.logInfo("-> No new commits found");
            return new GitCommitsRecord(build, scmKey, logger, latestCommit, parentCommits);
        }
        else {
            if (commits.size() == 1) {
                logger.logInfo("-> Recorded one new commit", commits.size());
            }
            else {
                logger.logInfo("-> Recorded %d new commits", commits.size());
            }
            return new GitCommitsRecord(build, scmKey, logger, commits.get(0), commits, parentCommits,
                    getRecordingType(latestCommit));
        }
    }

    private RecordingType getRecordingType(final String latestCommit) {
        if (StringUtils.isBlank(latestCommit)) {
            return RecordingType.START;
        }
        return RecordingType.INCREMENTAL;
    }

    private List<RevCommit> recordCommitsSincePreviousBuild(final String latestCommitName, final GitClient gitClient,
            final String scmKey, final FilteredLog logger) {
        try {
            return gitClient.withRepository(new GitCommitsCollector(latestCommitName));
        }
        catch (IOException | InterruptedException exception) {
            logger.logException(exception, "Unable to record commits of git repository '%s'", scmKey);
            return Collections.emptyList();
        }
    }

    private Optional<GitCommitsRecord> getPreviousRecord(final Run<?, ?> currentBuild, final String scmKey) {
        for (Run<?, ?> build = currentBuild.getPreviousBuild(); build != null; build = build.getPreviousBuild()) {
            Optional<GitCommitsRecord> record = findRecordForScm(build, scmKey);
            if (record.isPresent()) {
                return record;
            }
        }
        return Optional.empty();
    }
    
    private String findParentCommit(final GitClient gitClient) {
        try {
            return gitClient.withRepository((repository, channel) -> {
                RevCommit currentBuildCommit = getHead(repository);
                if(currentBuildCommit.getParentCount() > 0) {
                    return currentBuildCommit.getParent(0).getName();
                }
                return StringUtils.EMPTY;
            });
        } catch(IOException | InterruptedException e) {

        }
        return StringUtils.EMPTY;
    }
    
    private static RevCommit getHead(final Repository repo) throws IOException {
        return new RevWalk(repo).parseCommit(repo.resolve(Constants.HEAD));
    }

    /**
     * Collects and records all commits since the last build.
     */
    private static class GitCommitsCollector implements RepositoryCallback<List<RevCommit>> {
        private static final long serialVersionUID = -5980402198857923793L;

        private static final int MAX_COMMITS = 200; // TODO: should the number of recorded commits be configurable?

        private final String latestRecordedCommit;

        GitCommitsCollector(final String latestRecordedCommit) {
            this.latestRecordedCommit = latestRecordedCommit;
        }

        @Override
        public List<RevCommit> invoke(final Repository repository, final VirtualChannel channel) throws IOException {
            List<RevCommit> newCommits = new ArrayList<>();
            try (Git git = new Git(repository)) {
                for (RevCommit commit : git.log().add(getHead(repository)).call()) {
                    String commitId = commit.getName();
                    if (commitId.equals(latestRecordedCommit) || newCommits.size() >= MAX_COMMITS) {
                        return newCommits;
                    }
                    newCommits.add(commit);
                }
            }
            catch (GitAPIException e) {
                throw new IOException("Unable to record commits of git repository.", e);
            }
            return newCommits;
        }
    }
}
