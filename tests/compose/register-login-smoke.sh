#!/usr/bin/env bash
# Register → verify (Mailpit) → login through the compose full-profile gateway (:3000).
set -euo pipefail

WEB_BASE="${COMPOSE_WEB_BASE_URL:-http://127.0.0.1:3000}"
MAILPIT_API="${MAILPIT_API_URL:-http://127.0.0.1:8025}"
EMAIL="${COMPOSE_SMOKE_EMAIL:-smoke-$(date +%s)@example.com}"
PASSWORD="${COMPOSE_SMOKE_PASSWORD:-SmokeTest1!SmokeTest1!}"
COOKIE_JAR="$(mktemp)"
trap 'rm -f "${COOKIE_JAR}"' EXIT

echo "==> bootstrap CSRF cookie via ${WEB_BASE}/api/v1/ping"
curl -sf -c "${COOKIE_JAR}" -b "${COOKIE_JAR}" "${WEB_BASE}/api/v1/ping" >/dev/null
XSRF="$(
  grep -i 'XSRF-TOKEN' "${COOKIE_JAR}" 2>/dev/null | awk '{print $NF}' | tail -1 || true
)"
if [ -z "${XSRF}" ]; then
  echo "No XSRF-TOKEN after ping" >&2
  exit 1
fi

api_post() {
  local path="$1"
  local body="$2"
  curl -sf -X POST "${WEB_BASE}${path}" \
    -H 'Content-Type: application/json' \
    -H "X-XSRF-TOKEN: ${XSRF}" \
    -b "${COOKIE_JAR}" \
    -c "${COOKIE_JAR}" \
    -d "${body}"
}

echo "==> register via ${WEB_BASE}/api/v1/auth/register (${EMAIL})"
api_post '/api/v1/auth/register' "{\"email\":\"${EMAIL}\",\"password\":\"${PASSWORD}\",\"name\":\"Compose Smoke\"}" \
  | tee /tmp/posts-compose-register.json
echo

echo "==> waiting for verification mail in Mailpit"
message_id=""
for _ in $(seq 1 45); do
  message_id="$(
    curl -sf "${MAILPIT_API}/api/v1/search?query=${EMAIL}" \
      | python3 -c "import json,sys; d=json.load(sys.stdin); print(d['messages'][0]['ID'] if d.get('messages') else '')" 2>/dev/null || true
  )"
  if [ -n "${message_id}" ]; then
    break
  fi
  sleep 2
done
if [ -z "${message_id}" ]; then
  echo "No Mailpit message for ${EMAIL}" >&2
  exit 1
fi

body="$(curl -sf "${MAILPIT_API}/api/v1/message/${message_id}")"
token="$(
  printf '%s' "$body" | python3 -c "
import json, re, sys
raw = sys.stdin.read()
try:
    data = json.loads(raw)
    text = data.get('Text', '') or data.get('HTML', '') or raw
except json.JSONDecodeError:
    text = raw
m = re.search(r'token=([A-Za-z0-9_-]+)', text)
if not m:
    sys.exit(1)
print(m.group(1))
"
)"

echo "==> verify email via gateway"
api_post '/api/v1/auth/email/verify' "{\"token\":\"${token}\"}" | tee /tmp/posts-compose-verify.json
echo

echo "==> login via gateway"
api_post '/api/v1/auth/login' "{\"email\":\"${EMAIL}\",\"password\":\"${PASSWORD}\"}" \
  | tee /tmp/posts-compose-login.json
echo

python3 -c "
import json
with open('/tmp/posts-compose-login.json') as f:
    data = json.load(f)
assert data.get('accessToken'), 'missing accessToken'
assert data.get('refreshToken'), 'missing refreshToken'
print('register → verify → login OK')
"