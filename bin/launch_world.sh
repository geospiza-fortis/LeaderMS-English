#!/usr/bin/env bash

cd "$(dirname "${BASH_SOURCE[0]}")"/..
export MAVEN_OPTS="-Xmx600m"
mvn exec:java -Dexec.mainClass="handling.world.WorldServer" \
    -Dwzpath=wz/ \
    -Ddb.config=conf/db.properties \
    -Dlogin.config=conf/world.properties
