package io.jenkins.plugins.forensics.git.delta;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

import edu.hm.hafner.util.FilteredLog;

import hudson.remoting.VirtualChannel;

import io.jenkins.plugins.forensics.delta.model.Change;
import io.jenkins.plugins.forensics.delta.model.ChangeEditType;
import io.jenkins.plugins.forensics.delta.model.Delta;
import io.jenkins.plugins.forensics.delta.model.FileChanges;
import io.jenkins.plugins.forensics.delta.model.FileEditType;
import io.jenkins.plugins.forensics.git.delta.model.GitDelta;
import io.jenkins.plugins.forensics.git.util.AbstractRepositoryCallback;
import io.jenkins.plugins.forensics.git.util.RemoteResultWrapper;

/**
 * Repository callback that calculates the code difference - so called 'delta' - between two commits.
 *
 * @author Florian Orendi
 */
public class DeltaRepositoryCallback extends AbstractRepositoryCallback<RemoteResultWrapper<Delta>> {

    static final String ERROR_MESSAGE_UNKNOWN_FILE_EDIT_TYPE = "Detected unknown file edit type '%s'";

    static final String ERROR_MESSAGE_UNKNOWN_CHANGE_TYPE = "Detected unknown change type '%s'";

    private static final long serialVersionUID = -4561284338216569043L;

    private final String currentCommitId;

    private final String referenceCommitId;

    /**
     * Creates an instance which can be used for executing a Git repository callback.
     *
     * @param currentCommitId
     *         The commit ID of the currently processed commit
     * @param referenceCommitId
     *         The commit ID of the reference commit.
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
     * @param repository
     *         The Git repository.
     *
     * @return a serializable wrapper containing the delta
     * @throws IOException
     *         if communicating with Git failed
     */
    private RemoteResultWrapper<Delta> calculateDelta(final Repository repository) throws IOException {
        try (RevWalk walk = new RevWalk(repository)) {
            RevCommit currentCommit = walk.parseCommit(ObjectId.fromString(currentCommitId));
            RevCommit referenceCommit = walk.parseCommit(ObjectId.fromString(referenceCommitId));

            ByteArrayOutputStream diffStream = new ByteArrayOutputStream();

            FilteredLog log = new FilteredLog("Errors from Git Delta:");
            log.logInfo("Start scanning for differences between commits...");

            try (DiffFormatter diffFormatter = new DiffFormatter(diffStream)) {
                diffFormatter.setDiffComparator(RawTextComparator.WS_IGNORE_ALL);
                diffFormatter.setRepository(repository);

                final List<DiffEntry> diffEntries = diffFormatter.scan(referenceCommit, currentCommit);
                final Map<String, FileChanges> fileChangesMap = new HashMap<>();

                log.logInfo("%d files contain changes", diffEntries.size());

                for (DiffEntry diffEntry : diffEntries) {
                    FileEditType fileEditType = getFileEditType(diffEntry.getChangeType());
                    FileChanges fileChanges = createFileChanges(fileEditType, diffEntry, diffFormatter, repository);
                    fileChangesMap.put(diffEntry.getNewId().name(), fileChanges);
                }

                String diffFile = new String(diffStream.toByteArray(), StandardCharsets.UTF_8);

                GitDelta delta = new GitDelta(currentCommitId, referenceCommitId, fileChangesMap, diffFile);
                RemoteResultWrapper<Delta> wrapper = new RemoteResultWrapper<>(delta, "Errors from Git Delta:");
                wrapper.merge(log);

                return wrapper;
            }
        }
    }

    /**
     * Creates an instance of {@link FileChanges} which wraps the information about all changes made to a specific
     * file.
     *
     * @param fileEditType
     *         The type which shows how the file has been affected
     * @param diffEntry
     *         The wrapper created by Git which contains the made changes to a specific file
     * @param diffFormatter
     *         The Git formatter for a patch script
     * @param repository
     *         The Git repository
     *
     * @return the information about changes made to a specific file
     * @throws IOException
     *         if accessing Git resources failed
     */
    private FileChanges createFileChanges(final FileEditType fileEditType, final DiffEntry diffEntry,
            final DiffFormatter diffFormatter, final Repository repository)
            throws IOException {
        String filePath;
        String fileContent;
        if (fileEditType == FileEditType.DELETE) {
            // file path and content of deleted files have to be read using old ID
            filePath = diffEntry.getOldPath();
            fileContent = getFileContent(diffEntry.getOldId().toObjectId(), repository);
        }
        else {
            filePath = diffEntry.getNewPath();
            fileContent = getFileContent(diffEntry.getNewId().toObjectId(), repository);
        }

        diffFormatter.format(diffEntry);

        FileChanges fileChanges = new FileChanges(filePath, fileContent, fileEditType, new HashMap<>());

        for (Edit edit : diffFormatter.toFileHeader(diffEntry).toEditList()) {
            Change change = createChange(edit);
            fileChanges.addChange(change);
        }

        return fileChanges;
    }

    /**
     * Reads the content of a file which is specified by its id - the {@link ObjectId}.
     *
     * @param fileId
     *         The file id
     * @param repository
     *         The Git repository
     *
     * @return the file content
     * @throws IOException
     *         if reading failed
     */
    private String getFileContent(final ObjectId fileId, final Repository repository) throws IOException {
        ObjectDatabase objectDatabase = repository.getObjectDatabase();
        ObjectLoader objectLoader = objectDatabase.open(fileId);
        return new String(objectLoader.getCachedBytes(), StandardCharsets.UTF_8);
    }

    /**
     * Transforms the Git specific {@link ChangeType} to the general model {@link FileEditType}.
     *
     * @param type
     *         The Git specific change type
     *
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
                throw new IllegalArgumentException(String.format(ERROR_MESSAGE_UNKNOWN_FILE_EDIT_TYPE, type));
        }
    }

    /**
     * Transforms the Git specific {@link Edit.Type} to the general model {@link ChangeEditType}.
     *
     * @param type
     *         The Git specific edit type
     *
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
                throw new IllegalArgumentException(String.format(ERROR_MESSAGE_UNKNOWN_CHANGE_TYPE, type));
        }
    }

    /**
     * Processes an edit on a file and returns a {@link Change} which wraps the information about this edit.
     *
     * @param edit
     *         The edit to be processed
     *
     * @return the created change
     */
    private Change createChange(final Edit edit) {
        ChangeEditType changeEditType = getChangeEditType(edit.getType());
        // add 1 to the 'begin' since it is included and the index is zero based
        // 'end' does not need this because the value is excluded anyway
        if (changeEditType == ChangeEditType.DELETE) {
            // get the changed line indices from the old file version
            return new Change(changeEditType, edit.getBeginA() + 1, edit.getEndA());
        }
        else {
            // get the changed line indices from the new file version
            return new Change(changeEditType, edit.getBeginB() + 1, edit.getEndB());
        }
    }
}