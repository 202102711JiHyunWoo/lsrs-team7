#!/usr/bin/env sh
set -eu
cd "$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
jar=dist/lsrs-sprint1.jar
[ -f "$jar" ] || jar=build/libs/lsrs-sprint1.jar
[ -f "$jar" ] || { echo 'Run ./gradlew clean test bootJar first.'; exit 1; }
echo 'LSRS local: http://localhost:8080 | real clock, sign up first'
echo 'Development mailbox: http://localhost:8080/dev/mailbox'
exec java -jar "$jar" --spring.profiles.active=local
