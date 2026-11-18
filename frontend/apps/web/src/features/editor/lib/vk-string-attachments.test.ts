import { describe, expect, it } from 'vitest';

import {
  invalidVkStringAttachments,
  isValidVkStringAttachment,
  normalizeVkStringAttachments,
  parseVkAttachmentsInput,
} from './vk-string-attachments';

describe('vk-string-attachments', () => {
  it('accepts common VK tokens', () => {
    expect(isValidVkStringAttachment('photo-1_2')).toBe(true);
    expect(isValidVkStringAttachment('video123_456')).toBe(true);
    expect(isValidVkStringAttachment('doc1_2_accesskey')).toBe(true);
    expect(isValidVkStringAttachment('clip-1_2')).toBe(true);
  });

  it('rejects malformed tokens', () => {
    expect(isValidVkStringAttachment('photo')).toBe(false);
    expect(isValidVkStringAttachment('photo_1')).toBe(false);
    expect(isValidVkStringAttachment('http://example.com')).toBe(false);
    expect(invalidVkStringAttachments(['photo-1_2', 'bad', 'video1_2'])).toEqual(['bad']);
  });

  it('normalizes type casing', () => {
    expect(normalizeVkStringAttachments(['PHOTO-1_2'])).toEqual(['photo-1_2']);
  });

  it('parses comma/space separated input', () => {
    expect(parseVkAttachmentsInput('photo-1_2, video2_3; clip-4_5')).toEqual([
      'photo-1_2',
      'video2_3',
      'clip-4_5',
    ]);
  });
});
