import type { StoryOptions, StoryTtl } from '@posts/api-client';

import { createDefaultAdMarking, defaultScheduledAt } from '@/features/editor/model/defaults';
import type { EditorMediaItem } from '@/features/media/model/types';

import type { StoryDraft } from './types';

export function createDefaultStoryOptions(): StoryOptions {
  return {
    ttl: 'ttl_24h',
    deleteAfterPublish: false,
    mediaKind: 'photo',
    addToNews: true,
  };
}

export function createStoryDraft(workspaceId: string, draftId = 'new'): StoryDraft {
  return {
    id: draftId,
    workspaceId,
    updatedAt: new Date().toISOString(),
    title: '',
    selectedChannelIds: [],
    media: null,
    scheduledAt: defaultScheduledAt(),
    timezone: 'Europe/Moscow',
    publishMode: 'schedule',
    story: createDefaultStoryOptions(),
    isAd: false,
    ad: createDefaultAdMarking(),
    ordProvider: 'vk_ord',
    linkText: '',
    linkUrl: '',
    autoDeleteAfterMinutes: null,
    autoDeleteAt: null,
  };
}

export function mediaKindFromItem(item: EditorMediaItem | null): 'photo' | 'video' {
  if (!item) return 'photo';
  if (item.kind === 'video') return 'video';
  return 'photo';
}

export function withStoryLink(
  story: StoryOptions,
  linkText: string,
  linkUrl: string,
): StoryOptions {
  const text = linkText.trim();
  const url = linkUrl.trim();
  return {
    ...story,
    linkText: text || undefined,
    linkUrl: url || undefined,
  };
}

export function effectiveTtlForOk(ttl: StoryTtl | undefined): StoryTtl {
  return ttl === 'ttl_24h' || !ttl ? 'ttl_24h' : 'ttl_24h';
}
