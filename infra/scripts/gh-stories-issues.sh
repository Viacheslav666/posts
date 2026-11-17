#!/usr/bin/env bash
# Create GitHub Issues from docs/product/stories/E*-US-*.md (idempotent).
#
# Usage:
#   ./infra/scripts/gh-stories-issues.sh                 # dry-run (default)
#   ./infra/scripts/gh-stories-issues.sh --apply         # create missing issues
#   ./infra/scripts/gh-stories-issues.sh --apply --repo owner/name
#   ./infra/scripts/gh-stories-issues.sh --dry-run
#
# Idempotency: skips when an issue with the exact title "[US-X.Y] …" already
# exists (open or closed). Does not create duplicates on re-run.
#
# Owner runs --apply after merge; AN-3 PR only adds this script.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
STORIES_DIR="${ROOT}/docs/product/stories"
APPLY=0
REPO=""

usage() {
  cat <<'EOF'
Create GitHub Issues from docs/product/stories/E*-US-*.md (idempotent).

  ./infra/scripts/gh-stories-issues.sh                 # dry-run (default)
  ./infra/scripts/gh-stories-issues.sh --apply         # create missing issues
  ./infra/scripts/gh-stories-issues.sh --apply --repo owner/name
  ./infra/scripts/gh-stories-issues.sh --dry-run
EOF
  exit "${1:-0}"
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --apply) APPLY=1; shift ;;
    --dry-run) APPLY=0; shift ;;
    --repo)
      REPO="${2:-}"
      if [[ -z "${REPO}" ]]; then
        echo "error: --repo requires owner/name" >&2
        exit 1
      fi
      shift 2
      ;;
    -h|--help) usage 0 ;;
    *) echo "unknown arg: $1" >&2; usage 1 ;;
  esac
done

if ! command -v gh >/dev/null 2>&1; then
  echo "error: GitHub CLI (gh) is required" >&2
  exit 1
fi

if ! gh auth status >/dev/null 2>&1; then
  echo "error: gh is not authenticated; run: gh auth login" >&2
  exit 1
fi

if [[ -z "${REPO}" ]]; then
  REPO="$(gh repo view --json nameWithOwner --jq .nameWithOwner 2>/dev/null || true)"
fi
if [[ -z "${REPO}" ]]; then
  REPO="Viacheslav666/posts"
fi

if [[ ! -d "${STORIES_DIR}" ]]; then
  echo "error: stories dir not found: ${STORIES_DIR}" >&2
  exit 1
fi

frontmatter_value() {
  local file="$1" key="$2"
  awk -v k="${key}" '
    BEGIN { in_fm=0 }
    /^---$/ { in_fm++; next }
    in_fm==1 && $0 ~ ("^" k ":") {
      sub("^[^:]+:[[:space:]]*", "", $0)
      gsub(/[\[\]]/, "", $0)
      gsub(/^[[:space:]]+|[[:space:]]+$/, "", $0)
      print $0
      exit
    }
  ' "${file}"
}

story_h1() {
  local file="$1"
  awk '
    BEGIN { fm=0 }
    /^---$/ { fm++; next }
    fm>=2 && /^# / {
      sub(/^# /, "")
      print
      exit
    }
  ' "${file}"
}

issue_title_for() {
  local us_id="$1" h1="$2" rest
  rest="${h1}"
  if [[ "${rest}" == "${us_id}."* ]]; then
    rest="${rest#${us_id}.}"
  elif [[ "${rest}" == "${us_id} "* ]]; then
    rest="${rest#${us_id} }"
  fi
  rest="$(echo "${rest}" | sed 's/^[[:space:]]*//; s/[[:space:]]*$//')"
  echo "[${us_id}] ${rest}"
}

