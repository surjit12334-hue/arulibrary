#!/usr/bin/env bash
# Viva demo launcher: build -> reset sample data -> prove with 24 checks -> interactive app.
set -euo pipefail
cd "$(dirname "$0")/.."

# Make sure a JDK is reachable even if PATH was not prepared.
if ! command -v javac >/dev/null 2>&1; then
    for d in /tmp/opencode/tools/jdk-*/bin /usr/lib/jvm/java-*/bin /usr/lib/jvm/default-java/bin; do
        if [ -x "$d/javac" ]; then export PATH="$d:$PATH"; break; fi
    done
fi
if ! command -v java >/dev/null 2>&1; then
    for d in /tmp/opencode/tools/jdk-*/bin /usr/lib/jvm/java-*/bin /usr/lib/jvm/default-java/bin; do
        if [ -x "$d/java" ]; then export PATH="$d:$PATH"; break; fi
    done
fi
if ! command -v javac >/dev/null 2>&1; then
    echo "ERROR: javac not found. Run: export PATH=/tmp/opencode/tools/jdk-25.0.5+8/bin:\$PATH" >&2
    exit 1
fi

# Make PostgreSQL client tools reachable if needed.
if ! command -v psql >/dev/null 2>&1; then
    for d in /usr/lib/postgresql/*/bin; do
        if [ -x "$d/psql" ]; then export PATH="$d:$PATH"; break; fi
    done
fi

echo "========================================================="
echo "   ARULIBRARY - VIVA DEMO"
echo "========================================================="
echo
echo "[1/3] Building the application ..."
./scripts/build.sh
echo
echo "[2/3] Loading clean sample data into arulibrary_db ..."
./scripts/run.sh --setup
echo
echo "[3/3] Running the automated verification (24 checks) ..."
./scripts/verify.sh
echo
echo "========================================================="
echo "   Automated proof complete. Starting interactive app."
echo "   Use VIVA_DEMO.md for the exact keystrokes to type."
echo "========================================================="
echo
exec ./scripts/run.sh
