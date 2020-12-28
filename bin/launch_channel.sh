#!/usr/bin/env bash

cd "$(dirname "${BASH_SOURCE[0]}")"/..
export MAVEN_OPTS="-Xmx600m"
mvn exec:java -Dexec.mainClass="handling.channel.ChannelServer" \
    -Dwzpath=wz/ \
    -Ddb.config=conf/db.properties \
    -Dchannel.config=conf/channel.properties
