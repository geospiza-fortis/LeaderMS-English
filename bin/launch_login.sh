#!/usr/bin/env bash

cd "$(dirname "${BASH_SOURCE[0]}")"/..
export MAVEN_OPTS="-Xmx600m"
mvn exec:java -Dexec.mainClass="handling.login.LoginServer" \
    -Dwzpath=wz/ \
    -Djava.util.logging.config.file=conf/logging.properties \
    -Ddb.config=conf/db.properties \
    -Dcom.sun.management.jmxremote.local.only=false \
    -Dcom.sun.management.jmxremote.ssl=false \
    -Dcom.sun.management.jmxremote.authenticate=false \
    -Dcom.sun.management.jmxremote.port=1098 \
    -Dcom.sun.management.jmxremote.rmi.port=1098 \
    -Djava.rmi.server.hostname=world \
    -Dlogin.config=conf/login.properties
