package io.jenkins.plugins.forensics.git.miner;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;

import edu.hm.hafner.util.FilteredLog;
import edu.umd.cs.findbugs.annotations.NonNull;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.jenkinsci.plugins.workflow.steps.Step;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.scm.SCM;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import jenkins.tasks.SimpleBuildStep;

import io.jenkins.plugins.forensics.git.reference.GitCommitsRecord;
import io.jenkins.plugins.forensics.git.util.GitCommitTextDecorator;
import io.jenkins.plugins.forensics.git.util.GitRepositoryValidator;
import io.jenkins.plugins.forensics.git.util.RemoteResultWrapper;
import io.jenkins.plugins.forensics.miner.CommitDiffItem;
import io.jenkins.plugins.forensics.miner.CommitStatistics;
import io.jenkins.plugins.forensics.miner.CommitStatisticsBuildAction;
import io.jenkins.plugins.forensics.miner.ForensicsBuildAction;
import io.jenkins.plugins.forensics.miner.RepositoryStatistics;
import io.jenkins.plugins.forensics.reference.ReferenceFinder;
import io.jenkins.plugins.forensics.util.ScmResolver;
import io.jenkins.plugins.util.LogHandler;

/**
 * A pipeline {@link Step} or Freestyle or Maven {@link Recorder} that obtains statistics for all repository files. The
 * following statistics are computed:
 * <ul>
 *     <li>total number of commits</li>
 *     <li>total number of different authors</li>
 *     <li>creation time</li>
 *     <li>last modification time</li>
 *     <li>lines of code (from the commit details)</li>
 *     <li>code churn (changed lines since created)</li>
 * </ul>
 * Stores the created statistics in a {@link RepositoryStatistics} instance. The result is attached to
 * a {@link Run} by registering a {@link ForensicsBuildAction}.
 *
 * @author Ullrich Hafner
 */
@SuppressWarnings({"checkstyle:ClassFanOutComplexity", "PMD.ExcessiveImports"})
public class CommitStatisticsStep extends Recorder implements SimpleBuildStep {
    private static final GitCommitTextDecorator RENDERER = new GitCommitTextDecorator();

    private String scm = StringUtils.EMPTY;

    /**
     * Creates a new instance of {@link  CommitStatisticsStep}.
     */
    @DataBoundConstructor
    public CommitStatisticsStep() {
        super();

        // empty constructor required for Stapler
    }

    /**
     * Sets the SCM that should be used to find the reference build for. The reference recorder will select the SCM
     * based on a substring comparison, there is no need to specify the full name.
     *
     * @param scm
     *         the ID of the SCM to use (a substring of the full ID)
     */
    @DataBoundSetter
    public void setScm(final String scm) {
        this.scm = scm;
    }

    public String getScm() {
        return scm;
    }

    @Override
    public void perform(@NonNull final Run<?, ?> run, @NonNull final FilePath workspace, @NonNull final EnvVars env,
            @NonNull final Launcher launcher, @NonNull final TaskListener listener) throws InterruptedException {
        LogHandler logHandler = new LogHandler(listener, "Git DiffStats");
        FilteredLog logger = new FilteredLog("Errors while computing diff statistics");

        logger.logInfo("Analyzing commits to obtain diff statistics for affected repository files");

        for (SCM repository : new ScmResolver().getScms(run, getScm())) {
            logger.logInfo("-> checking SCM '%s'", repository.getKey());
            logHandler.log(logger);

            GitRepositoryValidator validator = new GitRepositoryValidator(repository, run, workspace, listener, logger);
            if (validator.isGitRepository()) {
                try {
                    computeStats(run, logger, repository, validator);
                }
                catch (IOException exception) {
                    logger.logInfo("-> skipping due to exception: %s", exception);
                }
            }
            else {
                logger.logInfo("-> skipping not supported repository");
            }

            logHandler.log(logger);
        }
    }

