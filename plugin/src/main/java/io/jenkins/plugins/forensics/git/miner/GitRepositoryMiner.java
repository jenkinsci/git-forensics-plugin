package io.jenkins.plugins.forensics.git.miner;

import org.apache.commons.lang3.StringUtils;

import edu.hm.hafner.util.FilteredLog;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import java.io.IOException;
import java.io.Serial;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.jenkinsci.plugins.gitclient.GitClient;
import hudson.model.Run;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.util.BuildData;
import hudson.scm.SCM;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMHead.HeadByItem;
import jenkins.scm.api.mixin.ChangeRequestSCMHead;

import io.jenkins.plugins.forensics.git.util.RemoteResultWrapper;
import io.jenkins.plugins.forensics.miner.Baseline;
import io.jenkins.plugins.forensics.miner.CommitDiffItem;
import io.jenkins.plugins.forensics.miner.CommitStatistics;
import io.jenkins.plugins.forensics.miner.RepositoryMiner;
import io.jenkins.plugins.forensics.miner.RepositoryStatistics;

/**
 * Mines a Git repository and creates statistics for all available files.
 *
 * @author Giulia Del Bravo
 * @author Ullrich Hafner
 * @see io.jenkins.plugins.forensics.miner.RepositoryStatistics
 * @see io.jenkins.plugins.forensics.miner.FileStatistics
 * @see CommitDiffItem
 */
@SuppressFBWarnings(value = "SE", justification = "GitClient implementation is Serializable")
public class GitRepositoryMiner extends RepositoryMiner {
    @Serial
    private static final long serialVersionUID = 1157958118716013983L;

    @SuppressWarnings("serial")
    private final GitClient gitClient;
    private transient Run<?, ?> build;
    private transient SCM scm;

    GitRepositoryMiner(final GitClient gitClient, final Run<?, ?> build, final SCM scm) {
        super();

        this.gitClient = gitClient;
        this.build = build;
        this.scm = scm;
    }

    @Override
    @SuppressWarnings("PMD.LooseCoupling")
    public RepositoryStatistics mine(final RepositoryStatistics previous, final FilteredLog logger)
            throws InterruptedException {
        try {
            long nano = System.nanoTime();

            String baseline = resolveBaseline(previous, logger);

            logger.logInfo("Analyzing the commit log of the Git repository '%s'",
                    gitClient.getWorkTree());
            RemoteResultWrapper<ArrayList<CommitDiffItem>> wrapped = gitClient.withRepository(
                    new RepositoryStatisticsCallback(baseline));
            logger.merge(wrapped);

            List<CommitDiffItem> commits = wrapped.getResult();
            logger.logInfo("-> Created report in %d seconds", 1 + (System.nanoTime() - nano) / 1_000_000_000L);
            CommitStatistics.logCommits(commits, logger);

            String latestCommitId;
            if (commits.isEmpty()) {
                latestCommitId = baseline;
            }
            else {
                latestCommitId = commits.get(0).getId();
            }
            var current = new RepositoryStatistics(latestCommitId);
            if (baseline.equals(previous.getLatestCommitId())) {
                current.addAll(previous);
            }
            Collections.reverse(commits); // make sure that we start with old commits to preserve the history
            current.addAll(commits);
            return current;
        }
        catch (IOException exception) {
            logger.logException(exception,
                    "Exception occurred while mining the Git repository using GitClient");
            return new RepositoryStatistics();
        }
    }

    private String resolveBaseline(final RepositoryStatistics previous, final FilteredLog logger) {
        if (getBaseline() == Baseline.TARGET) {
            try {
                var targetBaseline = resolveTargetBaseline(logger);
                if (StringUtils.isNotBlank(targetBaseline)) {
                    logger.logInfo("-> Using commit '%s' as baseline", targetBaseline);
                    return targetBaseline;
                }
                logger.logInfo("-> No target branch found, falling back to previous build as baseline");
            }
            catch (IOException | InterruptedException exception) {
                logger.logException(exception,
                        "Exception occurred while resolving the target branch of the Git repository");
            }
        }
        return previous.getLatestCommitId();
    }

    private String resolveTargetBaseline(final FilteredLog logger) throws IOException, InterruptedException {
        var targetBranch = resolveTargetBranch(logger);
        if (StringUtils.isNotBlank(targetBranch)) {
            return gitClient.withRepository(new TargetBranchSelector(targetBranch));
        }
        var targetHead = resolveTargetHeadFromBuildData(logger);
        if (StringUtils.isNotBlank(targetHead)) {
            return gitClient.withRepository(new MergeBaseSelector(targetHead));
        }
        return StringUtils.EMPTY;
    }

    private String resolveTargetHeadFromBuildData(final FilteredLog logger) {
        if (build == null) {
            return StringUtils.EMPTY;
        }
        for (var buildData : build.getActions(BuildData.class)) {
            if (!matchesScm(buildData)) {
                continue;
            }
            var revision = buildData.getLastBuiltRevision();
            if (revision == null) {
                continue;
            }
            var branches = revision.getBranches();
            if (branches == null || branches.isEmpty()) {
                continue;
            }
            for (var branch : branches) {
                var head = branch.getSHA1String();
                if (!head.equals(revision.getSha1String())) {
                    logger.logInfo("-> Detected target branch head '%s' from Git build data", head);
                    return head;
                }
            }
        }
        return StringUtils.EMPTY;
    }

    private boolean matchesScm(final BuildData buildData) {
        var scmName = buildData.getScmName();
        if (StringUtils.isNotBlank(scmName) && scm != null) {
            return scmName.equals(scm.getKey());
        }
        var remoteUrls = buildData.getRemoteUrls();
        if (remoteUrls == null || remoteUrls.isEmpty()) {
            return true; // no URL information to disambiguate, assume a single SCM
        }
        if (scm instanceof GitSCM gitScm) {
            for (var repository : gitScm.getRepositories()) {
                for (var uri : repository.getURIs()) {
                    if (remoteUrls.contains(stripGitSuffix(uri.toString()))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private String stripGitSuffix(final String url) {
        return url.replaceAll("/+$", "").replaceAll("[.]git$", "");
    }

    private String resolveTargetBranch(final FilteredLog logger) {
        if (build == null) {
            return StringUtils.EMPTY;
        }
        var head = HeadByItem.findHead(build.getParent());
        if (head instanceof ChangeRequestSCMHead changeRequestHead) {
            var target = changeRequestHead.getTarget();
            logger.logInfo("-> Detected a change request for target branch '%s'", target.getName());
            return target.getName();
        }
        logger.logInfo("-> No multibranch change request head detected");
        return StringUtils.EMPTY;
    }
}
