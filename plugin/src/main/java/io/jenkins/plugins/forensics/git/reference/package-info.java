/**
 *  Provides classes to discover reference builds in Git projects.
 *
 * <p>
 * Several plugins that report build statistics (test results, code coverage, metrics, static analysis warnings)
 * typically show their reports in two different ways: either as absolute report (e.g., total number of tests or
 * warnings, overall code coverage) or as relative delta report (e.g., additional tests, increased or decreased
 * coverage, new or fixed warnings). To compute a relative delta report, a plugin needs to carefully select the
 * other build to compare the current results to (a so-called reference build). For simple Jenkins jobs that build the
 * main branch of an SCM, the reference build will be selected from one of the previous builds of the same job. For more
 * complex branch source projects (i.e., projects that build several branches and pull requests in a connected job
 * hierarchy) it makes more sense to select a reference build from a job that builds the actual target branch (i.e., the
 * branch the current changes will be merged into). Here one typically is interested what changed in a branch or pull
 * request with respect to the main branch (or any other target branch): e.g., how will the code coverage change if the
 * team merges the changes. Selecting the correct reference build is not that easy, since the main branch of a project
 * will evolve more frequently than a specific feature or bugfix branch.
 * </p>
 */
@DefaultAnnotation(NonNull.class)
package io.jenkins.plugins.forensics.git.reference;

import edu.umd.cs.findbugs.annotations.DefaultAnnotation;
import edu.umd.cs.findbugs.annotations.NonNull;
