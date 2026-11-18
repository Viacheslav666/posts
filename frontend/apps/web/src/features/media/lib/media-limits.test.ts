import { describe, expect, it } from 'vitest';

import type { EditorMediaItem } from '../model/types';
import { validateMediaAgainstCapabilities } from './media-limits';

const capabilities = {
  version: '1.0.0',
  checkedAt: '2026-11-17',
  networks: [
    {
      id: 'vk' as const,
      name: 'ВКонтакте',
      media: {
        maxAttachmentsPerPost: 10,
        photo: {
          max_count_per_post: { value: 10 },
          max_file_bytes: { value: 50 * 1024 * 1024 },
          formats: { value: ['JPG', 'PNG', 'GIF'] },
        },
        video: {
          max_count_per_post: { value: 10 },
        },
      },
    },
    {
      id: 'tg' as const,
      name: 'Telegram',
      media: {
        maxAttachmentsPerPost: 10,
        photo: {
          max_count_per_post: { value: 10 },
          formats: { value: ['JPG', 'PNG', 'WEBP'] },
        },
      },
    },
  ],
};

function item(overrides: Partial<EditorMediaItem> & Pick<EditorMediaItem, 'id' | 'filename'>): EditorMediaItem {
  return {
    mediaId: overrides.mediaId ?? overrides.id,
    kind: 'image',
    mime: 'image/jpeg',
    sizeBytes: 1024,
    ...overrides,
  };
}

describe('validateMediaAgainstCapabilities', () => {
  it('passes when within limits', () => {
    const issues = validateMediaAgainstCapabilities(
      [item({ id: '1', filename: 'a.jpg' })],
      capabilities,
      ['vk'],
    );
    expect(issues).toEqual([]);
  });

  it('flags too many attachments', () => {
    const items = Array.from({ length: 11 }, (_, i) =>
      item({ id: String(i), filename: `a${i}.jpg` }),
    );
    const issues = validateMediaAgainstCapabilities(items, capabilities, ['vk']);
    expect(issues.some((issue) => issue.code === 'media.max_attachments')).toBe(true);
  });

  it('flags unsupported photo format for network', () => {
    const issues = validateMediaAgainstCapabilities(
      [item({ id: '1', filename: 'a.webp', mime: 'image/webp' })],
      capabilities,
      ['vk'],
    );
    expect(issues.some((issue) => issue.code === 'media.photo_format')).toBe(true);
  });

  it('flags oversized photo', () => {
    const issues = validateMediaAgainstCapabilities(
      [item({ id: '1', filename: 'big.jpg', sizeBytes: 60 * 1024 * 1024 })],
      capabilities,
      ['vk'],
    );
    expect(issues.some((issue) => issue.code === 'media.photo_size')).toBe(true);
  });

  it('validates only selected networks', () => {
    const issues = validateMediaAgainstCapabilities(
      [item({ id: '1', filename: 'a.webp', mime: 'image/webp' })],
      capabilities,
      ['tg'],
    );
    expect(issues).toEqual([]);
  });
});
