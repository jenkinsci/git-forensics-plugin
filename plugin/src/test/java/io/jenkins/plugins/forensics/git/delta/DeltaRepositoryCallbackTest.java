package io.jenkins.plugins.forensics.git.delta;

import static org.assertj.core.api.Assertions.*;

import io.jenkins.plugins.forensics.delta.FileEditType;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryBuilder;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Tests submodule changes without requiring the referenced commits in the parent repository. */
class DeltaRepositoryCallbackTest {
    private static final String SUBMODULE = "dependency";
    private static final String RENAMED_SUBMODULE = "renamed-dependency";
    private static final String SOURCE_FILE = "source.txt";
    private static final ObjectId OLD_SUBMODULE_COMMIT =
            ObjectId.fromString("1111111111111111111111111111111111111111");
    private static final ObjectId NEW_SUBMODULE_COMMIT =
            ObjectId.fromString("2222222222222222222222222222222222222222");

    @TempDir
    private Path directory;

    @ParameterizedTest
    @EnumSource(
            value = FileEditType.class,
            names = {"ADD", "MODIFY", "DELETE", "RENAME"})
    void shouldHandleSubmoduleChanges(final FileEditType type) throws IOException {
        verifySubmoduleChange(type, false);
    }

    @ParameterizedTest
    @EnumSource(
            value = FileEditType.class,
            names = {"ADD", "MODIFY", "DELETE", "RENAME"})
    void shouldPreserveSourceChangesAlongsideSubmoduleChanges(final FileEditType type) throws IOException {
        verifySubmoduleChange(type, true);
    }

    private void verifySubmoduleChange(final FileEditType type, final boolean changeSource) throws IOException {
        try (var repository = new RepositoryBuilder()
                .setGitDir(directory.resolve(".git").toFile())
                .build()) {
            repository.create();
            var reference = createCommit(
                    repository, null, type == FileEditType.ADD ? null : SUBMODULE, OLD_SUBMODULE_COMMIT, "before\n");
            var newSubmodulePath =
                    switch (type) {
                        case DELETE -> null;
                        case RENAME -> RENAMED_SUBMODULE;
                        default -> SUBMODULE;
                    };
            var current = createCommit(
                    repository,
                    reference,
                    newSubmodulePath,
                    type == FileEditType.MODIFY ? NEW_SUBMODULE_COMMIT : OLD_SUBMODULE_COMMIT,
                    changeSource ? "after\n" : "before\n");

            assertThat(repository.getObjectDatabase().has(OLD_SUBMODULE_COMMIT)).isFalse();
            assertThat(repository.getObjectDatabase().has(NEW_SUBMODULE_COMMIT)).isFalse();

            var wrapped = new DeltaRepositoryCallback(current.name(), reference.name()).invoke(repository, null);
            var delta = (GitDelta) wrapped.getResult();

            assertThat(wrapped.getErrorMessages()).isEmpty();
            assertThat(delta.getDiffFile()).contains("diff --git a/" + SUBMODULE);
            if (changeSource) {
                assertThat(delta.getFileChangesMap().values()).singleElement().satisfies(file -> {
                    assertThat(file.getFileName()).isEqualTo(SOURCE_FILE);
                    assertThat(file.getFileContent()).isEqualTo("after\n");
                    assertThat(file.getFileEditType()).isEqualTo(FileEditType.MODIFY);
                    assertThat(file.getModifiedLines()).containsExactly(1);
                });
                assertThat(delta.getDiffFile()).contains("-before", "+after");
            } else {
                assertThat(delta.getFileChangesMap()).isEmpty();
            }
        }
    }

    private ObjectId createCommit(
            final Repository repository,
            final ObjectId parent,
            final String submodulePath,
            final ObjectId submoduleCommit,
            final String content)
            throws IOException {
        try (var inserter = repository.newObjectInserter()) {
            var cache = DirCache.newInCore();
            var builder = cache.builder();
            if (submodulePath != null) {
                var submodule = new DirCacheEntry(submodulePath);
                submodule.setFileMode(FileMode.GITLINK);
                submodule.setObjectId(submoduleCommit);
                builder.add(submodule);
            }
            var source = new DirCacheEntry(SOURCE_FILE);
            source.setFileMode(FileMode.REGULAR_FILE);
            source.setObjectId(inserter.insert(Constants.OBJ_BLOB, content.getBytes(StandardCharsets.UTF_8)));
            builder.add(source);
            builder.finish();

            var commit = new CommitBuilder();
            commit.setTreeId(cache.writeTree(inserter));
            if (parent != null) {
                commit.setParentId(parent);
            }
            var author = new PersonIdent("Test", "test@example.invalid");
            commit.setAuthor(author);
            commit.setCommitter(author);
            commit.setMessage("Submodule regression fixture");
            var id = inserter.insert(commit);
            inserter.flush();
            return id;
        }
    }
}
