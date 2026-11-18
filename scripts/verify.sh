#!/usr/bin/env bash
# One-command check: build, then run the automated end-to-end self test.
# The self test uses library_test_db so your library_db data is untouched.
set -euo pipefail
cd "$(dirname "$0")/.."

./scripts/build.sh
DB_URL="${DB_URL:-jdbc:postgresql://127.0.0.1:5432/library_test_db}" ./scripts/run.sh --self-check
