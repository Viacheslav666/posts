#!/usr/bin/env bash
# TC-PUB-07 chaos helper (QA-5): kill publisher mid-flight against compose profile full.
set -euo pipefail
COMPOSE_FILE="${COMPOSE_FILE:-infra/docker-compose/docker-compose.yml}"
EMULATOR_URL="${EMULATOR_URL:-http://127.0.0.1:8090}"

echo "==> baseline emulator counts"
curl -sf "${EMULATOR_URL}/_control/posts/count" | tee /tmp/tc-pub-07-before.json
echo

echo "==> killing publisher-tg (SIGKILL)"
docker compose -f "${COMPOSE_FILE}" --profile full kill -s SIGKILL publisher-tg || \
  docker compose -f "${COMPOSE_FILE}" --profile full kill -s SIGKILL publisher || true

sleep 2
echo "==> restarting publisher"
docker compose -f "${COMPOSE_FILE}" --profile full up -d publisher-tg 2>/dev/null || \
  docker compose -f "${COMPOSE_FILE}" --profile full up -d publisher

echo "==> wait for recovery window (reaper + retries)"
sleep 30

echo "==> verify no true duplicates"
curl -sf "${EMULATOR_URL}/_control/verify" | tee /tmp/tc-pub-07-verify.json
echo
grep -q '"ok":true\|"ok": true' /tmp/tc-pub-07-verify.json
echo "TC-PUB-07 chaos helper finished (check DB succeeded count vs emulator separately)."
