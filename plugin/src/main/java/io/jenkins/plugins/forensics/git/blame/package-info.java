/**
 * Provides classes to run `git blame`. Using this service, plugins can show in which Git revisions the lines of a file
 * have been modified by what authors. This information can be used to discover the original commit that is the origin
 *  of a problematic code snippet.
 */
@DefaultAnnotation(NonNull.class)
package io.jenkins.plugins.forensics.git.blame;

import edu.umd.cs.findbugs.annotations.DefaultAnnotation;
import edu.umd.cs.findbugs.annotations.NonNull;
