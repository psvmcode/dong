#!/bin/bash

COMPOSE_FILE="/opt/dong-lab/docker-compose.yml"

usage() {
    cat <<EOF
usage: lab.sh <profile> [command]

profiles:
  core      mysql + redis                            about 850m
  mq        kafka + rocketmq                          about 1.1g
  search    elasticsearch                             about 900m
  doc       mongodb                                   about 520m
  replica   mariadb                                   about 390m
  full      everything                                not recommended on 2g

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
