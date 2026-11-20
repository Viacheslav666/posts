#!/usr/bin/env bash
# Fail compose profile `full` when JVM services still target production external APIs.
set -euo pipefail

COMPOSE_FILE="${COMPOSE_FILE:-infra/docker-compose/docker-compose.yml}"
COMPOSE=(docker compose -f "${COMPOSE_FILE}" --profile full)

FORBIDDEN_HOST_RE='api\.vk\.com|id\.vk\.ru|api\.telegram\.org|platform-api\.max\.ru|platform-api2\.max\.ru|api\.ok\.ru|api\.ord\.vk\.com|api\.yookassa\.ru|oauth\.yandex\.ru|login\.yandex\.ru'

REQUIRED_EMULATOR_VARS=(
  POSTS_VK_API_BASE_URL
  POSTS_SOCIAL_VK_BASE_URL
  POSTS_SOCIAL_BASE_URL
  POSTS_SOCIAL_EMULATOR_URL
  POSTS_VK_ID_BASE_URL
  POSTS_YANDEX_OAUTH_BASE_URL
  POSTS_YOOKASSA_BASE_URL
  POSTS_ORD_VK_BASE_URL
  POSTS_TG_NOTIFY_API_BASE_URL
)

service_env_lines() {
  local svc="$1"
  "${COMPOSE[@]}" config --format json | jq -r --arg svc "$svc" '
    .services[$svc].environment // [] |
    if type == "array" then
      .[] | if type == "string" then . else "\(.key)=\(.value)" end
    else
      to_entries[] | "\(.key)=\(.value)"
    end
  '
}

echo "==> merged compose env (profile full JVM services)"
services=(
  api scheduler ingest notifications publisher-vk publisher-tg publisher-ok publisher-max
)
for svc in "${services[@]}"; do
  mapfile -t env_lines < <(service_env_lines "$svc")
  if [ "${#env_lines[@]}" -eq 0 ]; then
    echo "missing service $svc in compose config" >&2
    exit 1
  fi
  for key in "${REQUIRED_EMULATOR_VARS[@]}"; do
    found=0
    for line in "${env_lines[@]}"; do
      if [[ "$line" == "${key}=http://social-emulator:8090"* ]]; then
        found=1
        break
      fi
    done
    if [ "$found" != 1 ]; then
      echo "$svc: expected ${key} -> http://social-emulator:8090* got:" >&2
      printf '%s\n' "${env_lines[@]}" | grep "^${key}=" >&2 || echo "(unset)" >&2
      exit 1
    fi
  done
  for line in "${env_lines[@]}"; do
    val="${line#*=}"
    if echo "$val" | grep -qiE "$FORBIDDEN_HOST_RE"; then
      echo "$svc: forbidden production host in ${line}" >&2
      exit 1
    fi
  done
done

echo "==> compose file wires JVM full services + production-host sinkhole"
COMPOSE_ON_DISK="${COMPOSE_FILE}"
if ! grep -q 'x-jvm-full-service' "$COMPOSE_ON_DISK"; then
  echo "missing x-jvm-full-service anchor in ${COMPOSE_ON_DISK}" >&2
  exit 1
fi
if ! grep -A25 'x-jvm-full-service' "$COMPOSE_ON_DISK" | grep -q 'api.vk.com:127.0.0.1'; then
  echo "x-jvm-full-service must sinkhole api.vk.com" >&2
  exit 1
fi
for svc in api scheduler ingest notifications; do
  if ! awk "/^  ${svc}:/,/^  [a-zA-Z0-9_-]+:/" "$COMPOSE_ON_DISK" | grep -q 'jvm-full-service'; then
    echo "${svc}: expected <<: *jvm-full-service" >&2
    exit 1
  fi
done
if ! awk '/^x-publisher-base:/,/^x-[a-z]/' "$COMPOSE_ON_DISK" | grep -q 'jvm-full-service'; then
  echo "x-publisher-base: expected <<: *jvm-full-service" >&2
  exit 1
fi

echo "==> emulator accepts VK API traffic"
hits_before="$(curl -sf http://127.0.0.1:8090/_control/http-hits | jq -r '.total // 0')"
curl -sf -X POST "http://127.0.0.1:8090/method/wall.get" \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  --data 'owner_id=-1&count=1&access_token=emulator&v=5.199' >/dev/null
hits_after="$(curl -sf http://127.0.0.1:8090/_control/http-hits | jq -r '.total // 0')"
if [ "${hits_after}" -le "${hits_before}" ]; then
  echo "emulator http-hits did not increase (${hits_before} -> ${hits_after})" >&2
  exit 1
fi

echo "full profile external URL guard OK"