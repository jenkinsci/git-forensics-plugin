package io.jenkins.plugins.git.forensics.miner;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;

import com.google.common.collect.Iterables;

public class FileAgeMiner {
    private final Git git;

    public FileAgeMiner(final Repository repository) {
        git = new Git(repository);
    }

    public Map<String, Long> computeAge(final Set<String> files) {
        return files.stream().collect(Collectors.toMap(Function.identity(), this::ageOf));
    }

    private long ageOf(final String file) {
        try {
            Iterable<RevCommit> commits = null;
            commits = git.log().addPath(file).call();
            RevCommit last = Iterables.getLast(commits);
            int commitTime = last.getCommitTime();
            Date date = new Date(commitTime * 1000L);
            Instant instant = date.toInstant();
            return Math.abs(
                    ChronoUnit.DAYS.between(LocalDate.now(), instant.atZone(ZoneId.systemDefault()).toLocalDate()));
        }
        catch (GitAPIException exception) {
            return 0;
        }
    }
}
