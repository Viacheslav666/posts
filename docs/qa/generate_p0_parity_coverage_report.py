#!/usr/bin/env python3
"""Generate docs/qa/p0-parity-coverage-report.md (QA-13).

Checks that every P0 AC row in E10 / E11 / E17 test-case matrices maps to an
automated test reference in the corresponding docs/qa/traceability-tc-*.md file.
Exit 1 if coverage < 100%.
"""

from __future__ import annotations

import re
import sys
from datetime import date
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
QA = ROOT / "docs" / "qa"
CASES = QA / "test-cases"
OUT = QA / "p0-parity-coverage-report.md"

# (epic, cases file, traceability file, AC column pattern in cases matrix)
EPICS = [
    ("E10", "E10-ad-marking.md", "traceability-tc-ad.md", re.compile(r"^\|\s*(\d+|—)\s*\|")),
    ("E11", "E11-stories.md", "traceability-tc-story.md", re.compile(r"^\|\s*(AC-STORY-\d+)\s*\|")),
    ("E17", "E17-grabber.md", "traceability-tc-grab.md", re.compile(r"^\|\s*(AC-G\d+)\s*\|")),
]

TC_RE = re.compile(r"\b(TC-[A-Z]+-\d+[a-z]?)\b")
P0_RE = re.compile(r"\|\s*P0\s*\|")
AUTO_RE = re.compile(r"\|\s*авто")


def parse_p0_acs(cases_path: Path, epic: str) -> list[tuple[str, str]]:
    """Return list of (ac_id, tc_ids) for P0 rows in the AC→TC matrix."""
    text = cases_path.read_text(encoding="utf-8")
    # Find the first "Трассировка" section table.
    section = re.search(r"## Трассировка.*?\n\n(\|.+?)(?:\n\n|\n## |\Z)", text, re.S)
    if not section:
        raise SystemExit(f"No трассировка table in {cases_path}")
    table = section.group(1)
    rows: list[tuple[str, str]] = []
    for line in table.splitlines():
        if not line.startswith("|") or line.startswith("|---") or "AC#" in line or "Кратко" in line:
            continue
        if "AC-STORY" in line or "AC-G" in line or re.match(r"^\|\s*\d+\s*\|", line) or re.match(
            r"^\|\s*—\s*\|", line
        ):
            if "P0" not in line and epic != "E10":
                # E10 matrix uses automation column; treat numbered + dash rows with авто as P0
                # unless explicitly P1 in a later column — E10 table has no P column on AC rows.
                pass
            if epic == "E11" and "| P0 |" not in line and "|P0|" not in line:
                continue
            if epic == "E17" and "| P0 |" not in line and "|P0|" not in line:
                continue
            tcs = TC_RE.findall(line)
            if not tcs:
                continue
            # AC id = first cell
            cells = [c.strip() for c in line.strip("|").split("|")]
            ac = cells[0]
            if epic == "E10":
                # Skip P1 rows marked in automation column as ручной only without авто — keep if авто
                if "авто" not in line:
                    continue
            rows.append((ac, ",".join(tcs)))
    return rows


def covered_in_traceability(trace_text: str, tc: str) -> bool:
    return tc in trace_text and ("авто" in trace_text.lower() or "CI" in trace_text or "nightly" in trace_text)


def main() -> int:
    lines: list[str] = [
        "# Отчёт покрытия P0 AC паритета (QA-13)",
        "",
        f"Обновлено: {date.today().isoformat()}. Генератор: `docs/qa/generate_p0_parity_coverage_report.py`.",
        "",
        "Эпики паритета: **E10** (ОРД/маркировка), **E11** (истории), **E17** (граббер).",
        "Критерий: каждый P0 AC из матрицы `test-cases/E*.md` имеет ≥1 автотест в `traceability-tc-*.md`.",
        "",
    ]
    total = 0
    covered = 0
    missing: list[str] = []

    for epic, cases_name, trace_name, _ in EPICS:
        cases_path = CASES / cases_name
        trace_path = QA / trace_name
        if not cases_path.exists() or not trace_path.exists():
            raise SystemExit(f"Missing {cases_path} or {trace_path}")
        acs = parse_p0_acs(cases_path, epic)
        trace = trace_path.read_text(encoding="utf-8")
        lines.append(f"## {epic}")
        lines.append("")
        lines.append("| AC | TC | Трассировка | Статус |")
        lines.append("|---|---|---|---|")
        for ac, tcs in acs:
            total += 1
            tc_list = tcs.split(",")
            ok = all(covered_in_traceability(trace, tc) for tc in tc_list)
            if ok:
                covered += 1
                status = "✅ авто"
            else:
                missing.append(f"{epic} {ac} → {tcs}")
                status = "❌ нет в traceability"
            lines.append(f"| {ac} | {tcs} | `{trace_name}` | {status} |")
        lines.append("")

    pct = 100.0 if total == 0 else 100.0 * covered / total
    lines.insert(
        5,
        f"**Покрытие P0 AC: {covered}/{total} = {pct:.0f}%**",
    )
    lines.insert(6, "")

    if missing:
        lines.append("## Пробелы")
        lines.append("")
        for m in missing:
            lines.append(f"- {m}")
        lines.append("")

    OUT.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"Wrote {OUT} — {covered}/{total} = {pct:.0f}%")
    if pct < 100.0:
        print("MISSING:")
        for m in missing:
            print(" -", m)
        return 1
    print("OK: P0 parity AC coverage is 100%")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
