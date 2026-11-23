/**
 * Normalize vposter-style source input: comma/space separated IDs, club/public prefixes, VK URLs.
 * Numeric club/public ids become negative owner ids (`club123` → `-123`), matching the API.
 */
const CLUB_RE = /^club(\d+)$/i;
const PUBLIC_RE = /^public(\d+)$/i;
const SOURCE_ID_RE = /^-?\d+$|^[A-Za-z0-9._]+$/;

export function normalizeSourceToken(raw: string): string | null {
  let value = raw.trim();
  if (!value) return null;
  if (value.startsWith('@')) value = value.slice(1);
  value = stripVkUrl(value);
  if (value.startsWith('@')) value = value.slice(1);
  const club = CLUB_RE.exec(value);
  if (club) return `-${club[1]}`;
  const community = PUBLIC_RE.exec(value);
  if (community) return `-${community[1]}`;
  if (SOURCE_ID_RE.test(value)) return value;
  return null;
}

export function normalizeSourceIds(raw: string): string[] {
  const out: string[] = [];
  for (const part of raw.split(/[\s,;]+/)) {
    const trimmed = part.trim();
    if (!trimmed) continue;
    const normalized = normalizeSourceToken(trimmed) ?? trimmed;
    if (!out.includes(normalized)) out.push(normalized);
  }
  return out;
}

export function sourceIdsToTextarea(ids: string[] | undefined): string {
  return (ids ?? []).join(', ');
}

export function isValidSourceId(value: string): boolean {
  const normalized = normalizeSourceToken(value);
  return normalized != null && SOURCE_ID_RE.test(normalized);
}

function stripVkUrl(value: string): string {
  let v = value.replace(/^https?:\/\//i, '');
  v = v.replace(/^www\./i, '');
  v = v.replace(/^(vk\.com|vk\.ru)\//i, '');
  const cut = v.search(/[?#]/);
  if (cut >= 0) v = v.slice(0, cut);
  return v.replace(/\/$/, '');
}
