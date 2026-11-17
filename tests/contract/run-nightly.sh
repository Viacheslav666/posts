#!/usr/bin/env bash
# Schemathesis nightly: broader property-based run against compose-backed API.
# Stub endpoints return documented HTTP 501 — use status_code_conformance
# (not not_a_server_error) so 501 is accepted when declared in OpenAPI.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
API_BASE_URL="${API_BASE_URL:-http://127.0.0.1:8080}"
OPENAPI_PATH="${OPENAPI_PATH:-${ROOT}/docs/api/openapi.yaml}"
REPORT_DIR="${REPORT_DIR:-${ROOT}/tests/contract/reports}"
MAX_EXAMPLES="${MAX_EXAMPLES:-5}"

mkdir -p "${REPORT_DIR}"
export PATH="${HOME}/.local/bin:${PATH}"

if ! command -v schemathesis >/dev/null 2>&1 && ! command -v st >/dev/null 2>&1; then
  echo "schemathesis not found; install with: pip install 'schemathesis>=3.39,<5'" >&2
  exit 127
fi

ST_BIN="$(command -v schemathesis || command -v st)"

echo "==> waiting for ${API_BASE_URL}/api/v1/ping"
ok=0
for _ in $(seq 1 60); do
  if curl -sf "${API_BASE_URL}/api/v1/ping" >/dev/null; then
    ok=1
    break
  fi
  sleep 2
done
if [[ "${ok}" != "1" ]]; then
  echo "API ping failed at ${API_BASE_URL}" >&2
  exit 1
fi

echo "==> Schemathesis nightly against ${API_BASE_URL} (max-examples=${MAX_EXAMPLES})"
"${ST_BIN}" run "${OPENAPI_PATH}" \
  --url "${API_BASE_URL}" \
  --max-examples "${MAX_EXAMPLES}" \
  --phases examples,fuzzing \
  --checks status_code_conformance,content_type_conformance \
  --report junit \
  --report-dir "${REPORT_DIR}" \
  --report-junit-path "${REPORT_DIR}/schemathesis-nightly-junit.xml"

echo "OK: nightly report → ${REPORT_DIR}/schemathesis-nightly-junit.xml"
