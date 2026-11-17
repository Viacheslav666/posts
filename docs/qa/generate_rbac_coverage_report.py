#!/usr/bin/env python3
"""Генерирует docs/qa/rbac-coverage-report.md (QA-6)."""

from __future__ import annotations

from datetime import date
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parents[2]
ROLES = ROOT / "docs" / "product" / "roles-permissions.yaml"
EMAP = ROOT / "docs" / "qa" / "rbac-endpoint-map.yaml"
OUT = ROOT / "docs" / "qa" / "rbac-coverage-report.md"


def main() -> None:
    roles_doc = yaml.safe_load(ROLES.read_text(encoding="utf-8"))
    emap = yaml.safe_load(EMAP.read_text(encoding="utf-8"))
    roles = [r["id"] for r in roles_doc["roles"]]
    effects: dict[str, dict[str, str]] = {}
    for row in roles_doc["matrix"]:
        effects[row["action"]] = {r: row[r] for r in roles}

    matrix_eps = [e for e in emap["endpoints"] if e.get("rbac") == "matrix"]
    lines: list[str] = [
        "# Отчёт покрытия роль × эндпоинт (QA-6)",
        "",
        f"Обновлено: {date.today().isoformat()}. Генератор: `docs/qa/generate_rbac_coverage_report.py`.",
        "",
        "Источник прав: `docs/product/roles-permissions.yaml`. Карта: `docs/qa/rbac-endpoint-map.yaml`.",
        "",
        "## Сводка",
        "",
        "| Метрика | Значение |",
        "|---|---|",
        f"| OpenAPI operationId | {len(emap['endpoints'])} |",
        f"| В матрице RBAC | {len(matrix_eps)} |",
        f"| Вне RBAC (auth/me/public) | {len(emap['endpoints']) - len(matrix_eps)} |",
        f"| Роли | {len(roles)} |",
        f"| Ячейки роль×эндпоинт | {len(roles) * len(matrix_eps)} |",
    ]
    impl = sum(1 for e in matrix_eps if e.get("implementedHint"))
    stub = len(matrix_eps) - impl
    lines += [
        f"| Реализованы (hint) | {impl} |",
        f"| Stub / 501 (hint) | {stub} — HTTP skip до реализации |",
        "",
        "## Матрица (ожидание HTTP)",
        "",
        "Легенда: **A** = allow (≠401/403), **D** = deny (403). Foreign workspace → **404** (IDOR).",
        "Эндпоинт со статусом stub автоматически skip в IT, пока ответ 501.",
        "",
        "| operationId | action | implemented | " + " | ".join(roles) + " |",
        "|---|---|---|" + "|---" * len(roles) + "|",
    ]
    for e in matrix_eps:
        action = e["action"]
        cells = []
        for r in roles:
            eff = effects.get(action, {}).get(r, "deny")
            cells.append("A" if eff == "allow" else "D")
        lines.append(
            f"| `{e['operationId']}` | `{action}` | "
            f"{'yes' if e.get('implementedHint') else 'stub'} | " + " | ".join(cells) + " |"
        )
    lines += [
        "",
        "## Синхронизация",
        "",
        "- `RbacOpenApiEndpointMapSyncTest` — падает, если в OpenAPI появился operationId без записи в карте.",
        "- `RbacMatrixDocsSyncTest` — падает при рассинхроне product YAML и classpath-копии BE-6.",
        "- `RbacMatrixFullCoverageTest` — все пары роль×action из YAML (unit).",
        "- `RbacEndpointMatrixIT` / `RbacIdorMatrixIT` — HTTP; skip при 501.",
        "- Сценарные TC-RBAC-01…05 / TC-IDOR-01 — `WorkspaceRbacIntegrationTest` (не дублируются).",
        "",
    ]
    OUT.write_text("\n".join(lines), encoding="utf-8")
    print(f"Wrote {OUT.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
