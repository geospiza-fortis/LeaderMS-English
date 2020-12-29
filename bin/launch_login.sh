#!/usr/bin/env bash

set -e
cd "$(dirname "${BASH_SOURCE[0]}")"/..
mvn exec:exec -Dexec.executable="java" \
    -Dexec.workingdir=$PWD \
    -Dexec.args="-cp %classpath:target/ \
        -Xmx600m \
        -Dwzpath=wz/ \
        -Djava.util.logging.config.file=conf/logging.properties \
        -Ddb.config=conf/db.properties \
        -Dlogin.config=conf/login.properties \
        handling.login.LoginServer"
