package io.jenkins.plugins.forensics.git.miner;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

import io.jenkins.plugins.forensics.miner.Commit;

/**
 * Collects delta information (added and deleted lines of code) for all files that are part of a given commit.
 *
 * @author Ullrich Hafner
 */
public class DiffsCollector {
    Map<String, Commit> getFilesAndDiffEntriesForCommit(
            final Repository repository, final Git git,
            final Commit fromCommit, final AbstractTreeIterator toTree,
            final FilteredLog logger) {
        Map<String, Commit> filePaths = new HashMap<>();
        try (DiffFormatter formatter = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
            formatter.setRepository(repository);
            List<DiffEntry> diffEntries = git.diff()
                    .setNewTree(CommitAnalyzer.createTreeIteratorFor(repository, fromCommit.getId()))
                    .setOldTree(toTree)
                    .call();
            RenameDetector renames = new RenameDetector(repository);
            renames.addAll(diffEntries);

            for (DiffEntry entry : renames.compute()) {
                Commit commit = new Commit(fromCommit);
                if (entry.getChangeType() == ChangeType.RENAME) {
                    commit.setOldPath(entry.getOldPath());
                }
                if (entry.getChangeType() == ChangeType.DELETE) {
                    commit.markAsDeleted();
                }
                for (Edit edit : formatter.toFileHeader(entry).toEditList()) {
                    commit.addLines(edit.getLengthB());
                    commit.deleteLines(edit.getLengthA());
                }
                filePaths.put(resolvePath(entry), commit);
            }
        }
        catch (IOException | GitAPIException exception) {
            logger.logException(exception, "Can't get Files and DiffEntries.");
        }
        return filePaths;
    }

    private String resolvePath(final DiffEntry entry) {
        String newPath = entry.getNewPath();
        if (newPath.equals(DiffEntry.DEV_NULL)) {
            return entry.getOldPath();
        }
        else {
            return newPath;
        }
    }

}
