#!/usr/bin/env bash

set -ex
cd "$(dirname "${BASH_SOURCE[0]}")"/..

bin/run_sql.sh <sql/leaderms.sql
for file in sql/data/*; do
    bin/run_sql.sh <$file
done
