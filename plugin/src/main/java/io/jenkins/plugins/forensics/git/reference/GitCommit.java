package io.jenkins.plugins.forensics.git.reference;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import edu.hm.hafner.util.FilteredLog;

import hudson.model.Run;
import jenkins.model.RunAction2;

import io.jenkins.plugins.util.JenkinsFacade;

/**
 * Action, which writes the information of the revisions into GitCommitLogs.
 *
 * @author Arne Schöntag
 */
@SuppressWarnings({"unused", "checkstyle:HiddenField"})
public class GitCommit implements RunAction2, Serializable {
    private static final long serialVersionUID = 8994811233847179343L;

    private static final String NAME = "GitCommit";
    private static JenkinsFacade jenkinsFacade = new JenkinsFacade();

    private transient Run<?, ?> owner;

    private final FilteredLog logger;

    /**
     * Key of the repository. The {@link GitCommitListener} ensures that a single action will be created for each
     * repository. 
     */
    private final String repositoryId;
    private final List<String> commits;

    public GitCommit(final Run<?, ?> owner, String repositoryId, final List<String> commits, final FilteredLog logger) {
        super();
        
        this.owner = owner;
        this.repositoryId = repositoryId;
        this.logger = logger;
        this.commits =  new ArrayList<>(commits);
    }

    public Run<?, ?> getOwner() {
        return owner;
    }

    static void setJenkinsFacade(final JenkinsFacade facade) {
        jenkinsFacade = facade;
    }

    public static GitCommit findVCSCommitFor(final Run<?, ?> run) {
        return run.getAction(GitCommit.class);
    }

    private static List<GitCommit> findAllExtensions() {
        return jenkinsFacade.getExtensionsFor(GitCommit.class);
    }

    public List<String> getGitCommitLog() {
        return commits;
    }

    public String getSummary() {
        return commits.toString();
    }

    public String getBuildName() {return owner.getExternalizableId();}

    /**
         * Tries to find the reference point of the GitCommit of another build.
         * @param reference the GitCommit of the other build
         * @param maxLogs maximal amount of commits looked at.
         * @return the build Id of the reference build or Optional.empty() if none found.
         */
    public Optional<String> getReferencePoint(final GitCommit reference, final int maxLogs, final boolean skipUnknownCommits) {
        if (reference == null || reference.getClass() != GitCommit.class) {
            // Incompatible version control types.
            // Wont happen if this build and the reference build are from the same VCS repository.
            return Optional.empty();
        }
        GitCommit referenceCommit = (GitCommit) reference;
        List<String> branchCommits = new ArrayList<>(this.getGitCommitLog());
        List<String> masterCommits = new ArrayList<>(referenceCommit.getGitCommitLog());

        Optional<String> referencePoint = Optional.empty();

        // Fill branch commit list
        Run<?, ?> tmp = owner;
        while (branchCommits.size() < maxLogs && tmp != null) {
            GitCommit gitCommit = getGitCommitForRepository(tmp);
            if (gitCommit == null) {
                // Skip build if it has no GitCommit Action.
                tmp = tmp.getPreviousBuild();
                continue;
            }
            branchCommits.addAll(gitCommit.getGitCommitLog());
            tmp = tmp.getPreviousBuild();
        }

        // Fill master commit list and check for intersection point
        tmp = referenceCommit.owner;
        while (masterCommits.size() < maxLogs && tmp != null) {
            GitCommit gitCommit = getGitCommitForRepository(tmp);
            if (gitCommit == null) {
                // Skip build if it has no GitCommit Action.
                tmp = tmp.getPreviousBuild();
                continue;
            }
            List<String> commits = gitCommit.getGitCommitLog();
            if (skipUnknownCommits && !branchCommits.containsAll(commits)) {
                // Skip build if it has unknown commits to current branch.
                tmp = tmp.getPreviousBuild();
                continue;
            }
            masterCommits.addAll(commits);
            referencePoint = branchCommits.stream().filter(masterCommits::contains).findFirst();
            // If an intersection is found the buildId in Jenkins will be saved
            if (referencePoint.isPresent()) {
                return Optional.of(tmp.getExternalizableId());
            }
            tmp = tmp.getPreviousBuild();
        }
        return Optional.empty();
    }

    /**
     * If multiple Repositorys are in a build this GitCommit will only look a the ones with the same repositoryId.
     * @param run the bulid to get the Actions from
     * @return the correct GitCommit if present. Or else null.
     */
    private GitCommit getGitCommitForRepository(Run<?, ?> run) {
        List<GitCommit> list = run.getActions(GitCommit.class);
        return list.stream().filter(gc -> this.getScmKey().equals(gc.getScmKey())).findFirst().orElse(null);
    }

    public String getScmKey() {
        return repositoryId;
    }

    public String getLatestRevision() {
        return getGitCommitLog().get(0);
    }

    public List<String> getRevisions() {
        return getGitCommitLog();
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
        return NAME;
    }

    @Override
    public String getUrlName() {
        return null;
    }

    public String getLatestCommitName() {
        if (isNotEmpty()) {
            return getGitCommitLog().get(0);
        }
        throw new NoSuchElementException("This record contains no commits");
    }

    public boolean isNotEmpty() {
        return !commits.isEmpty();
    }

    public List<String> getInfoMessages() {
        return logger.getInfoMessages();
    }

    public List<String> getErrorMessages() {
        return logger.getErrorMessages();
    }
}
