package io.jenkins.plugins.forensics.git.miner;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffEntry.ChangeType;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.RenameDetector;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.util.io.DisabledOutputStream;

import edu.hm.hafner.util.FilteredLog;
import edu.hm.hafner.util.TreeStringBuilder;

import io.jenkins.plugins.forensics.miner.CommitDiffItem;

/**
 * Collects delta information (added and deleted lines of code) for all files that are part of a given commit.
 *
 * @author Giulia Del Bravo
 * @author Ullrich Hafner
 */
public class DiffsCollector {
    List<CommitDiffItem> getDiffsForCommit(
            final Repository repository, final Git git,
            final CommitDiffItem fromCommit, final AbstractTreeIterator toTree,
            final TreeStringBuilder fileNameBuilder, final FilteredLog logger) {
        List<CommitDiffItem> commits = new ArrayList<>();
        try (DiffFormatter formatter = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
            formatter.setRepository(repository);
            List<DiffEntry> diffEntries = git.diff()
                    .setNewTree(CommitAnalyzer.createTreeIteratorFor(fromCommit.getId(), repository, logger))
                    .setOldTree(toTree)
                    .call();
            RenameDetector renames = new RenameDetector(repository);
            renames.addAll(diffEntries);

            for (DiffEntry entry : renames.compute()) {
                CommitDiffItem commit = new CommitDiffItem(fromCommit);
                commit.setNewPath(fileNameBuilder.intern(entry.getNewPath()));
                if (isDeleteOrRename(entry)) {
                    commit.setOldPath(fileNameBuilder.intern(entry.getOldPath()));
                }
                for (Edit edit : formatter.toFileHeader(entry).toEditList()) {
                    commit.addLines(edit.getLengthB());
                    commit.deleteLines(edit.getLengthA());
                }
                commits.add(commit);
            }
        }
        catch (IOException | GitAPIException exception) {
            logger.logException(exception, "Can't compute diffs for commit " + fromCommit);
        }
        return commits;
    }

    private boolean isDeleteOrRename(final DiffEntry entry) {
        return entry.getChangeType() == ChangeType.RENAME || entry.getChangeType() == ChangeType.DELETE;
    }
}
