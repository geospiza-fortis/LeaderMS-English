#!/usr/bin/env bash

cd "$(dirname "${BASH_SOURCE[0]}")"/..
export MAVEN_OPTS="-Xmx600m"
mvn exec:java -Dexec.mainClass="handling.login.LoginServer" \
    -Drecvops=recvops.properties \
    -Dsendops=sendops.properties \
    -Dwzpath=wz/ \
    -Dlogin.config=conf/login.properties
