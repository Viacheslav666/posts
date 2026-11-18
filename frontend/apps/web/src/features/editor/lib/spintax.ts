/**
 * Client-side `[rand]a|b|c[/rand]` expander mirroring BE SpintaxExpander.
 * Same seed → same expansion (publish retries / preview "stable" mode).
 */

const RAND = /\[rand](.*?)\[\/rand]/gi;

function sha256SyncFallback(seed: string): number[] {
  // Lightweight FNV-1a inspired digest for browser sync preview.
  // Server uses SHA-256; for UI "another variant" we intentionally change the seed.
  const bytes = new Array<number>(32).fill(0);
  let h = 2166136261;
  for (let i = 0; i < seed.length; i += 1) {
    h ^= seed.charCodeAt(i);
    h = Math.imul(h, 16777619);
    bytes[i % 32] = (bytes[i % 32]! + (h >>> 0)) & 0xff;
  }
  for (let i = 0; i < 32; i += 1) {
    h = Math.imul(h ^ bytes[i]!, 16777619);
    bytes[i] = (h >>> 0) & 0xff;
  }
  return bytes;
}

async function sha256Bytes(seed: string): Promise<number[]> {
  if (typeof crypto !== 'undefined' && crypto.subtle) {
    const data = new TextEncoder().encode(seed);
    const digest = await crypto.subtle.digest('SHA-256', data);
    return [...new Uint8Array(digest)];
  }
  return sha256SyncFallback(seed);
}

function pickFromDigest(digest: number[], bound: number, occurrence: number): number {
  if (bound <= 0) return 0;
  const offset = Math.floor(((occurrence * 4) % Math.max(1, digest.length - 3)));
  const value =
    ((digest[offset]! & 0xff) << 24) |
    ((digest[offset + 1]! & 0xff) << 16) |
    ((digest[offset + 2]! & 0xff) << 8) |
    (digest[offset + 3]! & 0xff);
  return ((value % bound) + bound) % bound;
}

function splitOptions(body: string): string[] {
  const out: string[] = [];
  let current = '';
  for (const char of body) {
    if (char === '|') {
      out.push(current);
      current = '';
    } else {
      current += char;
    }
  }
  out.push(current);
  return out;
}

export function containsSpintax(template: string | null | undefined): boolean {
  if (!template) return false;
  RAND.lastIndex = 0;
  return RAND.test(template);
}

/** Synchronous expansion with a digest-like seed (UI / Vitest). */
export function expandSpintax(template: string, seed = 'preview'): string {
  if (!template) return template ?? '';
  const digest = sha256SyncFallback(seed);
  let occurrence = 0;
  return template.replace(RAND, (_match, body: string) => {
    const options = splitOptions(body ?? '');
    if (options.length === 0) {
      occurrence += 1;
      return '';
    }
    const idx = pickFromDigest(digest, options.length, occurrence);
    occurrence += 1;
    return options[idx] ?? '';
  });
}

/** Async expansion using Web Crypto SHA-256 (closer to BE). */
export async function expandSpintaxAsync(template: string, seed = 'preview'): Promise<string> {
  if (!template) return template ?? '';
  const digest = await sha256Bytes(seed);
  let occurrence = 0;
  return template.replace(RAND, (_match, body: string) => {
    const options = splitOptions(body ?? '');
    if (options.length === 0) {
      occurrence += 1;
      return '';
    }
    const idx = pickFromDigest(digest, options.length, occurrence);
    occurrence += 1;
    return options[idx] ?? '';
  });
}

export const SPINTAX_SNIPPET = '[rand]вариант 1|вариант 2[/rand]';
