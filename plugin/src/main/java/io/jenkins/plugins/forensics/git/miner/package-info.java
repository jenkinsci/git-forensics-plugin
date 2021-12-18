/**
 * Provides API classes to obtain commit statistics for files in a repository.
 * <p>
 * For pull requests (or more generally: for jobs that have a reference build defined) the classes in this package
 * collect a statistical summary for all containing commits. This includes the commits count, the changed files count,
 * and the added and deleted lines in those commits.
 * </p>
 *
 * <p>
 * Additionally, the classes in this package will collect commit statistics for all repository files in the style of
 * the book "Code as a Crime Scene":
 * </p>
 * <ul>
 *     <li> commits count </li>
 *     <li> different authors count </li>
 *     <li> creation time </li>
 *     <li> last modification time </li>
 *     <li> lines of code (from the commit details) </li>
 *     <li> code churn (changed lines since created) </li>
 * </ul>
 */
@DefaultAnnotation(NonNull.class)
package io.jenkins.plugins.forensics.git.miner;

import edu.umd.cs.findbugs.annotations.DefaultAnnotation;
import edu.umd.cs.findbugs.annotations.NonNull;