    private void computeStats(final Run<?, ?> run, final FilteredLog logger, final SCM repository,
            final GitRepositoryValidator validator) throws IOException, InterruptedException {
        Optional<Run<?, ?>> possibleReferenceBuild = new ReferenceFinder().findReference(run, logger);
        if (possibleReferenceBuild.isPresent()) {
            Run<?, ?> referenceBuild = possibleReferenceBuild.get();
            logger.logInfo("-> found reference build '%s'", referenceBuild);
            GitCommitsRecord referenceCommits = referenceBuild.getAction(GitCommitsRecord.class);
            if (referenceCommits == null) {
                logger.logInfo("-> skipping since reference build '%s' has no recorded commits", referenceBuild);
            }
            else {
                computeStatsBasedOnReferenceBuild(run, logger, repository, validator, referenceCommits);
            }
        }
        else {
            Run<?, ?> previousCompletedBuild = run.getPreviousCompletedBuild();
            if (previousCompletedBuild == null) {
                logger.logInfo("-> skipping step since no previous build has been completed yet");
            }
            else {
                computeStatsBasedOnPreviousBuild(run, logger, repository, validator, previousCompletedBuild);
            }
        }
    }

    private void computeStatsBasedOnReferenceBuild(final Run<?, ?> run, final FilteredLog logger, final SCM repository,
            final GitRepositoryValidator validator, final GitCommitsRecord referenceCommits)
            throws IOException, InterruptedException {
        String latestCommit = referenceCommits.getLatestCommit();
        String ancestor = validator.createClient().withRepository(new MergeBaseSelector(latestCommit));
        if (StringUtils.isNotEmpty(ancestor)) {
            logger.logInfo("-> found best common ancestor '%s' between HEAD and target branch commit '%s'",
                    renderCommit(ancestor), renderCommit(latestCommit));
            extractStats(run, repository, validator.createClient(), logger, ancestor);

            return;
        }

        logger.logInfo("-> No common ancestor between HEAD and target branch commit '%s' found", latestCommit);
    }

    private void computeStatsBasedOnPreviousBuild(final Run<?, ?> run, final FilteredLog logger, final SCM repository,
            final GitRepositoryValidator validator, final Run<?, ?> previousCompletedBuild)
            throws IOException, InterruptedException {
        logger.logInfo("-> no reference build found, using previous build '%s' as baseline",
                previousCompletedBuild);
        GitCommitsRecord commitsRecord = previousCompletedBuild.getAction(GitCommitsRecord.class);
        if (commitsRecord != null) {
            String latestCommit = commitsRecord.getLatestCommit();
            if (StringUtils.isNotEmpty(latestCommit)) {
                logger.logInfo("-> found latest previous commit '%s'", renderCommit(latestCommit));

                extractStats(run, repository, validator.createClient(), logger, latestCommit);

                return;
            }
        }
        logger.logInfo("-> skipping since previous completed build '%s' has no recorded commits",
                previousCompletedBuild);
    }

    private void extractStats(final Run<?, ?> run, final SCM repository, final GitClient gitClient,
            final FilteredLog logger, final String ancestor) throws IOException, InterruptedException {
        RemoteResultWrapper<ArrayList<CommitDiffItem>> wrapped = gitClient.withRepository(
                new RepositoryStatisticsCallback(ancestor));
        List<CommitDiffItem> commits = wrapped.getResult();
        CommitStatistics.logCommits(commits, logger);

        RepositoryStatistics repositoryStatistics = new RepositoryStatistics(ancestor);
        repositoryStatistics.addAll(commits);
        run.addAction(new CommitStatisticsBuildAction(run, repository.getKey(),
                repositoryStatistics.getLatestStatistics()));
    }

    private String renderCommit(final String ancestor) {
        return RENDERER.asText(ancestor);
    }

    @Override
    public Descriptor getDescriptor() {
        return (Descriptor) super.getDescriptor();
    }

    /**
     * Descriptor for this step: defines the context and the UI elements.
     */
    @Extension
    @Symbol("gitDiffStat")
    public static class Descriptor extends BuildStepDescriptor<Publisher> {
        @NonNull
        @Override
        public String getDisplayName() {
            return "Git Diff Statistics";
        }

        @Override
        public boolean isApplicable(final Class<? extends AbstractProject> jobType) {
            return true;
        }
    }
}
