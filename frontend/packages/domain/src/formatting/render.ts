import { hasMarkupTags, stripTags, unicodeLength } from './html-utils';
import {
  MAX_MAX_CHARS,
  PLAIN_FORMATTING_WARNING,
  TELEGRAM_CAPTION_MAX_CHARS,
  TELEGRAM_MAX_CHARS,
  type FormattingRenderResult,
  type HtmlNetworkRender,
  type PlainNetworkRender,
} from './types';

const TELEGRAM_TAGS = new Set([
  'b',
  'strong',
  'i',
  'em',
  'u',
  'ins',
  's',
  'strike',
  'del',
  'span',
  'tg-spoiler',
  'a',
  'code',
  'pre',
  'blockquote',
  'br',
]);

const MAX_TAGS = new Set([
  'b',
  'strong',
  'i',
  'em',
  'u',
  'ins',
  's',
  'strike',
  'del',
  'code',
  'pre',
  'a',
  'h1',
  'h2',
  'h3',
  'h4',
  'h5',
  'h6',
  'blockquote',
  'mark',
  'br',
]);

type TagToken =
  | { kind: 'text'; value: string }
  | { kind: 'open'; name: string; raw: string; attrs: string }
  | { kind: 'close'; name: string; raw: string }
  | { kind: 'self'; name: string; raw: string };

function isNameChar(ch: string): boolean {
  return (
    (ch >= 'a' && ch <= 'z') ||
    (ch >= 'A' && ch <= 'Z') ||
    (ch >= '0' && ch <= '9') ||
    ch === '_' ||
    ch === ':' ||
    ch === '-'
  );
}

/**
 * Linear HTML tokenizer (no nested/quantified regex) for canonical editor markup.
 */
function tokenize(html: string): TagToken[] {
  const tokens: TagToken[] = [];
  let i = 0;
  let textStart = 0;

  while (i < html.length) {
    if (html[i] !== '<') {
      i += 1;
      continue;
    }

    const tagStart = i;
    let j = i + 1;
    let closing = false;
    if (j < html.length && html[j] === '/') {
      closing = true;
      j += 1;
    }
    if (j >= html.length || !((html[j] >= 'a' && html[j] <= 'z') || (html[j] >= 'A' && html[j] <= 'Z'))) {
      i += 1;
      continue;
    }

    if (tagStart > textStart) {
      tokens.push({ kind: 'text', value: html.slice(textStart, tagStart) });
    }

    const nameStart = j;
    while (j < html.length && isNameChar(html[j])) {
      j += 1;
    }
    const name = html.slice(nameStart, j).toLowerCase();
    const attrsStart = j;
    while (j < html.length && html[j] !== '>') {
      j += 1;
    }
    if (j >= html.length) {
      // Incomplete tag — treat remainder as text.
      tokens.push({ kind: 'text', value: html.slice(tagStart) });
      return tokens;
    }
    let attrs = html.slice(attrsStart, j);
    let selfClosing = false;
    const trimmedEnd = attrs.trimEnd();
    if (trimmedEnd.endsWith('/')) {
      selfClosing = true;
      attrs = trimmedEnd.slice(0, -1);
    }
    const raw = html.slice(tagStart, j + 1);
    i = j + 1;
    textStart = i;

    if (closing) {
      tokens.push({ kind: 'close', name, raw });
    } else if (selfClosing || name === 'br') {
      tokens.push({ kind: 'self', name, raw });
    } else {
      tokens.push({ kind: 'open', name, raw, attrs });
    }
  }

  if (textStart < html.length) {
    tokens.push({ kind: 'text', value: html.slice(textStart) });
  }
  return tokens;
}

function isTgSpoilerSpan(name: string, attrs: string): boolean {
  if (name !== 'span') {
    return false;
  }
  const lower = attrs.toLowerCase();
  return lower.includes('class') && lower.includes('tg-spoiler');
}

function hasExpandableAttr(attrs: string): boolean {
  const lower = attrs.toLowerCase();
  let idx = 0;
  while (idx < lower.length) {
    const found = lower.indexOf('expandable', idx);
    if (found === -1) {
      return false;
    }
    const beforeOk = found === 0 || lower[found - 1] === ' ' || lower[found - 1] === '\t';
    const after = found + 'expandable'.length;
    const afterOk =
      after >= lower.length ||
      lower[after] === ' ' ||
      lower[after] === '\t' ||
      lower[after] === '=';
    if (beforeOk && afterOk) {
      return true;
    }
    idx = found + 1;
  }
  return false;
}

