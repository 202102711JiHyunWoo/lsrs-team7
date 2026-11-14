#!/usr/bin/env sh
set -eu
cd "$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
jar=dist/lsrs-sprint1.jar
[ -f "$jar" ] || jar=build/libs/lsrs-sprint1.jar
[ -f "$jar" ] || { echo 'Run ./gradlew clean test bootJar first.'; exit 1; }
echo 'LSRS demo: http://localhost:8080 | fixed clock: 2026-11-14 10:00 Asia/Seoul'
echo 'Student: student@example.test / Demo1234! | Admin: admin@example.test / Demo1234!'
exec java -jar "$jar" --spring.profiles.active=demo
