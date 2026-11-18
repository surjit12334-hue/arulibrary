#!/usr/bin/env bash
# Convenience launcher for the browser interface.
# Usage: ./scripts/web.sh [port]      (default: 8080, or $WEB_PORT)
set -euo pipefail
cd "$(dirname "$0")/.."

if [ ! -d out/com/library ]; then
    ./scripts/build.sh
fi

PORT="${1:-${WEB_PORT:-8080}}"
echo "Starting Library Management System web interface..."
exec java -cp "out:lib/*" com.library.Main --web "$PORT"
