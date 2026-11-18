/**
 * Normalize vposter-style source input: comma/space separated IDs and @screen_names.
 */
export function normalizeSourceIds(raw: string): string[] {
  return raw
    .split(/[\s,;]+/)
    .map((part) => part.trim())
    .filter(Boolean)
    .map((part) => (part.startsWith('@') ? part.slice(1) : part))
    .filter((part, index, all) => all.indexOf(part) === index);
}

export function sourceIdsToTextarea(ids: string[] | undefined): string {
  return (ids ?? []).join(', ');
}

const SOURCE_ID_RE = /^-?\d+$|^[A-Za-z0-9._]+$/;

export function isValidSourceId(value: string): boolean {
  return SOURCE_ID_RE.test(value);
}
