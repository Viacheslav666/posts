#!/usr/bin/env bash
# Create or update GitHub labels for the Posts monorepo (idempotent).
# Usage:
#   ./infra/scripts/gh-labels.sh [owner/repo]
# Defaults to the current repository (gh repo view) or Viacheslav666/posts.
set -euo pipefail

if ! command -v gh >/dev/null 2>&1; then
  echo "error: GitHub CLI (gh) is required" >&2
  exit 1
fi

if ! gh auth status >/dev/null 2>&1; then
  echo "error: gh is not authenticated; run: gh auth login" >&2
  exit 1
fi

REPO="${1:-}"
if [[ -z "${REPO}" ]]; then
  REPO="$(gh repo view --json nameWithOwner --jq .nameWithOwner 2>/dev/null || true)"
fi
if [[ -z "${REPO}" ]]; then
  REPO="Viacheslav666/posts"
fi

# name|color|description
LABELS=(
  "role:analyst|5319E7|Задача агента Posts Аналитик"
  "role:backend|1D76DB|Задача агента Posts Разраб Бэк"
  "role:frontend|B60205|Задача агента Posts Разраб Фронт"
  "role:devops|0E8A16|Задача агента Posts Devops"
  "role:qa|D93F0B|Задача агента Posts Тестировщик"
  "phase:0|C5DEF5|Фаза 0 — фундамент"
  "phase:1|0075CA|Фаза 1 — MVP"
  "phase:2|0052CC|Фаза 2 — превосходство"
  "phase:3|0B3D91|Фаза 3 — экосистема"
  "P0|B60205|Приоритет P0 — критично"
  "P1|FBCA04|Приоритет P1 — высокий"
  "P2|C2E0C6|Приоритет P2 — обычный / позже"
  "contract-change|E99695|Несовместимое изменение OpenAPI / TS-клиента"
  "dependencies|0366D6|Обновления зависимостей (Renovate)"
  "e2e|BFDADC|Запуск e2e по метке"
)

upsert_label() {
  local name="$1"
  local color="$2"
  local description="$3"

  # --force: create or update color/description if the label already exists
  gh label create "${name}" \
    --repo "${REPO}" \
    --color "${color}" \
    --description "${description}" \
    --force >/dev/null

  echo "ok  ${name}"
}

echo "Applying labels to ${REPO}…"
for entry in "${LABELS[@]}"; do
  IFS='|' read -r name color description <<<"${entry}"
  upsert_label "${name}" "${color}" "${description}"
done

echo "Done. $(${#LABELS[@]}) labels ensured on ${REPO}."
