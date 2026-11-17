#!/usr/bin/env python3
"""Generate ≥50 formatting fixtures for TG/Max/VK/OK (QA-4).

Limits sourced from docs/api/fixtures/network-capabilities.yaml:
  telegram.text.max_chars = 4096
  telegram caption = 1024
  max.text.max_chars = 4000
  vk/ok formatting.mode = plain
"""

from __future__ import annotations

import html
import json
import re
import sys
from pathlib import Path

OUT = Path(__file__).resolve().parent / "cases.json"
README_NOTE = Path(__file__).resolve().parent

TG_MAX = 4096
MAX_MAX = 4000
CAPTION_MAX = 1024

PLAIN_WARNING = "Форматирование будет потеряно для VK/OK (plain text)."


def strip_tags(s: str) -> str:
    # Expand common entities then drop tags
    t = s
    t = t.replace("<br/>", "\n").replace("<br>", "\n")
    t = re.sub(r"<[^>]+>", "", t)
    return html.unescape(t)


def plain_render(src_html: str) -> dict:
    plain = strip_tags(src_html)
    stripped = bool(re.search(r"<[a-zA-Z]", src_html))
    out = {"plain": plain, "formatting_stripped": stripped}
    if stripped:
        out["warning"] = PLAIN_WARNING
    return out


def net_render(src_html: str, *, drop_tags: set[str] | None = None, warnings: list[str] | None = None) -> dict:
    html_out = src_html
    warns = list(warnings or [])
    if drop_tags:
        for tag in drop_tags:
            html_out = re.sub(rf"</?{tag}(?:\s[^>]*)?>", "", html_out, flags=re.I)
            warns.append(f"unsupported_or_stripped:{tag}")
    plain = strip_tags(html_out)
    return {
        "html": html_out,
        "plain_text": plain,
        "plain_length": len(plain),
        "valid": True,
        "warnings": warns,
    }


def case(
    n: int,
    title: str,
    categories: list[str],
    html_in: str,
    *,
    tg: dict | None = None,
    mx: dict | None = None,
    notes: str = "",
    limits: dict | None = None,
    source: str = "US-4.3 / network-capabilities.yaml",
) -> dict:
    expected_tg = tg or net_render(html_in)
    expected_mx = mx or net_render(html_in)
    plain = plain_render(html_in)
    # Auto plain_length if missing
    for e in (expected_tg, expected_mx):
        if "plain_length" not in e:
            e["plain_length"] = len(e.get("plain_text", strip_tags(e.get("html", ""))))
        e.setdefault("warnings", [])
        e.setdefault("valid", True)
    payload = {
        "id": f"FMT-{n:03d}",
        "title": title,
        "categories": categories,
        "source": source,
        "input": {"kind": "canonical-html", "html": html_in},
        "expected": {
            "telegram": expected_tg,
            "max": expected_mx,
            "vk": plain,
            "ok": dict(plain),
        },
    }
    if limits:
        payload["limits"] = limits
    else:
        # Derive limit flags from plain lengths of network renders
        tg_len = expected_tg["plain_length"]
        mx_len = expected_mx["plain_length"]
        payload["limits"] = {
            "telegram_max_chars": TG_MAX,
            "max_max_chars": MAX_MAX,
            "telegram_caption_max_chars": CAPTION_MAX,
            "exceeds_telegram": tg_len > TG_MAX,
            "exceeds_max": mx_len > MAX_MAX,
            "exceeds_telegram_caption": tg_len > CAPTION_MAX,
        }
        if payload["limits"]["exceeds_telegram"]:
            expected_tg["valid"] = False
            expected_tg["warnings"] = list(expected_tg.get("warnings", [])) + ["limit:telegram_max_chars"]
        if payload["limits"]["exceeds_max"]:
            expected_mx["valid"] = False
            expected_mx["warnings"] = list(expected_mx.get("warnings", [])) + ["limit:max_max_chars"]
    if notes:
        payload["notes"] = notes
    return payload


