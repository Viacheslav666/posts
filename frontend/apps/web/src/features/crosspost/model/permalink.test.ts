import { describe, expect, it } from 'vitest';

import { buildSourcePermalink } from './permalink';

describe('buildSourcePermalink', () => {
  it('builds VK wall link from remote id and externalId', () => {
    expect(
      buildSourcePermalink('vk', '42', { network: 'vk', externalId: '123456' }),
    ).toBe('https://vk.com/wall-123456_42');
  });

  it('passes through wall-owner_post remote ids', () => {
    expect(buildSourcePermalink('vk', 'wall-99_7')).toBe('https://vk.com/wall-99_7');
  });

  it('builds Telegram permalink from username', () => {
    expect(
      buildSourcePermalink('tg', '15', { network: 'tg', username: '@posts_news' }),
    ).toBe('https://t.me/posts_news/15');
  });

  it('returns null for Max without absolute URL', () => {
    expect(buildSourcePermalink('max', 'abc')).toBeNull();
  });

  it('returns absolute URLs as-is', () => {
    expect(buildSourcePermalink('max', 'https://max.ru/post/1')).toBe('https://max.ru/post/1');
  });

  it('returns null when remote id is empty', () => {
    expect(buildSourcePermalink('vk', '')).toBeNull();
  });
});
