package io.jenkins.plugins.git.forensics.miner;

import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;

import edu.umd.cs.findbugs.annotations.Nullable;

import io.jenkins.plugins.forensics.miner.FileStatistics;

/**
 * Mines a Git repository and creates statistics for all available files.
 *
 * @author Ullrich Hafner
 * @see io.jenkins.plugins.forensics.miner.FileStatistics
 * @see io.jenkins.plugins.git.forensics.miner.FilesCollector
 */
public class RepositoryMiner {
    private final Git git;

    RepositoryMiner(final Repository repository) {
        git = new Git(repository);
    }

    Map<String, FileStatistics> analyze(final Set<String> files) {
        return files.stream().collect(Collectors.toMap(Function.identity(), this::analyzeHistory));
    }

    private FileStatistics analyzeHistory(final String file) {
        FileStatistics fileStatistics = new FileStatistics(file);
        try {
            Iterable<RevCommit> commits = git.log().addPath(file).call();
            commits.forEach(c -> fileStatistics.inspectCommit(c.getCommitTime(), getAuthor(c)));
            return fileStatistics;
        }
        catch (GitAPIException exception) {
            // FIXME: logging
        }
        return fileStatistics;
    }

    @Nullable
    private String getAuthor(final RevCommit commit) {
        PersonIdent author = commit.getAuthorIdent();
        if (author != null) {
            return StringUtils.defaultString(author.getEmailAddress(), author.getName());
        }
        PersonIdent committer = commit.getCommitterIdent();
        if (committer != null) {
            return StringUtils.defaultString(committer.getEmailAddress(), committer.getName());
        }
        return StringUtils.EMPTY;
    }
}