def build_cases() -> list[dict]:
    cases: list[dict] = []
    n = 0

    def add(**kwargs):
        nonlocal n
        n += 1
        cases.append(case(n, **kwargs))

    # --- basic ---
    add(title="Plain text without markup", categories=["basic"], html_in="Hello Posts")
    add(title="Bold", categories=["basic"], html_in="<b>bold</b>")
    add(title="Italic", categories=["basic"], html_in="<i>italic</i>")
    add(title="Underline", categories=["basic"], html_in="<u>under</u>")
    add(title="Strikethrough s", categories=["basic"], html_in="<s>strike</s>")
    add(title="Strikethrough del", categories=["basic"], html_in="<del>deleted</del>")
    add(title="Code inline", categories=["basic", "code"], html_in="<code>x = 1</code>")
    add(
        title="Pre block",
        categories=["basic", "code"],
        html_in="<pre>line1\nline2</pre>",
    )
    add(
        title="Blockquote",
        categories=["basic", "quote"],
        html_in="<blockquote>quoted</blockquote>",
    )
    add(
        title="Spoiler tg-spoiler",
        categories=["basic", "spoiler"],
        html_in="<tg-spoiler>secret</tg-spoiler>",
        mx=net_render("secret", warnings=["unsupported:tg-spoiler"]),
        notes="Max не документирует spoiler — снимаем тег с предупреждением.",
    )

    # --- nested ---
    add(title="Nested bold>italic", categories=["nested"], html_in="<b>bold <i>and italic</i></b>")
    add(title="Nested italic>bold", categories=["nested"], html_in="<i>italic <b>and bold</b></i>")
    add(
        title="Nested bold>italic>underline",
        categories=["nested"],
        html_in="<b>A <i>B <u>C</u></i></b>",
    )
    add(
        title="Nested code inside bold",
        categories=["nested", "code"],
        html_in="<b>run <code>npm test</code></b>",
    )
    add(
        title="Link inside italic",
        categories=["nested", "links"],
        html_in='<i>see <a href="https://example.com">site</a></i>',
    )
    add(
        title="Spoiler inside bold",
        categories=["nested", "spoiler"],
        html_in="<b>open <tg-spoiler>hidden</tg-spoiler></b>",
        mx=net_render("<b>open hidden</b>", warnings=["unsupported:tg-spoiler"]),
    )
    add(
        title="Quote with nested formatting",
        categories=["nested", "quote"],
        html_in="<blockquote><b>Важно:</b> <i>проверить</i></blockquote>",
    )
    add(
        title="Triple nest strike/code/link",
        categories=["nested", "links", "code"],
        html_in='<s>old <code>api</code> → <a href="https://posts.example/docs">docs</a></s>',
    )

    # --- specialchars ---
    add(
        title="Ampersand escape",
        categories=["specialchars"],
        html_in="A &amp; B",
    )
    add(
        title="Less-than and greater-than escaped",
        categories=["specialchars"],
        html_in="1 &lt; 2 &amp;&amp; 3 &gt; 0",
    )
    add(
        title="Quotes in text",
        categories=["specialchars"],
        html_in="Он сказал: &quot;привет&quot;",
    )
    add(
        title="Raw angle brackets in code",
        categories=["specialchars", "code"],
        html_in="<code>&lt;script&gt;</code>",
    )
    add(
        title="HTML entities nbsp and mdash",
        categories=["specialchars"],
        html_in="цена&nbsp;—&nbsp;990&nbsp;₽",
    )
    add(
        title="Mixed markup and ampersand",
        categories=["specialchars", "mixed"],
        html_in="<b>VK &amp; OK</b> plain",
    )

    # --- emoji ---
    add(title="Single emoji", categories=["emoji"], html_in="🚀")
    add(title="Emoji with text", categories=["emoji"], html_in="Пост готов ✅")
    add(
        title="Emoji ZWJ family",
        categories=["emoji"],
        html_in="👨‍👩‍👧‍👦 семья",
    )
    add(
        title="Emoji inside bold",
        categories=["emoji", "nested"],
        html_in="<b>🔥 sale</b>",
    )
    add(
        title="Skin tone emoji",
        categories=["emoji"],
        html_in="👍🏻 mid",
    )

    # --- links ---
    add(
        title="Simple https link",
        categories=["links"],
        html_in='<a href="https://example.com">Example</a>',
    )
    add(
        title="Link with query and hash",
        categories=["links"],
        html_in='<a href="https://example.com/path?x=1&amp;y=2#frag">Q</a>',
    )
    add(
        title="Link with title text special chars",
        categories=["links", "specialchars"],
        html_in='<a href="https://example.com">A &amp; B &lt;C&gt;</a>',
    )
    add(
        title="mailto link",
        categories=["links"],
        html_in='<a href="mailto:qa@posts.test">mail</a>',
    )
    add(
        title="Multiple links in paragraph",
        categories=["links", "mixed"],
        html_in='См. <a href="https://a.example">A</a> и <a href="https://b.example">B</a>',
    )

    # --- Max headings / unsupported TG features ---
    add(
        title="Max heading h1 kept; TG strips unsupported heading",
        categories=["unsupported", "mixed"],
        html_in="<h1>Заголовок</h1>",
        tg=net_render("Заголовок", warnings=["unsupported:h1"]),
        mx=net_render("<h1>Заголовок</h1>"),
        notes="Max supports heading; Telegram HTML subset does not include h1.",
    )
    add(
        title="Max mark/highlight; TG strips mark",
        categories=["unsupported"],
        html_in="<mark>highlight</mark>",
        tg=net_render("highlight", warnings=["unsupported:mark"]),
        mx=net_render("<mark>highlight</mark>"),
    )
    add(
        title="span tg-spoiler attribute form",
        categories=["spoiler"],
        html_in='<span class="tg-spoiler">hidden2</span>',
        mx=net_render("hidden2", warnings=["unsupported:span.tg-spoiler"]),
    )

    # --- whitespace ---
    add(title="Empty string", categories=["whitespace"], html_in="")
    add(title="Only spaces", categories=["whitespace"], html_in="   ")
    add(
        title="Leading/trailing newlines in pre",
        categories=["whitespace", "code"],
        html_in="<pre>\n  indented\n</pre>",
    )
    add(
        title="Multiple paragraphs via br",
        categories=["whitespace", "mixed"],
        html_in="line1<br/>line2",
    )

    # --- limits from network-capabilities ---
    long_tg_ok = "T" * 4096
    add(
        title="Telegram exactly 4096 plain chars",
        categories=["limits"],
        html_in=long_tg_ok,
        notes="Граница telegram.text.max_chars=4096 (network-capabilities).",
    )
    add(
        title="Telegram 4097 chars exceeds limit",
        categories=["limits"],
        html_in="T" * 4097,
        notes="exceeds_telegram=true; сохранение варианта должно блокироваться (US-4.1 AC2).",
    )
    add(
        title="Max exactly 4000 plain chars",
        categories=["limits"],
        html_in="M" * 4000,
        notes="Граница max.text.max_chars=4000.",
    )
    add(
        title="Max 4001 chars exceeds limit",
        categories=["limits"],
        html_in="M" * 4001,
    )
    add(
        title="Caption boundary 1024 (telegram caption)",
        categories=["limits"],
        html_in="C" * 1024,
        notes="telegram caption_max_chars=1024; exceeds_telegram_caption=false на границе.",
    )
    add(
        title="Caption 1025 exceeds telegram caption",
        categories=["limits"],
        html_in="C" * 1025,
        notes="Для медиа-подписи; exceeds_telegram_caption=true.",
    )
    add(
        title="Bold wrapping near TG limit still counts plain length",
        categories=["limits", "nested"],
        html_in="<b>" + ("X" * 4090) + "</b>",
        notes="plain_length=4090 ≤ 4096; теги не входят в лимит entities/plain.",
    )
    add(
        title="Content valid for TG but exceeds Max (4000+)",
        categories=["limits", "mixed"],
        html_in="Z" * 4050,
        notes="TG OK (≤4096), Max invalid (>4000).",
    )

    # --- mixed / realistic ---
    add(
        title="Campaign blurb with link emoji and bold",
        categories=["mixed", "emoji", "links"],
        html_in='🔥 <b>Акция</b>: скидка на <a href="https://posts.example/pro">Про</a>',
    )
    add(
        title="Code sample with specials and link",
        categories=["mixed", "code", "links", "specialchars"],
        html_in='Используйте <code>curl &amp; jq</code> — <a href="https://example.com">docs</a>',
    )
    add(
        title="Nested quote spoiler link emoji",
        categories=["mixed", "nested", "spoiler", "links", "emoji", "quote"],
        html_in='<blockquote>⚠️ <b>spoiler</b> <tg-spoiler>секрет</tg-spoiler> <a href="https://x.test">x</a></blockquote>',
        mx=net_render(
            '<blockquote>⚠️ <b>spoiler</b> секрет <a href="https://x.test">x</a></blockquote>',
            warnings=["unsupported:tg-spoiler"],
        ),
    )
    add(
        title="Strong/em aliases",
        categories=["basic", "mixed"],
        html_in="<strong>S</strong> <em>E</em>",
    )
    add(
        title="Ins/u synonyms underline",
        categories=["basic"],
        html_in="<ins>inserted</ins>",
    )
    add(
        title="Strike synonym",
        categories=["basic"],
        html_in="<strike>old</strike>",
    )
    add(
        title="Pre with code language hint as text",
        categories=["code"],
        html_in="<pre>SELECT 1 &lt; 2;</pre>",
    )
    add(
        title="URL-only autolink-looking text without anchor",
        categories=["links", "basic"],
        html_in="https://example.com/path",
        notes="Без <a>; plain URL остаётся текстом (автолинк — зона сети, не рендера).",
    )
    add(
        title="Empty bold tags",
        categories=["whitespace", "basic"],
        html_in="<b></b>",
    )
    add(
        title="CJK and emoji mixed",
        categories=["emoji", "mixed"],
        html_in="日本語テスト 🎌",
    )
    add(
        title="RTL arabic snippet",
        categories=["mixed"],
        html_in="<b>مرحبا</b> world",
    )
    add(
        title="OK/VK rich paste becomes plain with warning",
        categories=["plain-networks", "mixed"],
        html_in="<b>Жирный</b> и <i>курсив</i>",
        notes="VK/OK: formatting_stripped=true + warning (US-4.3 AC3).",
    )

    assert len(cases) >= 50, len(cases)
    return cases


def validate_ids(cases: list[dict]) -> None:
    ids = [c["id"] for c in cases]
    assert len(ids) == len(set(ids))
    cats_needed = {
        "nested",
        "specialchars",
        "emoji",
        "links",
        "limits",
    }
    present = {c for case in cases for c in case["categories"]}
    missing = cats_needed - present
    assert not missing, missing


def main() -> int:
    cases = build_cases()
    validate_ids(cases)
    payload = {
        "version": "1.0.0",
        "task": "QA-4",
        "updated": "2026-11-17",
        "schema": "schema.json",
        "limits_source": "docs/api/fixtures/network-capabilities.yaml",
        "count": len(cases),
        "cases": cases,
    }
    OUT.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Wrote {OUT} with {len(cases)} cases")
    # Category coverage summary
    from collections import Counter

    c = Counter()
    for case in cases:
        c.update(case["categories"])
    print(json.dumps(dict(c), ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
