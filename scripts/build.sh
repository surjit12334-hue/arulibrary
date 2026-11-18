#!/usr/bin/env bash
# Compile the application without Maven (only a JDK + the JDBC driver are needed).
set -euo pipefail
cd "$(dirname "$0")/.."

JAVAC="${JAVAC:-javac}"
JAR="lib/postgresql-42.7.4.jar"

if [ ! -f "$JAR" ]; then
    echo "Downloading PostgreSQL JDBC driver..."
    mkdir -p lib
    curl -fsSL -o "$JAR" \
        "https://repo1.maven.org/maven2/org/postgresql/postgresql/42.7.4/postgresql-42.7.4.jar"
fi

rm -rf out
mkdir -p out
find src/main/java -name '*.java' -print0 | xargs -0 "$JAVAC" -encoding UTF-8 -cp "$JAR" -d out
echo "Build complete -> out/"
