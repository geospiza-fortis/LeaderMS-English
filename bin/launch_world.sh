#!/usr/bin/env bash

set -e
cd "$(dirname "${BASH_SOURCE[0]}")"/..
mvn exec:exec -Dexec.executable="java" \
    -Dexec.workingdir=$PWD \
    -Dexec.args="-cp %classpath:target/ \
        $(if [[ $DEBUG == true ]]; then echo -Xdebug -Xrunjdwp:transport=dt_socket,address=8000,server=y,suspend=y; fi) \
        -Xmx600m \
        -Dwzpath=wz/ \
        -Djava.util.logging.config.file=conf/logging.properties \
        -Ddb.config=conf/db.properties \
        -Dworld.config=conf/world.properties \
        -Dlogin.config=conf/login.properties \
        -Dchannel.config=conf/channel.properties \
        handling.world.WorldServer"
