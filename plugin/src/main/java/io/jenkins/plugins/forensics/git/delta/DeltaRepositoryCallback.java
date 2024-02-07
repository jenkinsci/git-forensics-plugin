package io.jenkins.plugins.forensics.git.delta;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffEntry.ChangeType;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.LargeObjectException;
import org.eclipse.jgit.lib.ObjectDatabase;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

import edu.hm.hafner.util.FilteredLog;

import hudson.remoting.VirtualChannel;

import io.jenkins.plugins.forensics.delta.Change;
import io.jenkins.plugins.forensics.delta.ChangeEditType;
import io.jenkins.plugins.forensics.delta.Delta;
import io.jenkins.plugins.forensics.delta.FileChanges;
import io.jenkins.plugins.forensics.delta.FileEditType;
import io.jenkins.plugins.forensics.git.util.AbstractRepositoryCallback;
import io.jenkins.plugins.forensics.git.util.RemoteResultWrapper;

/**
 * Repository callback that calculates the code difference - so called 'delta' - between two commits.
 *
 * @author Florian Orendi
 */
public class DeltaRepositoryCallback extends AbstractRepositoryCallback<RemoteResultWrapper<Delta>> {
    private static final long serialVersionUID = -4561284338216569043L;

    static final String ERROR_MESSAGE_UNKNOWN_FILE_EDIT_TYPE = "Detected unknown file edit type '%s'";

    static final String ERROR_MESSAGE_UNKNOWN_CHANGE_TYPE = "Detected unknown change type '%s'";

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
        FilteredLog log;
        try (RevWalk walk = new RevWalk(repository)) {
            RevCommit currentCommit = walk.parseCommit(ObjectId.fromString(currentCommitId));
            RevCommit referenceCommit = walk.parseCommit(ObjectId.fromString(referenceCommitId));

            ByteArrayOutputStream diffStream = new ByteArrayOutputStream();

            log = new FilteredLog("Errors from Git Delta:");
            log.logInfo("-> Start scanning for differences between commits...");

            try (DiffFormatter diffFormatter = new DiffFormatter(diffStream)) {
                diffFormatter.setDiffComparator(RawTextComparator.WS_IGNORE_ALL);
                diffFormatter.setRepository(repository);
                // enabling rename detection requires a set repository
                diffFormatter.setDetectRenames(true);

                final List<DiffEntry> diffEntries = diffFormatter.scan(referenceCommit, currentCommit);
                final Map<String, FileChanges> fileChangesMap = new HashMap<>();

                log.logInfo("-> %d files contain changes", diffEntries.size());

                for (DiffEntry diffEntry : diffEntries) {
                    FileEditType fileEditType = getFileEditType(diffEntry.getChangeType());
                    FileChanges fileChanges = createFileChanges(fileEditType, diffEntry, diffFormatter, repository);

                    String fileId = getFileId(diffEntry, fileEditType);
                    fileChangesMap.put(fileId, fileChanges);
                }

                log.logInfo("-> Creating the Git diff file");
                String diffFile = diffStream.toString(StandardCharsets.UTF_8);

                GitDelta delta = new GitDelta(currentCommitId, referenceCommitId, fileChangesMap, diffFile);
                RemoteResultWrapper<Delta> wrapper = new RemoteResultWrapper<>(delta, "Errors from Git Delta:");

                log.logInfo("-> Git code delta successfully calculated");
                wrapper.merge(log);

                return wrapper;
            }
        }
        catch (MissingObjectException exception) {
            GitDelta delta = new GitDelta(currentCommitId, referenceCommitId, Map.of(), exception.getMessage());
            RemoteResultWrapper<Delta> wrapper = new RemoteResultWrapper<>(delta, "Errors from Git Delta:");

            wrapper.logError("Could not find commit", exception);
            return wrapper;
        }
    }

    /**
     * Gets the ID of the edited file, which is represented by the passed {@link DiffEntry}. If it is a deleted file,
     * the old ID before the edit is taken in order to provide always unique IDs.
     *
     * @param diffEntry
     *         Represents the edits of a file
     * @param fileEditType
     *         The {@link FileEditType}
     *
     * @return the file ID
     */
    private String getFileId(final DiffEntry diffEntry, final FileEditType fileEditType) {
        if (FileEditType.DELETE.equals(fileEditType)) {
            return diffEntry.getOldId().name();
        }
        else {
            return diffEntry.getNewId().name();
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
        String oldFilePath;
        String fileContent;
        if (fileEditType.equals(FileEditType.DELETE)) {
            fileContent = getFileContent(diffEntry.getOldId().toObjectId(), repository);
            oldFilePath = diffEntry.getOldPath();
            filePath = "";
        }
        else {
            if (fileEditType.equals(FileEditType.ADD)) {
                oldFilePath = "";
            }
            else {
                oldFilePath = diffEntry.getOldPath();
            }
            fileContent = getFileContent(diffEntry.getNewId().toObjectId(), repository);
            filePath = diffEntry.getNewPath();
        }

        diffFormatter.format(diffEntry);

        FileChanges fileChanges = new FileChanges(filePath, oldFilePath, fileContent, fileEditType, new HashMap<>());

        for (Edit edit : diffFormatter.toFileHeader(diffEntry).toEditList()) {
            createChange(edit).ifPresent(fileChanges::addChange);
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
        try (ObjectDatabase objectDatabase = repository.getObjectDatabase()) {
            ObjectLoader objectLoader = objectDatabase.open(fileId);
            if (objectLoader.isLarge()) {
                return new String(objectLoader.getCachedBytes(1000),
                        StandardCharsets.UTF_8);
            }

            return new String(objectLoader.getCachedBytes(), StandardCharsets.UTF_8);
        }
        catch (LargeObjectException exception) {
            return "... skipped large file content ...";
        }
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
     * @return the created change as an Optional if there is a edit which is not empty
     */
    private Optional<Change> createChange(final Edit edit) {
        ChangeEditType changeEditType = getChangeEditType(edit.getType());
        // add 1 to the 'begin' of the interval which is relevant for determining the made change since the begin is
        // included and the index is zero based ('end' does not need this because the value is excluded anyway)
        if (changeEditType.equals(ChangeEditType.DELETE)) {
            return Optional.of(new Change(changeEditType,
                    edit.getBeginA() + 1, edit.getEndA(),
                    edit.getBeginB(), edit.getEndB()));
        }
        else if (changeEditType.equals(ChangeEditType.INSERT)) {
            return Optional.of(new Change(changeEditType,
                    edit.getBeginA(), edit.getEndA(),
                    edit.getBeginB() + 1, edit.getEndB()));
        }
        else if (changeEditType.equals(ChangeEditType.REPLACE)) {
            return Optional.of(new Change(changeEditType,
                    edit.getBeginA() + 1, edit.getEndA(),
                    edit.getBeginB() + 1, edit.getEndB()));
        }
        return Optional.empty();
    }
}
