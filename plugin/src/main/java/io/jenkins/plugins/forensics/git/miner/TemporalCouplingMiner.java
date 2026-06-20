package io.jenkins.plugins.forensics.git.miner;

import edu.hm.hafner.util.FilteredLog;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import io.jenkins.plugins.forensics.miner.CommitDiffItem;

/**
 * Computes the <em>temporal coupling</em> metric for all file pairs found in a list of {@link CommitDiffItem} objects.
 * Temporal coupling is described in "Your Code as a Crime Scene" (Adam Tornhill, page 72): files that are modified
 * together in the same commit frequently are considered temporally coupled, which often reveals hidden architectural
 * dependencies.
 *
 * <p>For each pair of files that share at least one commit, this miner produces a {@link TemporalCoupling} entry
 * containing:
 * <ul>
 *   <li>the absolute co-change count (number of commits that touched both files), and</li>
 *   <li>the coupling ratio: {@code coChanges / min(totalCommits(fileA), totalCommits(fileB))}.</li>
 * </ul>
 *
 * <p>Only pairs that appear in at least {@code minimumCoChanges} shared commits are included in the result,
 * allowing callers to filter out spurious couplings due to coincidental simultaneous changes.
 *
 * @author Akash Manna
 */
public class TemporalCouplingMiner {
    /**
     * Default minimum number of co-changes required before a file pair is considered temporally coupled.
     */
    public static final int DEFAULT_MINIMUM_CO_CHANGES = 2;

    private final int minimumCoChanges;

    /**
     * Creates a new {@link TemporalCouplingMiner} using the {@link #DEFAULT_MINIMUM_CO_CHANGES} threshold.
     */
    public TemporalCouplingMiner() {
        this(DEFAULT_MINIMUM_CO_CHANGES);
    }

    /**
     * Creates a new {@link TemporalCouplingMiner} with a custom minimum co-change threshold.
     *
     * @param minimumCoChanges
     *         the minimum number of commits in which two files must have appeared together before they are reported
     *         as coupled; must be &ge; 1
     * @throws IllegalArgumentException
     *         if {@code minimumCoChanges} is less than 1
     */
    public TemporalCouplingMiner(final int minimumCoChanges) {
        if (minimumCoChanges < 1) {
            throw new IllegalArgumentException(
                    "minimumCoChanges must be >= 1, but was: " + minimumCoChanges);
        }
        this.minimumCoChanges = minimumCoChanges;
    }

    /**
     * Computes temporal coupling for all file pairs in the supplied commit diff items.
     *
     * <p>The algorithm groups diff items by commit ID, then counts for each unordered file pair how many commits
     * contain both files. Pairs that occur fewer than {@code minimumCoChanges} times are discarded.
     *
     * @param commitDiffItems
     *         the list of commit diff items as produced by {@link CommitAnalyzer}; must not be {@code null}
     * @param logger
     *         a logger for informational and error messages; must not be {@code null}
     *
     * @return an unordered list of {@link TemporalCoupling} entries, one per qualifying file pair
     */
    public List<TemporalCoupling> compute(final List<CommitDiffItem> commitDiffItems, final FilteredLog logger) {
        Map<String, Set<String>> commitToFiles = groupFilesByCommit(commitDiffItems);
        Map<String, Integer> fileCommitCounts = countCommitsPerFile(commitToFiles);
        Map<String, Integer> pairCoChangeCounts = countCoChanges(commitToFiles);

        List<TemporalCoupling> result = buildCouplings(pairCoChangeCounts, fileCommitCounts);

        logger.logInfo("Computed temporal coupling: found %d qualifying file pair(s) with at least %d co-change(s)",
                result.size(), minimumCoChanges);
        return result;
    }

    private Map<String, Set<String>> groupFilesByCommit(final Collection<CommitDiffItem> items) {
        Map<String, Set<String>> commitToFiles = new HashMap<>();
        for (CommitDiffItem item : items) {
            String fileName = resolveFileName(item);
            if (fileName == null) {
                continue;
            }
            commitToFiles.computeIfAbsent(item.getId(), k -> new HashSet<>()).add(fileName);
        }
        return commitToFiles;
    }

    private String resolveFileName(final CommitDiffItem item) {
        String newPath = item.getNewPath();
        if (!"/dev/null".equals(newPath)) {
            return newPath;
        }
        String oldPath = item.getOldPath();
        if (!"/dev/null".equals(oldPath)) {
            return oldPath;
        }
        return null;
    }

    private Map<String, Integer> countCommitsPerFile(final Map<String, Set<String>> commitToFiles) {
        Map<String, Integer> counts = new HashMap<>();
        for (Set<String> files : commitToFiles.values()) {
            for (String file : files) {
                counts.merge(file, 1, Integer::sum);
            }
        }
        return counts;
    }

    private Map<String, Integer> countCoChanges(final Map<String, Set<String>> commitToFiles) {
        Map<String, Integer> pairCounts = new HashMap<>();
        for (Set<String> files : commitToFiles.values()) {
            if (files.size() < 2) {
                continue;
            }
            List<String> sorted = files.stream().sorted().collect(Collectors.toList());
            for (int i = 0; i < sorted.size(); i++) {
                for (int j = i + 1; j < sorted.size(); j++) {
                    String key = pairKey(sorted.get(i), sorted.get(j));
                    pairCounts.merge(key, 1, Integer::sum);
                }
            }
        }
        return pairCounts;
    }

    private List<TemporalCoupling> buildCouplings(final Map<String, Integer> pairCoChangeCounts,
            final Map<String, Integer> fileCommitCounts) {
        List<TemporalCoupling> result = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : pairCoChangeCounts.entrySet()) {
            int coChanges = entry.getValue();
            if (coChanges < minimumCoChanges) {
                continue;
            }
            String[] parts = splitPairKey(entry.getKey());
            String leftFile = parts[0];
            String rightFile = parts[1];

            int leftCommits = fileCommitCounts.getOrDefault(leftFile, 1);
            int rightCommits = fileCommitCounts.getOrDefault(rightFile, 1);
            int minCommits = Math.min(leftCommits, rightCommits);

            double ratio = minCommits > 0 ? (double) coChanges / minCommits : 0.0;
            result.add(new TemporalCoupling(leftFile, rightFile, coChanges, ratio));
        }
        return result;
    }

    private String pairKey(final String fileA, final String fileB) {
        return fileA + "\u0000" + fileB;
    }

    private String[] splitPairKey(final String key) {
        return key.split("\u0000", 2);
    }
}