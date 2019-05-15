package io.jenkins.plugins.git.forensics.miner;

import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;

/**
 * Mines a Git repository and creates statistics for all available files.
 *
 * @author Ullrich Hafner
 * @see io.jenkins.plugins.git.forensics.miner.FileStatistics
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
        FileStatistics fileStatistics = new FileStatistics();
        try {
            Iterable<RevCommit> commits = git.log().addPath(file).call();
            commits.forEach(fileStatistics::add);
            return fileStatistics;
        }
        catch (GitAPIException exception) {
            // FIXME: logging
        }
        return fileStatistics;
    }
}
