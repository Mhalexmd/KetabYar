#!/usr/bin/env bash
set -e
cd "$(dirname "$0")"
mkdir -p out
ls lib/sqlite-jdbc-*.jar >/dev/null 2>&1 || { echo "SQLite JDBC driver not found in lib/. See README.md."; exit 1; }
find src/main/java -name '*.java' -print0 | xargs -0 javac -encoding UTF-8 -cp "lib/*" -d out
java -cp "out:lib/*" com.example.library.Main "$@"
