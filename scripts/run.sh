#!/usr/bin/env bash
# Run the application. Any arguments are passed through to com.library.Main.
set -euo pipefail
cd "$(dirname "$0")/.."

if [ ! -d out/com/library ]; then
    ./scripts/build.sh
fi

exec java -cp "out:lib/*" com.library.Main "$@"
