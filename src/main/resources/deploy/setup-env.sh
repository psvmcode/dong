#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="${SCRIPT_DIR}/.env"
TEMPLATE="${SCRIPT_DIR}/.env.example"

if [[ ! -f "${TEMPLATE}" ]]; then
    echo "template not found: ${TEMPLATE}" >&2
    exit 1
fi

if [[ -f "${ENV_FILE}" ]]; then
    read -r -p ".env already exists, overwrite? [y/N] " reply
    if [[ "${reply}" != "y" && "${reply}" != "Y" ]]; then
        echo "aborted"
        exit 0
    fi
fi

prompt() {
    local label="$1"
    local secret="$2"
    local fallback="$3"
    local value=""
    if [[ "${secret}" == "true" ]]; then
        read -r -s -p "${label}: " value
        echo
    else
        read -r -p "${label} [${fallback}]: " value
    fi
    printf '%s' "${value:-${fallback}}"
}

require() {
    local name="$1"
    local value="$2"
    if [[ -z "${value}" ]]; then
        echo "${name} is required" >&2
        exit 1
    fi
}

echo "enter deployment values, press enter to accept the default"
echo

LAB_PUBLIC_HOST="$(prompt "LAB_PUBLIC_HOST" "false" "127.0.0.1")"
LAB_MYSQL_USERNAME="$(prompt "LAB_MYSQL_USERNAME" "false" "root")"
LAB_MYSQL_PASSWORD="$(prompt "LAB_MYSQL_PASSWORD" "true" "")"
LAB_REDIS_PASSWORD="$(prompt "LAB_REDIS_PASSWORD" "true" "")"
LAB_MARIADB_USERNAME="$(prompt "LAB_MARIADB_USERNAME" "false" "root")"
LAB_MARIADB_PASSWORD="$(prompt "LAB_MARIADB_PASSWORD" "true" "")"
LAB_ES_PASSWORD="$(prompt "LAB_ES_PASSWORD" "true" "")"
LAB_MONGO_USERNAME="$(prompt "LAB_MONGO_USERNAME" "false" "dong")"
LAB_MONGO_PASSWORD="$(prompt "LAB_MONGO_PASSWORD" "true" "")"

require "LAB_MYSQL_PASSWORD" "${LAB_MYSQL_PASSWORD}"
require "LAB_REDIS_PASSWORD" "${LAB_REDIS_PASSWORD}"
require "LAB_MARIADB_PASSWORD" "${LAB_MARIADB_PASSWORD}"
require "LAB_ES_PASSWORD" "${LAB_ES_PASSWORD}"
require "LAB_MONGO_PASSWORD" "${LAB_MONGO_PASSWORD}"

cat > "${ENV_FILE}" <<EOF
LAB_PUBLIC_HOST=${LAB_PUBLIC_HOST}

LAB_MYSQL_USERNAME=${LAB_MYSQL_USERNAME}
LAB_MYSQL_PASSWORD=${LAB_MYSQL_PASSWORD}

LAB_REDIS_PASSWORD=${LAB_REDIS_PASSWORD}

LAB_MARIADB_USERNAME=${LAB_MARIADB_USERNAME}
LAB_MARIADB_PASSWORD=${LAB_MARIADB_PASSWORD}

LAB_ES_PASSWORD=${LAB_ES_PASSWORD}

LAB_MONGO_USERNAME=${LAB_MONGO_USERNAME}
LAB_MONGO_PASSWORD=${LAB_MONGO_PASSWORD}
EOF

chmod 600 "${ENV_FILE}"

echo
echo "written to ${ENV_FILE} with mode 600"
echo "note: passwords are url encoded where needed, @ becomes %40"
