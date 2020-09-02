# Git Forensics Jenkins Plugin

[![Join the chat at https://gitter.im/jenkinsci/warnings-plugin](https://badges.gitter.im/jenkinsci/warnings-plugin.svg)](https://gitter.im/jenkinsci/warnings-plugin?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
[![Jenkins Plugin](https://img.shields.io/jenkins/plugin/v/git-forensics.svg?label=latest%20version)](https://plugins.jenkins.io/git-forensics)
[![Jenkins](https://ci.jenkins.io/job/Plugins/job/git-forensics-plugin/job/master/badge/icon?subject=Jenkins%20CI)](https://ci.jenkins.io/job/Plugins/job/git-forensics-plugin/job/master/)
[![GitHub Actions](https://github.com/jenkinsci/git-forensics-plugin/workflows/GitHub%20CI/badge.svg?branch=master)](https://github.com/jenkinsci/git-forensics-plugin/actions)
[![Codacy Badge](https://api.codacy.com/project/badge/Grade/1999b59401394431a1c2fea2923a919d)](https://www.codacy.com/app/uhafner/git-forensics-plugin?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=jenkinsci/git-forensics-plugin&amp;utm_campaign=Badge_Grade)
[![Codecov](https://codecov.io/gh/jenkinsci/git-forensics-plugin/branch/master/graph/badge.svg)](https://codecov.io/gh/jenkinsci/git-forensics-plugin)

This Git Forensics Jenkins plugin mines and analyzes data from a Git repository. It implements all extension points of
[Jenkins' Forensics API Plugin](https://github.com/jenkinsci/forensics-api-plugin).

The following services are provided by this plugin:
- **Blames**: Shows what revision and author last modified a specified set of lines of a file. This information can be 
used to track the original commit that introduced a piece of code. 
- **File statistics**: Collects commit statistics for all repository files in the style of 
  [Code as a Crime Scene](https://www.adamtornhill.com/articles/crimescene/codeascrimescene.htm) 
  \[Adam Tornhill, November 2013\]:
  - total number of commits
  - total number of different authors
  - creation time
  - last modification time
- **Commit tracking**: Tracks all new commits that are part of a build. Using this information plugins can search for 
 builds that contain a specific commit. 
- **Reference build**: Several plugins that report build statistics (test results, code coverage, metrics, static 
analysis warnings) typically show their reports in two different ways: either as absolute report 
(e.g., total number of tests or warnings, overall code coverage) or as relative delta report (e.g., additional tests,
increased or decreased coverage, new or fixed warnings). In order to compute a relative delta report a plugin needs 
to carefully select the other build to compare the current results to (a so called *reference build*). 
For simple Jenkins jobs that build the main branch of an SCM the reference build will be selected from one of the 
previous builds of the same job. For more complex branch source projects (i.e., projects that build several branches 
and pull requests in a connected job hierarchy) it makes more sense to select a reference build from a job 
that builds the actual target branch (i.e., the branch the current changes will be merged into). Here one typically is
interested what changed in a branch or pull request with respect to the main branch (or any other 
target branch): e.g., how will the code coverage change if the team merges the changes. Selecting the correct reference
build is not that easy, since the main branch of a project will evolve more frequently than a specific feature or bugfix
branch.   
