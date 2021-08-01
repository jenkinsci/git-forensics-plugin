package io.jenkins.plugins.forensics.git.reference;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

import edu.hm.hafner.util.FilteredLog;

import hudson.Extension;
import hudson.FilePath;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.SCMListener;
import hudson.remoting.VirtualChannel;
import hudson.scm.SCM;
import hudson.scm.SCMRevisionState;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMRevisionAction;
import jenkins.scm.api.mixin.ChangeRequestSCMRevision;

import io.jenkins.plugins.forensics.git.reference.GitCommitsRecord.RecordingType;
import io.jenkins.plugins.forensics.git.util.AbstractRepositoryCallback;
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
@SuppressWarnings("PMD.ExcessiveImports")
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
        return findRecordForScm(build, scmKey).isPresent();
    }

    private Optional<GitCommitsRecord> findRecordForScm(final Run<?, ?> build, final String scmKey) {
        return build.getActions(GitCommitsRecord.class)
                .stream().filter(record -> scmKey.equals(record.getScmKey())).findAny();
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
        Commits commits = recordCommitsSincePreviousBuild(latestCommit, isMerge(build), gitRepository, logger);

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


    private Commits recordCommitsSincePreviousBuild(final String latestCommitName,
            final boolean isMergeCommit, final GitRepositoryValidator gitRepository, final FilteredLog logger) {
        try {
            RemoteResultWrapper<Commits> resultWrapper = gitRepository.createClient()
                    .withRepository(new GitCommitsCollector(latestCommitName, isMergeCommit));
            logger.merge(resultWrapper);

            return resultWrapper.getResult();
        }
        catch (IOException | InterruptedException exception) {
            logger.logException(exception, "Unable to record commits of git repository '%s'", gitRepository.getId());

            return new Commits(isMergeCommit, latestCommitName);
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

    /**
     * Collects and records all commits since the last build.
     */
    private static class GitCommitsCollector extends AbstractRepositoryCallback<RemoteResultWrapper<Commits>> {
        private static final long serialVersionUID = -5980402198857923793L;

        private static final int MAX_COMMITS = 200; // TODO: should the number of recorded commits be configurable?

        private final String latestRecordedCommit;
        private final boolean isMergeCommit;

        GitCommitsCollector(final String latestRecordedCommit, final boolean isMergeCommit) {
            this.latestRecordedCommit = latestRecordedCommit;
            this.isMergeCommit = isMergeCommit;
        }

        @Override
        public RemoteResultWrapper<Commits> invoke(final Repository repository, final VirtualChannel channel) throws IOException {
            try (Git git = new Git(repository)) {
                Commits commits = new Commits(isMergeCommit, latestRecordedCommit);
                RemoteResultWrapper<Commits> result = new RemoteResultWrapper<>(commits, "Errors while collecting commits");
                findHeadCommit(repository, commits, result);
                for (RevCommit commit : git.log().add(commits.head).call()) {
                    String commitId = commit.getName();
                    if (commitId.equals(latestRecordedCommit) || commits.size() >= MAX_COMMITS) {
                        return result;
                    }
                    commits.add(commitId);
                }
                return result;
            }
            catch (GitAPIException e) {
                throw new IOException("Unable to record commits of git repository.", e);
            }
        }

        private void findHeadCommit(final Repository repository, final Commits commits, final FilteredLog logger)
                throws IOException {
            RevCommit head = getHead(repository);
            if (isMergeCommit) {
                RevCommit[] parents = head.getParents();
                if (parents.length < 1) {
                    logger.logInfo("-> No parent commits found - detected the first commit in the branch");
                    logger.logInfo("-> Using head commit '%s' as starting point", DECORATOR.asText(head));
                    commits.setHead(head);
                }
                else if (parents.length == 1) {
                    logger.logInfo("-> Single parent commit found - branch is already descendant of target branch head");
                    logger.logInfo("-> Using head commit '%s' as starting point", DECORATOR.asText(head));
                    commits.setHead(head);
                }
                else {
                    logger.logInfo("-> Multiple parent commits found - skipping commits of local merge '%s'", DECORATOR.asText(head));
                    commits.setMerge(head);
                    commits.setHead(parents[0]);
                    logger.logInfo("-> Using parent commit '%s' of local merge as starting point", DECORATOR.asText(parents[0]));
                    logger.logInfo("-> Storing target branch head '%s' (second parent of local merge) ", DECORATOR.asText(parents[1]));
                    commits.setTarget(parents[1]);
                }
            }
            else {
                logger.logInfo("-> Using head commit '%s' as starting point", DECORATOR.asText(head));
                commits.setHead(head);
            }
        }

        private RevCommit getHead(final Repository repository) throws IOException {
            ObjectId head = repository.resolve(Constants.HEAD);
            if (head == null) {
                throw new IOException("No HEAD commit found in " + repository);
            }
            return new RevWalk(repository).parseCommit(head);
        }
    }

    static class Commits implements Serializable {
        private final boolean isMergeCommit;
        private final String previousBuildCommit;

        private final List<String> commits = new ArrayList<>();

        private ObjectId head = ObjectId.zeroId();
        private ObjectId target = ObjectId.zeroId();
        private ObjectId merge = ObjectId.zeroId();

        Commits(final boolean isMergeCommit, final String previousBuildCommit) {
            this.isMergeCommit = isMergeCommit;
            this.previousBuildCommit = previousBuildCommit;
        }

        String getPreviousBuildCommit() {
            return previousBuildCommit;
        }

        void setHead(final RevCommit head) {
            this.head = head;
        }

        ObjectId getHead() {
            return head;
        }

        void setTarget(final RevCommit target) {
            this.target = target;
        }

        ObjectId getTarget() {
            return target;
        }

        void setMerge(final RevCommit merge) {
            this.merge = merge;
        }

        ObjectId getMerge() {
            return merge;
        }

        List<String> getCommits() {
            return commits;
        }

        int size() {
            return commits.size();
        }

        void add(final String commitId) {
            commits.add(commitId);
        }

        boolean isEmpty() {
            return commits.isEmpty();
        }

        RecordingType getRecordingType() {
            if (StringUtils.isBlank(previousBuildCommit)) {
                return RecordingType.START;
            }
            return RecordingType.INCREMENTAL;
        }

        public String getLatestCommit() {
            if (commits.isEmpty()) {
                return previousBuildCommit;
            }
            return commits.get(0);
        }
    }
}