body_from_story() {
  local file="$1" relpath="$2"
  # Strip YAML frontmatter (first --- … --- block)
  local content
  content="$(awk '
    BEGIN { fm=0 }
    /^---$/ { fm++; next }
    fm>=2 { print }
  ' "${file}")"

  cat <<EOF
<!-- generated-by: infra/scripts/gh-stories-issues.sh -->
<!-- story-file: ${relpath} -->

Источник правды: [\`${relpath}\`](https://github.com/${REPO}/blob/main/${relpath})

${content}
EOF
}

priority_label() {
  case "$1" in
    P0|P1|P2) echo "$1" ;;
    *) echo "P0" ;;
  esac
}

ensure_story_label() {
  gh label create "story" \
    --repo "${REPO}" \
    --color "BFD4F2" \
    --description "User story (product AN-3)" \
    --force >/dev/null 2>&1 || true
}

CREATED=0
SKIPPED=0
FAILED=0

echo "Repository: ${REPO}"
echo "Stories:    ${STORIES_DIR}"
if [[ ${APPLY} -eq 1 ]]; then
  echo "Mode:       APPLY"
else
  echo "Mode:       DRY-RUN"
fi
echo

# Load existing issue titles (open + closed) for idempotency
EXISTING_JSON="$(gh issue list --repo "${REPO}" --state all --limit 500 --json title 2>/dev/null || echo '[]')"

title_exists() {
  local t="$1"
  echo "${EXISTING_JSON}" | jq -e --arg t "${t}" 'any(.[]; .title == $t)' >/dev/null 2>&1
}

if [[ ${APPLY} -eq 1 ]]; then
  ensure_story_label
fi

shopt -s nullglob
files=("${STORIES_DIR}"/E*-US-*.md)
IFS=$'\n' files_sorted=($(printf '%s\n' "${files[@]}" | sort -V))
unset IFS

if [[ ${#files_sorted[@]} -eq 0 ]]; then
  echo "error: no story files matching E*-US-*.md" >&2
  exit 1
fi

for file in "${files_sorted[@]}"; do
  us_id="$(frontmatter_value "${file}" "id")"
  priority="$(frontmatter_value "${file}" "priority")"
  h1="$(story_h1 "${file}")"

  if [[ -z "${us_id}" || -z "${h1}" ]]; then
    echo "skip  (parse error) $(basename "${file}")" >&2
    FAILED=$((FAILED + 1))
    continue
  fi

  issue_title="$(issue_title_for "${us_id}" "${h1}")"
  relpath="docs/product/stories/$(basename "${file}")"
  plabel="$(priority_label "${priority}")"

  if title_exists "${issue_title}"; then
    echo "exists  ${issue_title}"
    SKIPPED=$((SKIPPED + 1))
    continue
  fi

  if [[ ${APPLY} -eq 0 ]]; then
    echo "create  ${issue_title}  [${plabel} phase:1 story]"
    CREATED=$((CREATED + 1))
    continue
  fi

  body="$(body_from_story "${file}" "${relpath}")"

  # Labels: story + phase + priority + roles (implementation vertical)
  if url="$(
    gh issue create \
      --repo "${REPO}" \
      --title "${issue_title}" \
      --body "${body}" \
      --label "story" \
      --label "phase:1" \
      --label "${plabel}" \
      --label "role:analyst" \
      --label "role:backend" \
      --label "role:frontend" \
      --label "role:qa" \
      2>/dev/null
  )"; then
    echo "created ${url}"
    EXISTING_JSON="$(echo "${EXISTING_JSON}" | jq --arg t "${issue_title}" '. + [{title:$t}]')"
    CREATED=$((CREATED + 1))
  elif url="$(
    gh issue create \
      --repo "${REPO}" \
      --title "${issue_title}" \
      --body "${body}" \
      --label "phase:1" \
      --label "${plabel}" \
      2>/dev/null
  )"; then
    echo "created ${url} (subset of labels — run infra/scripts/gh-labels.sh)"
    EXISTING_JSON="$(echo "${EXISTING_JSON}" | jq --arg t "${issue_title}" '. + [{title:$t}]')"
    CREATED=$((CREATED + 1))
  else
    echo "FAIL    ${issue_title}" >&2
    FAILED=$((FAILED + 1))
  fi
done

echo
echo "Done. create/would-create=${CREATED} skipped_existing=${SKIPPED} failed=${FAILED}"
if [[ ${APPLY} -eq 0 ]]; then
  echo "Dry-run only. Re-run with --apply to create issues (owner)."
fi

[[ ${FAILED} -eq 0 ]]
