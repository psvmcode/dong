#!/bin/bash

COMPOSE_FILE="/opt/dong-lab/docker-compose.yml"

usage() {
    cat <<EOF
usage: lab.sh <profile> [command]

profiles:
  core      mysql + redis                            about 450m
  mq        rocketmq (namesrv + broker)               about 640m
  kafka     kafka (kraft, image pulled on demand)     about 510m
  search    elasticsearch                             about 800m
  doc       mongodb                                   about 400m
  replica   mariadb                                   about 260m
  full      everything                                about 3g, not recommended on 2g

commands:
  up        start the profile
  down      stop the profile
  ps        show status
  logs      tail logs

examples:
  lab.sh core up
  lab.sh mq up
  lab.sh core down
EOF
}

if [ $# -lt 2 ]; then
    usage
    exit 1
fi

PROFILE=$1
COMMAND=$2

export COMPOSE_PROFILES="${PROFILE}"

case "${COMMAND}" in
up)
    docker compose -f "${COMPOSE_FILE}" --profile "${PROFILE}" up -d
    ;;
down)
    docker compose -f "${COMPOSE_FILE}" --profile "${PROFILE}" down
    ;;
ps)
    docker compose -f "${COMPOSE_FILE}" --profile "${PROFILE}" ps
    ;;
logs)
    docker compose -f "${COMPOSE_FILE}" --profile "${PROFILE}" logs -f --tail=100
    ;;
*)
    usage
    exit 1
    ;;
esac
