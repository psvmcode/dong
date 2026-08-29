#!/usr/bin/env bash

set -euo pipefail

url_encode() {
    local raw="$1"
    local out=""
    local i
    local c
    for ((i = 0; i < ${#raw}; i++)); do
        c="${raw:i:1}"
        case "${c}" in
            [a-zA-Z0-9._~-]) out+="${c}" ;;
            '%') out+="%25" ;;
            '@') out+="%40" ;;
            ':') out+="%3A" ;;
            '/') out+="%2F" ;;
            '?') out+="%3F" ;;
            '#') out+="%23" ;;
            '&') out+="%26" ;;
            '=') out+="%3D" ;;
            ' ') out+="%20" ;;
            '+') out+="%2B" ;;
            '$') out+="%24" ;;
            '!') out+="%21" ;;
            '*') out+="%2A" ;;
            "'") out+="%27" ;;
            '(') out+="%28" ;;
            ')') out+="%29" ;;
            ';') out+="%3B" ;;
            ',') out+="%2C" ;;
            *) out+="$(printf '%%%02X' "'${c}")" ;;
        esac
    done
    printf '%s' "${out}"
}

prompt() {
    local label="$1"
    local secret="$2"
    local fallback="$3"
    local value=""
    if [[ "${secret}" == "true" ]]; then
        read -r -s -p "${label}: " value
        echo >&2
    else
        read -r -p "${label} [${fallback}]: " value
    fi
    value="${value%$'\r'}"
    value="${value%$'\n'}"
    printf '%s' "${value:-${fallback}}"
}

require() {
    if [[ -z "${2:-}" ]]; then
        echo "${1} is required" >&2
        exit 1
    fi
}

echo "enter the deployment values, press enter to accept the default" >&2
echo "nothing is written to disk, the command is printed at the end" >&2
echo >&2

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

MONGO_ENCODED="$(url_encode "${LAB_MONGO_PASSWORD}")"

cat <<OUTPUT
cat > /opt/dong-lab/.env <<'LABENV'
LAB_PUBLIC_HOST='${LAB_PUBLIC_HOST}'

LAB_MYSQL_USERNAME='${LAB_MYSQL_USERNAME}'
LAB_MYSQL_PASSWORD='${LAB_MYSQL_PASSWORD}'

LAB_REDIS_PASSWORD='${LAB_REDIS_PASSWORD}'

LAB_MARIADB_USERNAME='${LAB_MARIADB_USERNAME}'
LAB_MARIADB_PASSWORD='${LAB_MARIADB_PASSWORD}'

LAB_ES_PASSWORD='${LAB_ES_PASSWORD}'

LAB_MONGO_USERNAME='${LAB_MONGO_USERNAME}'
LAB_MONGO_PASSWORD='${MONGO_ENCODED}'
LABENV
chmod 600 /opt/dong-lab/.env
OUTPUT

if [[ "${MONGO_ENCODED}" != "${LAB_MONGO_PASSWORD}" ]]; then
    echo "note: LAB_MONGO_PASSWORD was url encoded, @ became %40, the mongo uri requires it" >&2
fi

echo "verify with: cd /opt/dong-lab && docker compose --profile core config > /dev/null && echo ok" >&2
