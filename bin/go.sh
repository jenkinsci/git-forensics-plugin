#!/bin/bash

(cd plugin; mvn clean install -DskipITs || { echo "Build failed"; exit 1; })

$(dirname "$0")/deploy.sh git-forensics
