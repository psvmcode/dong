#!/usr/bin/env bash

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SRC="$ROOT/db/schema.sql"
MAIN_OUT="$ROOT/deploy/initdb/01-schema.sql"
REPLICA_OUT="$ROOT/deploy/initdb-replica/01-schema.sql"

if [[ ! -f "$SRC" ]]; then
    echo "schema not found: $SRC" >&2
    exit 1
fi

TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

awk -v main="$TMP_DIR/main.sql" -v replica="$TMP_DIR/replica.sql" '
    BEGIN { db = "" }
    /^[[:space:]]*create[[:space:]]+database/ { next }
    /^[[:space:]]*use[[:space:]]+/ {
        db = $0
        sub(/^[[:space:]]*use[[:space:]]+/, "", db)
        sub(/[[:space:]]*;.*$/, "", db)
        next
    }
    db == "dong_lab"         { print > main }
    db == "dong_lab_replica" { print > replica }
' "$SRC"

tidy() {
    awk '
        /^[[:space:]]*$/ { blank++; next }
        {
            if (printed > 0 && blank > 0) {
                print ""
            }
            blank = 0
            print
            printed++
        }
    ' "$1" > "$2"
}

tidy "$TMP_DIR/main.sql" "$MAIN_OUT"
tidy "$TMP_DIR/replica.sql" "$REPLICA_OUT"

echo "generated from db/schema.sql:"
echo "  $MAIN_OUT    $(grep -c '^create table' "$MAIN_OUT") tables"
echo "  $REPLICA_OUT $(grep -c '^create table' "$REPLICA_OUT") tables"
