package io.jenkins.plugins.forensics.git.miner;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.EditList;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.EmptyTreeIterator;
import org.eclipse.jgit.util.io.DisabledOutputStream;

import edu.hm.hafner.util.FilteredLog;
import edu.umd.cs.findbugs.annotations.CheckForNull;

import io.jenkins.plugins.forensics.miner.FileStatistics;

/**
 * Collects delta information (added and deleted lines of code) for all files that are part of a given commit.
 *
 * @author Ullrich Hafner
 */
public class DiffsCollector {
    Map<String, CommitFileDelta> getFilesAndDiffEntriesFromCommit(
            final Repository repository, final Git git,
            @CheckForNull final String oldCommit, final String newCommit,
            final FilteredLog logger, final Map<String, FileStatistics> fileStatistics) {
        Map<String, CommitFileDelta> filePaths = new HashMap<>();
        try (DiffFormatter formatter = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
            formatter.setRepository(repository);
            List<DiffEntry> diffEntries = git.diff()
                    .setOldTree(getTreeParser(repository, oldCommit))
                    .setNewTree(getTreeParser(repository, newCommit))
                    .call();
            for (DiffEntry entry : diffEntries) {
                filePaths.put(resolvePath(entry),
                        computeLinesOfCode(formatter.toFileHeader(entry).toEditList(), newCommit));
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

    private CommitFileDelta computeLinesOfCode(final EditList edits, final String currentCommitId) {
        CommitFileDelta changes = new CommitFileDelta(currentCommitId);
        for (Edit edit : edits) {
            changes.updateDelta(edit.getLengthB(), edit.getLengthA()); // TODO: should we handle replacements differently?
        }
        return changes;
    }

    private AbstractTreeIterator getTreeParser(final Repository repository, @CheckForNull final String objectId)
            throws IOException {
        if (objectId == null) {
            return new EmptyTreeIterator();
        }
        try (RevWalk walk = new RevWalk(repository)) {
            RevCommit commit = walk.parseCommit(repository.resolve(objectId));
            RevTree tree = walk.parseTree(commit.getTree().getId());

            CanonicalTreeParser treeParser = new CanonicalTreeParser();
            try (ObjectReader reader = repository.newObjectReader()) {
                treeParser.reset(reader, tree.getId());
            }
            walk.dispose();
            return treeParser;
        }
    }
}
