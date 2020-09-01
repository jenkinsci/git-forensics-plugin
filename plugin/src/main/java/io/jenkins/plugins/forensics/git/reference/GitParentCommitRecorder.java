package io.jenkins.plugins.forensics.git.reference;

import java.util.ArrayList;
import java.util.Optional;

import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Job;
import hudson.model.Run;
import hudson.model.TaskListener;
import io.jenkins.plugins.forensics.reference.ReferenceBuild;
import io.jenkins.plugins.forensics.reference.ReferenceRecorder;
import io.jenkins.plugins.util.JenkinsFacade;

@Extension(ordinal = 10_000)
public class GitParentCommitRecorder extends ReferenceRecorder {

    @DataBoundConstructor
    public GitParentCommitRecorder(JenkinsFacade jenkins) {
        super(jenkins);
    }
    
    public GitParentCommitRecorder() {
        this(new JenkinsFacade());
    }
    
    private Optional<Run<?, ?>> findParentReferenceBuildFromCommit(final Run<?, ?> owner, final String parentCommit) {
        for(Run<?, ?> run = owner.getPreviousBuild(); run != null; run = run.getPreviousBuild()) {
            GitCommitsRecord record = run.getAction(GitCommitsRecord.class);
            if(record != null && parentCommit.equals(record.getLatestCommit())) {
                return Optional.of(run);
            }
        }
        return Optional.empty();
    }
    
    
    @Override
    protected Optional<Run<?, ?>> find(final Run<?, ?> owner, final Run<?, ?> lastCompletedBuildOfReferenceJob) {
        GitCommitsRecord thisCommit = owner.getAction(GitCommitsRecord.class);
        return findParentReferenceBuildFromCommit(owner, thisCommit.getParentCommit());
    }
    
    @Override
    public void perform(Run<?, ?> run, FilePath workspace, Launcher launcher, TaskListener listener) {
        Job<?, ?> parent = run.getParent();
        Optional<Run<?, ?>> reference = find(run, parent.getLastCompletedBuild());
        if(reference.isPresent()) {
            run.addAction(new ReferenceBuild(run, new ArrayList<String>(), reference.get()));
        }
        else {
            run.addAction(new ReferenceBuild(run, new ArrayList<String>()));
        }
    }
    

    @Override
    public Descriptor getDescriptor() {
        return (Descriptor) super.getDescriptor();
    }
    
    
    @Extension
    @Symbol("gitForensics")
    public static class Descriptor extends ReferenceRecorderDescriptor {
        
        @Override
        public String getDisplayName() {
            return " Parent Commit Finder";
        }
    }
}
