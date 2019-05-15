package io.jenkins.plugins.git.forensics.miner;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;

import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * Aggregates Git commit statistics for a given file:
 * <ul>
 *     <li>total number of commits</li>
 *     <li>total number of different authors</li>
 *     <li>creation time</li>
 *     <li>last modification time</li>
 * </ul>
 *
 * @author Ullrich Hafner
 */
public class FileStatistics implements Serializable {
    private static final long serialVersionUID = -5776167206905031327L;
    private int numberOfAuthors;
    private int numberOfCommits;
    private int creationTime;
    private int lastModificationTime;

    private transient Set<String> authors = new HashSet<>();

    private Object readResolve() {
        authors = new HashSet<>(); // restore an empty set since the authors set is used only during aggregation

        return this;
    }

    public int getNumberOfAuthors() {
        return numberOfAuthors;
    }

    public int getNumberOfCommits() {
        return numberOfCommits;
    }

    public int getCreationTime() {
        return creationTime;
    }

    public long getAgeInDays() {
        return computeDaysSince(creationTime);
    }

    public int getLastModificationTime() {
        return lastModificationTime;
    }

    public long getLastModifiedInDays() {
        return computeDaysSince(lastModificationTime);
    }

    void add(final RevCommit commit) {
        if (lastModificationTime == 0) {
            lastModificationTime = commit.getCommitTime();
        }
        creationTime = commit.getCommitTime();
        numberOfCommits++;
        authors.add(getAuthor(commit));
        numberOfAuthors = authors.size();
    }

    private long computeDaysSince(final int timeInSecondsSinceEpoch) {
        return Math.abs(ChronoUnit.DAYS.between(LocalDate.now(), toLocalDate(timeInSecondsSinceEpoch)));
    }

    private LocalDate toLocalDate(final int timeInSecondsSinceEpoch) {
        return new Date(timeInSecondsSinceEpoch * 1000L)
                .toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDate();
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
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        FileStatistics that = (FileStatistics) o;
        return numberOfAuthors == that.numberOfAuthors
                && numberOfCommits == that.numberOfCommits
                && creationTime == that.creationTime
                && lastModificationTime == that.lastModificationTime;
    }

    @Override
    public int hashCode() {
        return Objects.hash(numberOfAuthors, numberOfCommits, creationTime, lastModificationTime);
    }

    @Override
    public String toString() {
        return ReflectionToStringBuilder.toString(this);
    }
}
