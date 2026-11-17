#!/usr/bin/env python3
"""Generate logical QA seed fixtures (QA-4).

Outputs JSON independent of the database schema.
TODO(BE-2): add SQL/JDBC mapper after domain Flyway migrations land.
"""

from __future__ import annotations

import argparse
import json
import math
import random
import sys
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Any

try:
    import yaml
except ImportError:  # pragma: no cover - stdlib fallback not expected in CI docs
    yaml = None  # type: ignore


ROOT = Path(__file__).resolve().parent
DEFAULT_SPEC = ROOT / "seed_spec.yaml"
DEFAULT_OUT = ROOT / "out"


def load_spec(path: Path) -> dict[str, Any]:
    text = path.read_text(encoding="utf-8")
    if yaml is not None:
        return yaml.safe_load(text)
    # Minimal YAML subset via json if PyYAML missing: require JSON twin
    json_twin = path.with_suffix(".json")
    if json_twin.exists():
        return json.loads(json_twin.read_text(encoding="utf-8"))
    raise RuntimeError(
        "PyYAML is required to read seed_spec.yaml. "
        "Install pyyaml or provide seed_spec.json."
    )


def stable_id(prefix: str, n: int) -> str:
    return f"{prefix}_{n:06d}"


def pick_weighted(rng: random.Random, weights: dict[str, float]) -> str:
    keys = list(weights.keys())
    vals = [weights[k] for k in keys]
    return rng.choices(keys, weights=vals, k=1)[0]


def allocate_counts(total: int, mix: dict[str, float]) -> dict[str, int]:
    keys = list(mix.keys())
    raw = [mix[k] * total for k in keys]
    counts = {k: int(math.floor(v)) for k, v in zip(keys, raw)}
    remainder = total - sum(counts.values())
    # Give leftovers to largest fractional parts
    fracs = sorted(
        ((raw[i] - counts[keys[i]], keys[i]) for i in range(len(keys))),
        reverse=True,
    )
    for i in range(remainder):
        counts[fracs[i % len(fracs)][1]] += 1
    return counts


def build_users(spec: dict[str, Any]) -> list[dict[str, Any]]:
    users = []
    for u in spec["users"]:
        users.append(
            {
                "logical_id": u["key"],
                "email": u["email"],
                "display_name": u["display_name"],
                "timezone": u.get("timezone", "Europe/Moscow"),
                "email_verified": u.get("email_verified", True),
                "totp_enabled": u.get("totp_enabled", False),
                "plan_hint": u.get("plan"),
                "notes": u.get("notes"),
                # Placeholder password hash marker — never a real secret
                "password_placeholder": "SEED_ONLY_CHANGE_ME",
            }
        )
    return users


def build_workspaces(spec: dict[str, Any], profile: str) -> tuple[list[dict], list[dict], list[dict]]:
    workspaces: list[dict[str, Any]] = []
    projects: list[dict[str, Any]] = []
    members: list[dict[str, Any]] = []

    volumes = spec["volumes"]
    profiles = spec.get("profiles", {})
    profile_cfg = profiles.get(profile, {})
    channels_n = profile_cfg.get(
        "channels_per_load_workspace", volumes["channels_per_load_workspace"]
    )
    posts_n = profile_cfg.get(
        "posts_per_load_workspace", volumes["posts_per_load_workspace"]
    )

    for ws in spec["workspaces"]:
        is_load = ws["key"] == "ws_load"
        ws_channels = channels_n if is_load else ws.get("channels_count", 0)
        ws_posts = posts_n if is_load else ws.get("posts_count", 0)
        workspaces.append(
            {
                "logical_id": ws["key"],
                "name": ws["name"],
                "owner_user": ws["owner"],
                "plan": ws.get("plan", "free"),
                "channel_addons": ws.get("channel_addons", 0),
                "target_channels": ws_channels,
                "target_posts": ws_posts,
            }
        )
        for p in ws.get("projects", []):
            projects.append(
                {
                    "logical_id": p["key"],
                    "workspace": ws["key"],
                    "name": p["name"],
                }
            )
        # Owner membership implied
        members.append(
            {
                "workspace": ws["key"],
                "user": ws["owner"],
                "role": "owner",
                "projects": [p["key"] for p in ws.get("projects", [])],
            }
        )
        for m in ws.get("members", []):
            members.append(
                {
                    "workspace": ws["key"],
                    "user": m["user"],
                    "role": m["role"],
                    "projects": m.get("projects", []),
                }
            )
    return workspaces, projects, members


