package io.jenkins.plugins.git.forensics.miner;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;

/**
 * Collects all files of a Git repository.
 *
 * @author Ullrich Hafner
 */
public class FilesCollector {
    private final Repository repository;

    FilesCollector(final Repository repository) {
        this.repository = repository;
    }

    Set<String> findAllFor(final ObjectId commitId) {
        try {
            RevWalk revWalk = new RevWalk(repository);
            RevTree tree = revWalk.parseCommit(commitId).getTree();
            TreeWalk treeWalk = new TreeWalk(repository);
            treeWalk.setRecursive(true);
            treeWalk.addTree(tree);
            Set<String> files = new HashSet<>();
            while (treeWalk.next()) {
                files.add(treeWalk.getPathString());
            }
            return files;
        }
        catch (IOException exception) { // FIXME: add logging
            return Collections.emptySet();
        }
    }
}
