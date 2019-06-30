package io.jenkins.plugins.git.forensics.miner;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;

import edu.umd.cs.findbugs.annotations.Nullable;

import io.jenkins.plugins.forensics.miner.FileStatistics;
import io.jenkins.plugins.forensics.miner.RepositoryMiner;
import io.jenkins.plugins.forensics.miner.RepositoryStatistics;

/**
 * Mines a Git repository and creates statistics for all available files.
 *
 * @author Ullrich Hafner
 * @see io.jenkins.plugins.forensics.miner.FileStatistics
 * @see io.jenkins.plugins.git.forensics.miner.FilesCollector
 */
public class GitRepositoryMiner extends RepositoryMiner {
    private static final long serialVersionUID = 1157958118716013983L;

    private final Repository repository;

    GitRepositoryMiner(final Repository repository) {
        this.repository = repository;
    }

    RepositoryStatistics analyze(final Set<String> files) {
        RepositoryStatistics statistics = new RepositoryStatistics();
        List<FileStatistics> fileStatistics = files.stream()
                .map(file -> analyzeHistory(file, statistics))
                .collect(Collectors.toList());
        statistics.addAll(fileStatistics);
        return statistics;
    }

    private FileStatistics analyzeHistory(final String fileName,
            final RepositoryStatistics statistics) {
        FileStatistics fileStatistics = new FileStatistics(fileName);
        try {
            Git git = new Git(repository);
            Iterable<RevCommit> commits = git.log().addPath(fileName).call();
            commits.forEach(c -> fileStatistics.inspectCommit(c.getCommitTime(), getAuthor(c)));
            return fileStatistics;
        }
        catch (GitAPIException exception) {
            statistics.logException(exception, "Can't analyze history of file %s", fileName);
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

    @Override
    public RepositoryStatistics mine() {
        try {
            ObjectId head = repository.resolve(Constants.HEAD);
            Set<String> files = new FilesCollector(repository).findAllFor(head);
            return analyze(files);
        }
        catch (IOException exception) {
            RepositoryStatistics statistics = new RepositoryStatistics();
            statistics.logException(exception, "Can't obtain HEAD of repository.");
            return statistics;
        }
    }
}
