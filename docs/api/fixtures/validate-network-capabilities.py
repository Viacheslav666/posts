#!/usr/bin/env python3
"""Проверка docs/api/fixtures/network-capabilities.yaml по JSON Schema (AN-2)."""

from __future__ import annotations

import json
import sys
from pathlib import Path

try:
    import yaml
except ImportError as exc:  # pragma: no cover
    raise SystemExit("Нужен PyYAML: pip install pyyaml") from exc

try:
    from jsonschema import Draft202012Validator
except ImportError as exc:  # pragma: no cover
    raise SystemExit("Нужен jsonschema: pip install jsonschema") from exc

ROOT = Path(__file__).resolve().parent
YAML_PATH = ROOT / "network-capabilities.yaml"
SCHEMA_PATH = ROOT / "network-capabilities.schema.json"


def main() -> int:
    data = yaml.safe_load(YAML_PATH.read_text(encoding="utf-8"))
    schema = json.loads(SCHEMA_PATH.read_text(encoding="utf-8"))
    validator = Draft202012Validator(schema)
    errors = sorted(validator.iter_errors(data), key=lambda e: list(e.path))
    if errors:
        for err in errors:
            path = ".".join(str(p) for p in err.path) or "(root)"
            print(f"FAIL {path}: {err.message}", file=sys.stderr)
        print(f"{len(errors)} error(s)", file=sys.stderr)
        return 1

    # Доп. инвариант AN-2: нет confirmed без source/checked_at (уже в схеме),
    # и нет «голых» чисел вне Fact — проверяем, что у каждой сети есть id.
    ids = [n["id"] for n in data["networks"]]
    expected = ["vk", "telegram", "ok", "max", "rutube"]
    if ids != expected:
        print(f"FAIL networks order/ids: {ids} != {expected}", file=sys.stderr)
        return 1

    unknown = 0
    confirmed = 0

    def walk(node: object) -> None:
        nonlocal unknown, confirmed
        if isinstance(node, dict):
            if "status" in node and "source" in node and "checked_at" in node:
                if node["status"] == "unknown":
                    unknown += 1
                elif node["status"] == "confirmed":
                    confirmed += 1
            for v in node.values():
                walk(v)
        elif isinstance(node, list):
            for v in node:
                walk(v)

    walk(data)
    print(f"OK {YAML_PATH.name}: schema valid; facts confirmed={confirmed} unknown={unknown}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