def build_channels(
    spec: dict[str, Any],
    workspaces: list[dict[str, Any]],
    projects: list[dict[str, Any]],
    rng: random.Random,
) -> list[dict[str, Any]]:
    channels: list[dict[str, Any]] = []
    dist = spec["channels"]["distribution"]
    status_mix = spec["channels"]["status_mix"]
    projects_by_ws: dict[str, list[str]] = {}
    for p in projects:
        projects_by_ws.setdefault(p["workspace"], []).append(p["logical_id"])

    seq = 1
    for ws in workspaces:
        n = int(ws["target_channels"])
        if n <= 0:
            continue
        ws_projects = projects_by_ws.get(ws["logical_id"], [])
        if not ws_projects:
            raise ValueError(f"workspace {ws['logical_id']} has no projects for channels")
        for i in range(n):
            network = pick_weighted(rng, dist)
            status = pick_weighted(rng, status_mix)
            project = ws_projects[i % len(ws_projects)]
            channels.append(
                {
                    "logical_id": stable_id("ch", seq),
                    "workspace": ws["logical_id"],
                    "project": project,
                    "network": network,
                    "external_id": f"{network}-ext-{seq:06d}",
                    "title": f"{network.upper()} Channel {seq}",
                    "status": status,
                }
            )
            seq += 1
    return channels


def build_posts(
    spec: dict[str, Any],
    workspaces: list[dict[str, Any]],
    channels: list[dict[str, Any]],
    members: list[dict[str, Any]],
    rng: random.Random,
) -> list[dict[str, Any]]:
    posts: list[dict[str, Any]] = []
    status_mix = spec["volumes"]["post_status_mix"]
    network_mix = spec["posts"]["networks_per_post_mix"]
    now = datetime(2026, 11, 17, 12, 0, 0, tzinfo=timezone.utc)

    channels_by_ws: dict[str, list[dict[str, Any]]] = {}
    for ch in channels:
        channels_by_ws.setdefault(ch["workspace"], []).append(ch)

    authors_by_ws: dict[str, list[str]] = {}
    for m in members:
        if m["role"] in ("owner", "admin", "editor", "author"):
            authors_by_ws.setdefault(m["workspace"], []).append(m["user"])

    seq = 1
    for ws in workspaces:
        n = int(ws["target_posts"])
        if n <= 0:
            continue
        ws_channels = channels_by_ws.get(ws["logical_id"], [])
        if not ws_channels:
            raise ValueError(f"workspace {ws['logical_id']} needs channels to attach posts")
        authors = authors_by_ws.get(ws["logical_id"], [ws["owner_user"]])
        status_counts = allocate_counts(n, status_mix)
        status_queue: list[str] = []
        for st, cnt in status_counts.items():
            status_queue.extend([st] * cnt)
        rng.shuffle(status_queue)

        for status in status_queue:
            multi = pick_weighted(rng, network_mix)
            k = {"single": 1, "multi_2": 2, "multi_3": 3, "multi_4": 4}[multi]
            chosen = rng.sample(ws_channels, k=min(k, len(ws_channels)))
            scheduled_at = None
            if status in ("scheduled", "paused", "failed"):
                scheduled_at = (now + timedelta(hours=rng.randint(1, 720))).isoformat()
            elif status == "published":
                scheduled_at = (now - timedelta(hours=rng.randint(1, 720))).isoformat()

            variants = []
            networks = sorted({c["network"] for c in chosen})
            for net in networks:
                variants.append(
                    {
                        "network": net,
                        "text": f"Seed post {seq} for {net}",
                        "has_media": rng.random() < 0.25,
                    }
                )

            posts.append(
                {
                    "logical_id": stable_id("post", seq),
                    "workspace": ws["logical_id"],
                    "project": chosen[0]["project"],
                    "created_by": rng.choice(authors),
                    "status": status,
                    "scheduled_at": scheduled_at,
                    "channel_ids": [c["logical_id"] for c in chosen],
                    "variants": variants if spec["posts"].get("include_variants", True) else [],
                }
            )
            seq += 1
    return posts


