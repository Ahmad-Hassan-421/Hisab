#!/bin/sh
set -eu

attempt=1
while [ "$attempt" -le 3 ]; do
    if sh ./gradlew --no-daemon installDist > /tmp/hisab-gradle.log 2>&1; then
        cat /tmp/hisab-gradle.log
        exit 0
    fi
    cat /tmp/hisab-gradle.log
    if [ "$attempt" -eq 3 ] || ! grep -qE 'Received status code (429|5[0-9]{2})' /tmp/hisab-gradle.log; then
        exit 1
    fi
    sleep "$((attempt * 15))"
    attempt=$((attempt + 1))
done
