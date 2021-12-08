package io.jenkins.plugins.forensics.git.delta;

import hudson.remoting.VirtualChannel;
import io.jenkins.plugins.forensics.delta.model.*;
import io.jenkins.plugins.forensics.git.util.AbstractRepositoryCallback;
import io.jenkins.plugins.forensics.git.util.RemoteResultWrapper;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffEntry.ChangeType;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.lib.ObjectDatabase;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Repository callback that calculates the code difference - so called 'delta' - between two commits.
 *
 * @author Florian Orendi
 */
public class DeltaRepositoryCallback extends AbstractRepositoryCallback<RemoteResultWrapper<Delta>> {

    private static final long serialVersionUID = -4561284338216569043L;

    /**
     * The currently processed commit.
     */
    private final String currentCommitId;
    /**
     * The reference commit to calculate the delta to.
     */
    private final String referenceCommitId;

    /**
     * Creates an instance which can be used for executing a Git repository callback.
     *
     * @param currentCommitId The commit ID of the currently processed commit
     * @param referenceCommitId The commit ID of the reference commit.
     */
    public DeltaRepositoryCallback(final String currentCommitId, final String referenceCommitId) {
        super();
        this.currentCommitId = currentCommitId;
        this.referenceCommitId = referenceCommitId;
    }

    @Override
    public RemoteResultWrapper<Delta> invoke(final Repository repository, final VirtualChannel channel)
            throws IOException {
        return calculateDelta(repository);
    }

    /**
     * Calculates the delta between the commits {@link #currentCommitId} and {@link #referenceCommitId} by using the Git
     * repository.
     *
     * @param repository The Git repository.
     * @return a serializable wrapper containing the delta
     * @throws IOException if communicating with Git failed
     */
    private RemoteResultWrapper<Delta> calculateDelta(final Repository repository) throws IOException {
        Delta delta = new Delta(currentCommitId, referenceCommitId);
        RemoteResultWrapper<Delta> wrapper = new RemoteResultWrapper<>(delta, "Git Delta");

        RevWalk walk = new RevWalk(repository);

        RevCommit currentCommit = walk.parseCommit(ObjectId.fromString(currentCommitId));
        RevCommit referenceCommit = walk.parseCommit(ObjectId.fromString(referenceCommitId));

        ByteArrayOutputStream diffStream = new ByteArrayOutputStream();

        wrapper.logInfo("Start scanning for differences between commits...");

        try (DiffFormatter diffFormatter = new DiffFormatter(diffStream)) {
            diffFormatter.setDiffComparator(RawTextComparator.WS_IGNORE_ALL);
            diffFormatter.setRepository(repository);

            final List<DiffEntry> diffEntries = diffFormatter.scan(referenceCommit, currentCommit);
            final Map<String, FileChanges> fileChangesMap = new HashMap<>();

            wrapper.logInfo("%d files contain changes", diffEntries.size());

            for (DiffEntry diffEntry : diffEntries) {
                String filePath = diffEntry.getNewPath();
                String fileContent;
                FileEditType fileEditType = getFileEditType(diffEntry.getChangeType());
                if (fileEditType == FileEditType.UNDEFINED) {
                    // this case should not happen when using JGIT since the types are well defined
                    wrapper.logError("The file edit type of the file '%s' is undefined -> skip", filePath);
                    continue;
                }
                else if (fileEditType == FileEditType.DELETE) {
                    // file path and content of deleted files have to be read using old ID
                    filePath = diffEntry.getOldPath();
                    fileContent = getFileContent(diffEntry.getOldId().toObjectId(), repository);
                }
                else {
                    fileContent = getFileContent(diffEntry.getNewId().toObjectId(), repository);
                }

                diffFormatter.format(diffEntry);

                Map<ChangeEditType, List<Change>> changesByType = new HashMap<>();
                FileChanges fileChanges = new FileChanges(filePath, fileContent, fileEditType, changesByType);
                for (Edit edit : diffFormatter.toFileHeader(diffEntry).toEditList()) {
                    addChangeByType(edit, changesByType);
                }

                fileChangesMap.put(diffEntry.getNewId().name(), fileChanges);
            }

            String diffFile = new String(diffStream.toByteArray(), StandardCharsets.UTF_8);
            delta.setDiffFile(diffFile);
            delta.setFileChanges(fileChangesMap);
        }

        return wrapper;
    }

    /**
     * Reads the content of a file which is specified by its id - the {@link ObjectId}.
     *
     * @param fileId     The file id
     * @param repository The Git repository
     * @return the file content
     * @throws IOException if reading failed
     */
    private String getFileContent(final ObjectId fileId, final Repository repository) throws IOException {
        ObjectDatabase objectDatabase = repository.getObjectDatabase();
        ObjectLoader objectLoader = objectDatabase.open(fileId);
        return new String(objectLoader.getBytes(), StandardCharsets.UTF_8);
    }

    /**
     * Transforms the Git specific {@link ChangeType} to the general model {@link FileEditType}.
     *
     * @param type The Git specific change type
     * @return the transformed general type
     */
    private FileEditType getFileEditType(final ChangeType type) {
        switch (type) {
            case ADD:
                return FileEditType.ADD;
            case DELETE:
                return FileEditType.DELETE;
            case MODIFY:
                return FileEditType.MODIFY;
            case RENAME:
                return FileEditType.RENAME;
            case COPY:
                return FileEditType.COPY;
            default:
                return FileEditType.UNDEFINED;
        }
    }

    /**
     * Transforms the Git specific {@link Edit.Type} to the general model {@link ChangeEditType}.
     *
     * @param type The Git specific edit type
     * @return the transformed general type
     */
    private ChangeEditType getChangeEditType(final Edit.Type type) {
        switch (type) {
            case INSERT:
                return ChangeEditType.INSERT;
            case DELETE:
                return ChangeEditType.DELETE;
            case REPLACE:
                return ChangeEditType.REPLACE;
            case EMPTY:
                return ChangeEditType.EMPTY;
            default:
                return ChangeEditType.UNDEFINED;
        }
    }

    /**
     * Processes an edit on a file and adds it to a map which key are the {@link ChangeEditType}.
     *
     * @param edit          The edit to be processed
     * @param changesByType The map to which edits are added to after processing
     */
    private void addChangeByType(final Edit edit, final Map<ChangeEditType, List<Change>> changesByType) {
        ChangeEditType changeEditType = getChangeEditType(edit.getType());
        if (changeEditType == ChangeEditType.UNDEFINED) {
            return;
        }
        Change change;
        // add 1 to the 'begin' since it is included and the index is zero based
        // 'end' does not need this because the value is excluded anyway
        if (changeEditType == ChangeEditType.DELETE) {
            // get the changed line indices from the old file version
            change = new Change(changeEditType, edit.getBeginA() + 1, edit.getEndA());
        }
        else {
            // get the changed line indices from the new file version
            change = new Change(changeEditType, edit.getBeginB() + 1, edit.getEndB());
        }

        if (changesByType.containsKey(changeEditType)) {
            changesByType.get(changeEditType).add(change);
        }
        else {
            List<Change> changes = new ArrayList<>();
            changes.add(change);
            changesByType.put(changeEditType, changes);
        }
    }
}
