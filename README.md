# Git Forensics Jenkins Plugin

[![Join the chat at https://gitter.im/jenkinsci/warnings-plugin](https://badges.gitter.im/jenkinsci/warnings-plugin.svg)](https://gitter.im/jenkinsci/warnings-plugin?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
[![Jenkins Version](https://img.shields.io/badge/Jenkins-2.121.1-green.svg)](https://jenkins.io/download/)
![JDK8](https://img.shields.io/badge/jdk-8-yellow.svg)
[![License: MIT](https://img.shields.io/badge/license-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![GitHub pull requests](https://img.shields.io/github/issues-pr/jenkinsci/git-forensics-plugin.svg)](https://github.com/jenkinsci/git-forensics-plugin/pulls)

This Git Forensics Jenkins plugin mines and analyzes data from a Git repository. It implements all extension points of
[Jenkins' Forensics API Plugin](https://github.com/jenkinsci/forensics-api-plugin).

The following services are provided by this plugin:
- **Blames**: Shows what revision and author last modified a specified set of lines of a file.
- **File statistics**: Collects commit statistics for all repository files:
    - total number of commits
    - total number of different authors
    - creation time
    - last modification time


[![Travis](https://img.shields.io/travis/jenkinsci/git-forensics-plugin.svg?logo=travis&label=travis%20build&logoColor=white)](https://travis-ci.org/jenkinsci/git-forensics-plugin)
[![Codacy](https://api.codacy.com/project/badge/Grade/6f1e586841f7419bb40973862c8871aa)](https://www.codacy.com/app/jenkinsci/git-forensics-plugin?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=jenkinsci/git-forensics-plugin&amp;utm_campaign=Badge_Grade)
[![Codecov](https://img.shields.io/codecov/c/github/jenkinsci/git-forensics-plugin.svg)](https://codecov.io/gh/jenkinsci/git-forensics-plugin)
