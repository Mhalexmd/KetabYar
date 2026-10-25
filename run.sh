#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p out
compgen -G 'lib/sqlite-jdbc-*.jar' >/dev/null || { echo 'SQLite JDBC driver is missing from lib/.'; exit 1; }
find src/main/java -name '*.java' -print0 | xargs -0 javac --release 17 -encoding UTF-8 -cp 'lib/*' -d out
mode="${1:-desktop}"
if [ "$#" -gt 0 ]; then shift; fi
case "$mode" in
  desktop) java -cp 'out:lib/*' com.example.library.Main "$@" ;;
  api) java -cp 'out:lib/*' com.example.library.ApiMain "$@" ;;
  build) echo 'SoftwareEngineeing compiled successfully.' ;;
  test)
    find src/test/java -name '*.java' -print0 | xargs -0 javac --release 17 -encoding UTF-8 -cp 'out:lib/*' -d out
    java -Djava.awt.headless=true -cp 'out:lib/*' com.example.library.LibraryIntegrationTest "$@" ;;
  *) echo 'Usage: bash run.sh [desktop|api|build|test] [database] [port]'; exit 1 ;;
esac
