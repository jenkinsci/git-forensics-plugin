# Git Forensics Jenkins Plugin

<!---  
[![Jenkins Plugin](https://img.shields.io/jenkins/plugin/v/analysis-model-api.svg)](https://plugins.jenkins.io/warnings)
--->
[![Join the chat at https://gitter.im/jenkinsci/warnings-plugin](https://badges.gitter.im/jenkinsci/warnings-plugin.svg)](https://gitter.im/jenkinsci/warnings-plugin?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
![JDK8](https://img.shields.io/badge/jdk-8-yellow.svg)
[![License: MIT](https://img.shields.io/badge/license-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

This Git Forensics Jenkins plug-in mines and analyzes data from a Git repository. Currently, this plugin is only used
by the [Jenkins Warning Next Generation Plugin](https://github.com/jenkinsci/warnings-ng-plugin). 

The following services are provided by this plugin:
- Git blames: Shows what revision and author last modified a specified set of lines of a file.