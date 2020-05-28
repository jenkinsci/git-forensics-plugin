# Git Forensics Jenkins Plugin

[![Join the chat at https://gitter.im/jenkinsci/warnings-plugin](https://badges.gitter.im/jenkinsci/warnings-plugin.svg)](https://gitter.im/jenkinsci/warnings-plugin?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
[![Jenkins Plugin](https://img.shields.io/jenkins/plugin/v/git-forensics.svg)](https://plugins.jenkins.io/git-forensics)
[![Jenkins Version](https://img.shields.io/badge/Jenkins-2.204.4-green.svg?label=min.%20Jenkins)](https://jenkins.io/download/)
![JDK8](https://img.shields.io/badge/jdk-8-yellow.svg?label=min.%20JDK)
[![License: MIT](https://img.shields.io/badge/license-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Jenkins](https://ci.jenkins.io/job/Plugins/job/git-forensics-plugin/job/master/badge/icon)](https://ci.jenkins.io/job/Plugins/job/git-forensics-plugin/job/master/)
[![GitHub Actions](https://github.com/jenkinsci/git-forensics-plugin/workflows/CI%20on%20all%20platforms/badge.svg?branch=master)](https://github.com/jenkinsci/git-forensics-plugin/actions)
[![Codacy Badge](https://api.codacy.com/project/badge/Grade/1999b59401394431a1c2fea2923a919d)](https://www.codacy.com/app/uhafner/git-forensics-plugin?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=jenkinsci/git-forensics-plugin&amp;utm_campaign=Badge_Grade)
[![Codecov](https://codecov.io/gh/jenkinsci/git-forensics-plugin/branch/master/graph/badge.svg)](https://codecov.io/gh/jenkinsci/git-forensics-plugin)
[![GitHub pull requests](https://img.shields.io/github/issues-pr/jenkinsci/git-forensics-plugin.svg)](https://github.com/jenkinsci/git-forensics-plugin/pulls)

This Git Forensics Jenkins plugin mines and analyzes data from a Git repository. It implements all extension points of
[Jenkins' Forensics API Plugin](https://github.com/jenkinsci/forensics-api-plugin).

The following services are provided by this plugin:
- **Blames**: Shows what revision and author last modified a specified set of lines of a file.
- **File statistics**: Collects commit statistics for repository files:
  - total number of commits
  - total number of different authors
  - creation time
  - last modification time

