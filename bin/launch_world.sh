#!/usr/bin/env bash

cd "$(dirname "${BASH_SOURCE[0]}")"/..
export MAVEN_OPTS="-Xmx600m"
mvn exec:java -Dexec.mainClass="handling.world.WorldServer" \
    -Dwzpath=wz/ \
    -Djava.util.logging.config.file=conf/logging.properties \
    -Ddb.config=conf/db.properties \
    -Dlogin.config=conf/world.properties
