package io.jenkins.plugins.forensics.git.reference;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import edu.hm.hafner.util.VisibleForTesting;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.jgit.revwalk.RevCommit;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.jenkinsci.Symbol;
import hudson.Extension;
import hudson.model.Result;
import hudson.model.Run;
import hudson.util.ListBoxModel;
import io.jenkins.plugins.forensics.git.util.ReferenceBuildFinderStrategy;
import io.jenkins.plugins.forensics.reference.ReferenceRecorder;
import io.jenkins.plugins.util.JenkinsFacade;

/**
 * Recorder that finds a reference build that matches best with the current build of a given Git branch.
 *
 * @author Arne Schöntag
 * @author Ullrich Hafner
 */
@Extension(ordinal = 10_000) @SuppressWarnings("PMD.DataClass")
public class GitReferenceRecorder extends ReferenceRecorder {
    private int maxCommits = 100;
    private boolean skipUnknownCommits = false;
    private ReferenceBuildFinderStrategy referenceBuildFinderStrategy = 
            ReferenceBuildFinderStrategy.PARENT_COMMIT_BUILD;

    /**
     * Creates a new instance of {@link GitReferenceRecorder}.
     */
    @DataBoundConstructor
    public GitReferenceRecorder() {
        this(new JenkinsFacade());
    }

    @VisibleForTesting
    GitReferenceRecorder(final JenkinsFacade jenkins) {
        super(jenkins);
    }

    /**
     * Sets the maximal number of commits that will be compared with the builds of the reference job to find the
     * matching reference build.
     *
     * @param maxCommits
     *         maximal number of commits
     */
    @DataBoundSetter
    public void setMaxCommits(final int maxCommits) {
        this.maxCommits = maxCommits;
    }

    public int getMaxCommits() {
        return maxCommits;
    }

    /**
     * If enabled, then a build of the reference job will be skipped if one of the commits is unknown in the current
     * branch.
     *
     * @param skipUnknownCommits
     *         if {@code true} then builds with unknown commits will be skipped, otherwise unknown commits will be
     *         ignored
     */
    @DataBoundSetter
    public void setSkipUnknownCommits(final boolean skipUnknownCommits) {
        this.skipUnknownCommits = skipUnknownCommits;
    }

    public boolean isSkipUnknownCommits() {
        return skipUnknownCommits;
    }
    
    /**
     * Determines the reference build selection strategy, if
     *  BEST_MATCH_BUILD: default reference point will be used
     *  PARENT_COMMIT_BUILD: a reference build, which has built the parent commit of the current built revision
     *  
     * @param referenceBuildFinderStrategy         
     */
    @DataBoundSetter
    public void setReferenceBuildFinderStrategy(ReferenceBuildFinderStrategy referenceBuildFinderStrategy) {
        this.referenceBuildFinderStrategy = referenceBuildFinderStrategy;
    }
    
    public ReferenceBuildFinderStrategy getReferenceBuildFinderStrategy() {
        return referenceBuildFinderStrategy;
    }
        
    /**
     * Tries to find a successful build with the given commit hash
     * @param owner
     *          the current build
     * @param commit
     *          the SHA1 of the commit we wish to find
     *          
     * @return the build, if exists
     */
    private Optional<Run<?, ?>> findBuildWithLatestCommit(Run<?, ?> owner, String commit) {
        for(Run<?, ?> run = owner.getPreviousBuild(); run != null; run = run.getPreviousBuild()) {
            GitCommitsRecord record = run.getAction(GitCommitsRecord.class);
            if(record != null && commit.equals(record.getLatestCommit()) && run.getResult().equals(Result.SUCCESS)) {
                return Optional.of(run);
            }
        }
        return Optional.empty();
    }

    /**
     * Tries to find a reference build, which built the parent commit of the current build
     * @param thisCommit
     *          GitCommitsRecord for the current build
     *
     * @return reference build, if exists
     */
    private Optional<Run<?, ?>> findParentReferenceBuildFromCommit(final GitCommitsRecord thisCommit) {
        List<String> parentCommits = thisCommit.getParentCommits();
        String parentCommit = parentCommits.get(0);

        while (!StringUtils.isEmpty(parentCommit)) {
        	Optional<Run<?, ?>> reference = findBuildWithLatestCommit(thisCommit.getOwner(), parentCommit);
        	if(reference.isPresent()) {
        		return reference;
        	}
        	
        	if(thisCommit.getCommits().contains(parentCommit)) {
        		parentCommit = parentCommits.get(thisCommit.getCommits().indexOf(parentCommit));
        	} else {
        		break;
        	}
        }
        
        return Optional.empty();
    }

    @Override
    protected Optional<Run<?, ?>> find(final Run<?, ?> owner, final Run<?, ?> lastCompletedBuildOfReferenceJob) {
        GitCommitsRecord thisCommit = owner.getAction(GitCommitsRecord.class);
        
        if(referenceBuildFinderStrategy == ReferenceBuildFinderStrategy.BEST_MATCH_BUILD) {
            GitCommitsRecord referenceCommit = lastCompletedBuildOfReferenceJob.getAction(GitCommitsRecord.class);
            return thisCommit.getReferencePoint(referenceCommit, getMaxCommits(), isSkipUnknownCommits());
        } else {
            return findParentReferenceBuildFromCommit(thisCommit);
        }
   }

    @Override
    @SuppressFBWarnings("BC")
    public Descriptor getDescriptor() {
        return (Descriptor) super.getDescriptor();
    }

    /**
     * Descriptor for this step: defines the symbol for the pipeline.
     */
    @Extension
    @Symbol("gitForensics")
    // TODO: should the symbol be part of the API?
    public static class Descriptor extends ReferenceRecorderDescriptor {
        
        /**
         * Returns the model with the possible reference build finder strategies.
         *
         * @return the model with the possible reference build finder strategies
         */
        public ListBoxModel doFillReferenceBuildFinderStrategyItems() {
            ListBoxModel model = new ListBoxModel();
            model.add(Messages.ReferenceBuildSelectionStrategy_PARENT_COMMIT_BUILD(), 
                    ReferenceBuildFinderStrategy.PARENT_COMMIT_BUILD.name());
            model.add(Messages.ReferenceBuildSelectionStrategy_BEST_MATCH_BUILD(), 
                    ReferenceBuildFinderStrategy.BEST_MATCH_BUILD.name());
            return model;
        }
    }
}