def summarize(payload: dict[str, Any]) -> dict[str, Any]:
    return {
        "users": len(payload["users"]),
        "workspaces": len(payload["workspaces"]),
        "projects": len(payload["projects"]),
        "members": len(payload["memberships"]),
        "channels": len(payload["channels"]),
        "posts": len(payload["posts"]),
        "channels_by_workspace": {
            ws["logical_id"]: sum(
                1 for c in payload["channels"] if c["workspace"] == ws["logical_id"]
            )
            for ws in payload["workspaces"]
        },
        "posts_by_workspace": {
            ws["logical_id"]: sum(
                1 for p in payload["posts"] if p["workspace"] == ws["logical_id"]
            )
            for ws in payload["workspaces"]
        },
        "roles_present": sorted({m["role"] for m in payload["memberships"]}),
    }


def generate(spec: dict[str, Any], profile: str) -> dict[str, Any]:
    seed = int(spec["posts"].get("seed", 424242))
    rng = random.Random(seed)
    users = build_users(spec)
    workspaces, projects, members = build_workspaces(spec, profile)
    channels = build_channels(spec, workspaces, projects, rng)
    posts = build_posts(spec, workspaces, channels, members, rng)
    payload = {
        "meta": {
            "version": spec.get("version"),
            "task": spec.get("task"),
            "profile": profile,
            "generated_at": datetime.now(timezone.utc).isoformat(),
            "schema_binding": spec.get("schema_binding", "TODO after BE-2"),
            "seed": seed,
            "todo_be2": (
                "Map logical entities to Flyway tables after BE-2 merges. "
                "Do not emit SQL until column names are stable."
            ),
        },
        "users": users,
        "workspaces": workspaces,
        "projects": projects,
        "memberships": members,
        "channels": channels,
        "posts": posts,
    }
    payload["summary"] = summarize(payload)
    return payload


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Generate QA seed logical fixtures")
    parser.add_argument("--spec", type=Path, default=DEFAULT_SPEC)
    parser.add_argument(
        "--profile",
        choices=["smoke", "load", "idor"],
        default="smoke",
        help="smoke=small; load=1000 channels / 50000 posts; idor=foreign workspace focus",
    )
    parser.add_argument("--out-dir", type=Path, default=DEFAULT_OUT)
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Print summary only, do not write files",
    )
    args = parser.parse_args(argv)

    spec = load_spec(args.spec)
    if args.profile == "idor":
        # Force tiny volumes on load workspace for IDOR-focused dumps
        spec = json.loads(json.dumps(spec))
        spec.setdefault("profiles", {}).setdefault("idor", {})
        spec["profiles"]["idor"]["channels_per_load_workspace"] = 4
        spec["profiles"]["idor"]["posts_per_load_workspace"] = 10

    payload = generate(spec, args.profile)
    summary = payload["summary"]

    print(json.dumps({"profile": args.profile, "summary": summary}, ensure_ascii=False, indent=2))

    # Acceptance checks for load profile
    if args.profile == "load":
        ch = summary["channels_by_workspace"].get("ws_load", 0)
        po = summary["posts_by_workspace"].get("ws_load", 0)
        roles = set(summary["roles_present"])
        required_roles = {"owner", "admin", "editor", "author", "viewer"}
        if ch != 1000 or po != 50000:
            print(
                f"ERROR: expected ws_load 1000 channels / 50000 posts, got {ch}/{po}",
                file=sys.stderr,
            )
            return 1
        if not required_roles.issubset(roles):
            print(f"ERROR: missing roles {required_roles - roles}", file=sys.stderr)
            return 1

    if args.dry_run:
        return 0

    args.out_dir.mkdir(parents=True, exist_ok=True)
    out_path = args.out_dir / f"seed-{args.profile}.json"
    out_path.write_text(
        json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    (args.out_dir / f"seed-{args.profile}.summary.json").write_text(
        json.dumps(summary, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    print(f"Wrote {out_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
