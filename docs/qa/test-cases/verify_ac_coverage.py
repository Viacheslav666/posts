#!/usr/bin/env python3
"""Verify QA-4 acceptance: every P0 AC in stories E1–E5 appears in a test-cases matrix.

Parses AC lines from docs/product/stories/E[1-5]-*.md and checks that
docs/qa/test-cases/E{n}.md contains a table row with Story + AC#.
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
STORIES = ROOT / "docs" / "product" / "stories"
CASES = ROOT / "docs" / "qa" / "test-cases"

AC_RE = re.compile(r"^(\d+)\.\s+\*\*Given\*\*", re.M)
STORY_ID_RE = re.compile(r"^id:\s*(US-[\d.]+)\s*$", re.M)
EPIC_RE = re.compile(r"^epic:\s*(E\d)\s*$", re.M)
PRIORITY_RE = re.compile(r"^priority:\s*(P\d)\s*$", re.M)


def parse_stories() -> list[tuple[str, str, int]]:
    out: list[tuple[str, str, int]] = []
    for path in sorted(STORIES.glob("E[1-5]-US-*.md")):
        text = path.read_text(encoding="utf-8")
        sid = STORY_ID_RE.search(text)
        epic = EPIC_RE.search(text)
        prio = PRIORITY_RE.search(text)
        if not sid or not epic:
            raise SystemExit(f"Cannot parse {path}")
        if prio and prio.group(1) != "P0":
            continue
        ac_nums = [int(m.group(1)) for m in AC_RE.finditer(text)]
        if not ac_nums:
            raise SystemExit(f"No ACs in {path}")
        for n in ac_nums:
            out.append((epic.group(1), sid.group(1), n))
    return out


def load_case_text(epic: str) -> str:
    path = CASES / f"{epic}.md"
    if not path.exists():
        raise SystemExit(f"Missing {path}")
    return path.read_text(encoding="utf-8")


def covered(case_text: str, story: str, ac: int) -> bool:
    # Rows like: | US-1.1 | 3 | ... |
    patterns = [
        rf"\|\s*{re.escape(story)}\s*\|\s*{ac}\s*\|",
        rf"\|\s*{re.escape(story)}\s*/\s*AC{ac}\b",
        rf"\|\s*{re.escape(story)}\s*/\s*{ac}\b",
        rf"{re.escape(story)}\s*/\s*AC{ac}\b",
        rf"{re.escape(story)}\s*/\s*AC{ac}[–-]",
        rf"{re.escape(story)}\s*/\s*AC{ac},",
        rf"{re.escape(story)}\s*/\s*AC{ac}\s",
    ]
    return any(re.search(p, case_text) for p in patterns)


def main() -> int:
    required = parse_stories()
    by_epic: dict[str, str] = {}
    missing: list[str] = []
    for epic, story, ac in required:
        text = by_epic.get(epic)
        if text is None:
            text = load_case_text(epic)
            by_epic[epic] = text
        if not covered(text, story, ac):
            missing.append(f"{epic} {story} AC{ac}")

    # Spot-check automation markers exist
    auto_issues = []
    for epic, text in by_epic.items():
        if "авто" not in text or "ручной" not in text:
            auto_issues.append(epic)

    print(f"P0 ACs checked: {len(required)}")
    if missing:
        print("MISSING coverage:")
        for m in missing:
            print(" -", m)
        return 1
    if auto_issues:
        print("Missing авто/ручной markers in:", ", ".join(auto_issues))
        return 1
    print("OK: all P0 ACs E1–E5 mapped; automation markers present")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
