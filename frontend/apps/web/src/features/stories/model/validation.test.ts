import { describe, expect, it } from 'vitest';

import type { EditorChannelOption } from '@/features/channels/hooks/use-workspace-channels';

import { createStoryDraft } from './defaults';
import { isValidVkStoryLinkUrl, validateStoryDraft } from './validation';

const vkReady: EditorChannelOption = {
  id: 'vk-1',
  name: 'VK Ready',
  network: 'vk',
  projectName: 'Demo',
  storyConnectionStatus: 'ready',
};

const vkMissing: EditorChannelOption = {
  id: 'vk-2',
  name: 'VK Missing',
  network: 'vk',
  projectName: 'Demo',
  storyConnectionStatus: 'missing',
};

const tg: EditorChannelOption = {
  id: 'tg-1',
  name: 'TG',
  network: 'tg',
  projectName: 'Demo',
};

const ok: EditorChannelOption = {
  id: 'ok-1',
  name: 'OK',
  network: 'ok',
  projectName: 'Demo',
};

describe('isValidVkStoryLinkUrl', () => {
  it('accepts vk.com / vk.ru HTTPS URLs', () => {
    expect(isValidVkStoryLinkUrl('https://vk.com/club1')).toBe(true);
    expect(isValidVkStoryLinkUrl('https://vk.ru/club1')).toBe(true);
  });

  it('rejects external URLs', () => {
    expect(isValidVkStoryLinkUrl('https://example.com')).toBe(false);
    expect(isValidVkStoryLinkUrl('http://vk.com/club1')).toBe(false);
  });
});

describe('validateStoryDraft', () => {
  it('requires media and channels', () => {
    const draft = createStoryDraft('ws');
    const issues = validateStoryDraft(draft, [vkReady]);
    expect(issues.some((issue) => issue.code === 'story_media_invalid')).toBe(true);
    expect(issues.some((issue) => issue.messageKey === 'validation.channelsRequired')).toBe(true);
  });

  it('blocks VK without story connect', () => {
    const draft = createStoryDraft('ws');
    draft.selectedChannelIds = [vkMissing.id];
    draft.media = {
      id: 'm1',
      mediaId: 'asset-1',
      kind: 'image',
      mime: 'image/jpeg',
      filename: 'story.jpg',
    };
    const issues = validateStoryDraft(draft, [vkMissing]);
    expect(issues.some((issue) => issue.code === 'story_connection_required')).toBe(true);
  });

  it('rejects telegram as story target', () => {
    const draft = createStoryDraft('ws');
    draft.selectedChannelIds = [tg.id];
    draft.media = {
      id: 'm1',
      mediaId: 'asset-1',
      kind: 'image',
      mime: 'image/jpeg',
      filename: 'story.jpg',
    };
    const issues = validateStoryDraft(draft, [tg]);
    expect(issues.some((issue) => issue.code === 'story_unsupported_network')).toBe(true);
  });

  it('allows OK without story connect and validates VK link', () => {
    const draft = createStoryDraft('ws');
    draft.selectedChannelIds = [ok.id, vkReady.id];
    draft.media = {
      id: 'm1',
      mediaId: 'asset-1',
      kind: 'image',
      mime: 'image/jpeg',
      filename: 'story.jpg',
    };
    draft.linkText = 'go_to';
    draft.linkUrl = 'https://example.com';
    const issues = validateStoryDraft(draft, [ok, vkReady]);
    expect(issues.some((issue) => issue.code === 'story_link_invalid')).toBe(true);

    draft.linkUrl = 'https://vk.com/club1';
    const okIssues = validateStoryDraft(draft, [ok, vkReady]);
    expect(okIssues).toEqual([]);
  });
});
