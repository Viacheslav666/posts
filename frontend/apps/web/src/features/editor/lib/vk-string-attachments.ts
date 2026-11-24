/**
 * VK string attachment tokens — mirrors BE VkStringAttachmentValidator.
 * Examples: photo-1_2, video123_456, doc1_2_accesskey, clip-1_2
 */

const ATTACHMENT =
  /^(photo|video|audio|doc|poll|market|album|page|note|clip|audio_playlist|market_album)-?\d+_\d+(?:_[A-Za-z0-9_-]+)?$/i;

/** wall.post also accepts one link snippet given as an http(s) URL. */
const LINK = /^https?:\/\/[^\s,<>"']+$/i;

/** Soft UI cap (backend validates format; OpenAPI has no maxItems). */
export const VK_STRING_ATTACHMENTS_MAX = 10;

export function isValidVkStringAttachment(token: string | null | undefined): boolean {
  if (!token || !token.trim()) return false;
  const trimmed = token.trim();
  return ATTACHMENT.test(trimmed) || LINK.test(trimmed);
}

export function invalidVkStringAttachments(attachments: string[] | null | undefined): string[] {
  if (!attachments?.length) return [];
  const invalid: string[] = [];
  for (const raw of attachments) {
    if (raw == null || !String(raw).trim()) continue;
    const token = String(raw).trim();
    if (!isValidVkStringAttachment(token)) {
      invalid.push(token);
    }
  }
  return invalid;
}

export function normalizeVkStringAttachments(attachments: string[] | null | undefined): string[] {
  if (!attachments?.length) return [];
  const out: string[] = [];
  for (const raw of attachments) {
    if (raw == null || !String(raw).trim()) continue;
    let token = String(raw).trim();
    if (LINK.test(token)) {
      out.push(token);
      continue;
    }
    const us = token.indexOf('_');
    if (us > 0) {
      const typeAndOwner = token.slice(0, us);
      const digit = firstDigitOrMinus(typeAndOwner);
      if (digit > 0) {
        const type = typeAndOwner.slice(0, digit).toLowerCase();
        token = type + typeAndOwner.slice(digit) + token.slice(us);
      }
    }
    out.push(token);
  }
  return out;
}

function firstDigitOrMinus(s: string): number {
  for (let i = 0; i < s.length; i += 1) {
    const c = s[i]!;
    if (c === '-' || (c >= '0' && c <= '9')) return i;
  }
  return -1;
}

export function parseVkAttachmentsInput(value: string): string[] {
  return value
    .split(/[\s,;]+/)
    .map((item) => item.trim())
    .filter(Boolean)
    .slice(0, VK_STRING_ATTACHMENTS_MAX);
}
