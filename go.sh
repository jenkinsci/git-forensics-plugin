#!/bin/bash

(cd plugin; mvn clean install|| { echo "Build failed"; exit 1; })

$(dirname "$0")/deploy.sh git-forensics

