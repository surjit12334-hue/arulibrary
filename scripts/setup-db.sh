#!/usr/bin/env bash
# Optional helper: initialise and start a throwaway local PostgreSQL cluster.
# Requires PostgreSQL server binaries (initdb, pg_ctl, psql) to be installed.
set -euo pipefail
cd "$(dirname "$0")/.."

PG_BIN="$(pg_config --bindir 2>/dev/null || echo /usr/lib/postgresql/18/bin)"
PGDATA="${PGDATA:-$PWD/pgdata}"
PGPORT="${PGPORT:-5432}"
PGUSER_LOCAL="${PGUSER:-postgres}"
PGPASS_LOCAL="${PGPASSWORD_LOCAL:-postgres}"

if [ ! -x "$PG_BIN/initdb" ]; then
    echo "ERROR: PostgreSQL server binaries not found in $PG_BIN" >&2
    exit 1
fi

if [ ! -f "$PGDATA/PG_VERSION" ]; then
    echo "Initialising PostgreSQL cluster at $PGDATA ..."
    PWFILE="${PGDATA}.pgpass"
    printf '%s' "$PGPASS_LOCAL" > "$PWFILE"
    chmod 600 "$PWFILE"
    rm -rf "$PGDATA"
    "$PG_BIN/initdb" -D "$PGDATA" -U "$PGUSER_LOCAL" \
        --auth-local=trust --auth-host=scram-sha-256 --pwfile="$PWFILE" >/dev/null
    rm -f "$PWFILE"
fi

mkdir -p "$PGDATA/sock"
if ! "$PG_BIN/pg_isready" -h 127.0.0.1 -p "$PGPORT" -q >/dev/null 2>&1; then
    echo "Starting PostgreSQL on 127.0.0.1:$PGPORT ..."
    "$PG_BIN/pg_ctl" -D "$PGDATA" \
        -o "-h 127.0.0.1 -p $PGPORT -k $PGDATA/sock" \
        -l "$PGDATA/server.log" -w start
fi

for db in arulibrary_db arulibrary_test_db; do
    if ! PGPASSWORD="$PGPASS_LOCAL" "$PG_BIN/psql" -h 127.0.0.1 -p "$PGPORT" \
            -U "$PGUSER_LOCAL" -tc "SELECT 1 FROM pg_database WHERE datname='$db'" | grep -q 1; then
        echo "Creating database $db ..."
        PGPASSWORD="$PGPASS_LOCAL" "$PG_BIN/createdb" -h 127.0.0.1 -p "$PGPORT" \
            -U "$PGUSER_LOCAL" "$db"
    fi
done

echo "PostgreSQL ready. Databases: arulibrary_db, arulibrary_test_db"