function stripExpandableAttr(_rawOpen: string): string {
  // Canonical form for Max: drop expandable attribute entirely.
  return '<blockquote>';
}

type TransformResult = { html: string; warnings: string[] };

function transformForNetwork(html: string, network: 'telegram' | 'max'): TransformResult {
  const allowed = network === 'telegram' ? TELEGRAM_TAGS : MAX_TAGS;
  const warnings: string[] = [];
  const seen = new Set<string>();

  const addWarning = (code: string) => {
    if (!seen.has(code)) {
      seen.add(code);
      warnings.push(code);
    }
  };

  let out = '';
  for (const token of tokenize(html)) {
    if (token.kind === 'text') {
      out += token.value;
      continue;
    }

    if (token.kind === 'self') {
      if (allowed.has(token.name)) {
        out += token.raw;
      } else {
        addWarning(`unsupported:${token.name}`);
      }
      continue;
    }

    if (token.kind === 'open') {
      if (network === 'max' && token.name === 'tg-spoiler') {
        addWarning('unsupported:tg-spoiler');
        continue;
      }
      if (network === 'max' && isTgSpoilerSpan(token.name, token.attrs)) {
        addWarning('unsupported:span.tg-spoiler');
        continue;
      }
      if (network === 'max' && token.name === 'blockquote' && hasExpandableAttr(token.attrs)) {
        addWarning('unsupported:blockquote.expandable');
        out += stripExpandableAttr(token.raw);
        continue;
      }
      if (!allowed.has(token.name)) {
        addWarning(`unsupported:${token.name}`);
        continue;
      }
      out += token.raw;
      continue;
    }

    // close
    if (network === 'max' && token.name === 'tg-spoiler') {
      continue;
    }
    if (network === 'max' && token.name === 'span') {
      if (!allowed.has('span')) {
        continue;
      }
    }
    if (!allowed.has(token.name)) {
      continue;
    }
    out += token.raw;
  }

  return { html: out, warnings };
}

function htmlNetworkRender(
  sourceHtml: string,
  network: 'telegram' | 'max',
  maxChars: number,
  limitWarning: string,
): HtmlNetworkRender {
  const { html, warnings } = transformForNetwork(sourceHtml, network);
  const plain_text = stripTags(html);
  const plain_length = unicodeLength(plain_text);
  const exceeds = plain_length > maxChars;
  const allWarnings = [...warnings];
  if (exceeds) {
    allWarnings.push(limitWarning);
  }
  return {
    html,
    plain_text,
    plain_length,
    valid: !exceeds,
    warnings: allWarnings,
  };
}

function plainNetworkRender(sourceHtml: string): PlainNetworkRender {
  const plain = stripTags(sourceHtml);
  const formatting_stripped = hasMarkupTags(sourceHtml);
  if (formatting_stripped) {
    return {
      plain,
      formatting_stripped: true,
      warning: PLAIN_FORMATTING_WARNING,
    };
  }
  return { plain, formatting_stripped: false };
}

/**
 * Render canonical editor HTML into network-specific preview payloads.
 * Matches etalon fixtures in `docs/api/fixtures/formatting/cases.json`.
 */
export function renderFromCanonicalHtml(canonicalHtml: string): FormattingRenderResult {
  const telegram = htmlNetworkRender(
    canonicalHtml,
    'telegram',
    TELEGRAM_MAX_CHARS,
    'limit:telegram_max_chars',
  );
  const max = htmlNetworkRender(canonicalHtml, 'max', MAX_MAX_CHARS, 'limit:max_max_chars');
  const vk = plainNetworkRender(canonicalHtml);
  const ok = plainNetworkRender(canonicalHtml);

  return {
    telegram,
    max,
    vk,
    ok,
    limits: {
      telegram_max_chars: TELEGRAM_MAX_CHARS,
      max_max_chars: MAX_MAX_CHARS,
      telegram_caption_max_chars: TELEGRAM_CAPTION_MAX_CHARS,
      exceeds_telegram: telegram.plain_length > TELEGRAM_MAX_CHARS,
      exceeds_max: max.plain_length > MAX_MAX_CHARS,
      exceeds_telegram_caption: telegram.plain_length > TELEGRAM_CAPTION_MAX_CHARS,
    },
  };
}

/** Convenience: pick one network from a full render. */
export function renderForPreviewNetwork(
  canonicalHtml: string,
  network: 'telegram' | 'max' | 'vk' | 'ok',
): HtmlNetworkRender | PlainNetworkRender {
  const all = renderFromCanonicalHtml(canonicalHtml);
  return all[network];
}
