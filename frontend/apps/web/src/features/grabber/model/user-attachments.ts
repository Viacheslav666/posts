import { invalidVkStringAttachments } from '@/features/editor/lib/vk-string-attachments';

/**
 * «Свои вложения» for grabbed posts without attachments — mirrors BE UserAttachmentsSpec.
 * `photo-1_2, video-1_3`, `[rand]a|b[/rand]`, `[keyTextEntry]photo-1_2|прикол,смеят[/keyTextEntry]`.
 */
export const USER_ATTACHMENTS_MAX_LENGTH = 4000;

export type UserAttachmentsError =
  | 'userAttachmentsTooLong'
  | 'userAttachmentsNested'
  | 'userAttachmentsRandVariants'
  | 'userAttachmentsKeyTextFormat'
  | 'userAttachmentsUnclosed'
  | 'userAttachmentsInvalid';

const BLOCK = /\[(rand|keyTextEntry)\]([\s\S]*?)\[\/\1\]/gi;

function splitRefs(raw: string): string[] {
  return raw
    .trim()
    .split(/[,;\s]+/)
    .map((token) => token.trim())
    .filter(Boolean);
}

function keywords(raw: string): string[] {
  return raw
    .split(',')
    .map((word) => word.trim())
    .filter(Boolean);
}

/** First validation error code, or null when the spec is blank or valid. */
export function validateUserAttachments(spec: string | null | undefined): UserAttachmentsError | null {
  if (!spec || !spec.trim()) return null;
  if (spec.length > USER_ATTACHMENTS_MAX_LENGTH) return 'userAttachmentsTooLong';

  let rest = '';
  let last = 0;
  for (const match of spec.matchAll(BLOCK)) {
    const index = match.index ?? 0;
    rest += `${spec.slice(last, index)},`;
    last = index + match[0].length;
    const tag = (match[1] ?? '').toLowerCase();
    const body = match[2] ?? '';
    if (body.includes('[')) return 'userAttachmentsNested';
    if (tag === 'rand') {
      const variants = body.split('|');
      if (variants.length < 2) return 'userAttachmentsRandVariants';
      for (const variant of variants) {
        const refs = splitRefs(variant);
        if (refs.length === 0) return 'userAttachmentsRandVariants';
        if (invalidVkStringAttachments(refs).length) return 'userAttachmentsInvalid';
      }
    } else {
      const bar = body.lastIndexOf('|');
      if (bar < 0) return 'userAttachmentsKeyTextFormat';
      const refs = splitRefs(body.slice(0, bar));
      if (refs.length === 0 || keywords(body.slice(bar + 1)).length === 0) {
        return 'userAttachmentsKeyTextFormat';
      }
      if (invalidVkStringAttachments(refs).length) return 'userAttachmentsInvalid';
    }
  }
  rest += spec.slice(last);
  if (rest.includes('[') || rest.includes(']')) return 'userAttachmentsUnclosed';
  if (invalidVkStringAttachments(splitRefs(rest)).length) return 'userAttachmentsInvalid';
  return null;
}
