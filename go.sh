#!/bin/bash

if [[ -z "$JENKINS_HOME" ]]; then
    JENKINS_HOME=../warnings-ng-plugin-devenv/docker/volumes/jenkins-home
    echo "JENKINS_HOME is not defined, using $JENKINS_HOME"
fi

mvn install || { echo "Build failed"; exit 1; }

echo "Installing plugin in $JENKINS_HOME"
rm -rf $JENKINS_HOME/plugins/git-forensics*
cp -fv target/git-forensics.hpi $JENKINS_HOME/plugins/git-forensics.jpi

cd ../warnings-ng-plugin-devenv

IS_RUNNING=`docker-compose ps -q jenkins-master`
if [[ "$IS_RUNNING" != "" ]]; then
    echo "Restarting Jenkins..."
    docker-compose restart
fi

