import type { AdMarking, OrdProvider, StoryOptions } from '@posts/api-client';

import type { EditorMediaItem } from '@/features/media/model/types';

export type StoryPublishMode = 'schedule' | 'now';

export type StoryDraft = {
  id: string;
  workspaceId: string;
  updatedAt: string;
  title: string;
  selectedChannelIds: string[];
  /** Exactly one media asset for a single story. */
  media: EditorMediaItem | null;
  scheduledAt: string;
  timezone: string;
  publishMode: StoryPublishMode;
  story: StoryOptions;
  isAd: boolean;
  ad: AdMarking;
  ordProvider: OrdProvider;
  linkText: string;
  linkUrl: string;
  autoDeleteAfterMinutes: number | null;
  autoDeleteAt: string | null;
};

export type StoryBatchRow = {
  id: string;
  media: EditorMediaItem;
  scheduledAt: string;
};
