import type {
  AdMarking,
  PollOptions,
  PostVariantOptions,
  Recurrence,
  RepostTarget,
} from '@posts/api-client';
import type { NetworkId, TiptapNode } from '@posts/domain';

import type { EditorChannelOption } from '@/features/channels/hooks/use-workspace-channels';
import type { EditorMediaItem } from '@/features/media/model/types';

/** Channel option in the editor picker (backed by GET /channels). */
export type EditorChannel = EditorChannelOption;

/** @deprecated Use EditorChannel — kept for older imports. */
export type MockChannel = EditorChannel;

export type EditorVariant = {
  network: NetworkId;
  /** When set, overrides base content for this network. */
  overridden: boolean;
  html: string;
  doc: TiptapNode | null;
};

export type PublishMode = 'schedule' | 'now';

/** Shared post-level options (FE-12 parity with CreatePostRequest + PostVariantOptions). */
export type EditorPostOptions = {
  title: string;
  publishMode: PublishMode;
  scheduledAt: string;
  timezone: string;
  recurrence: Recurrence;
  stopAt: string | null;
  removeAfterPublish: boolean;
  repost: RepostTarget | null;
  /** Shared variant options applied to all selected networks (unsupported greyed in UI). */
  variant: PostVariantOptions;
  isAd: boolean;
  ad: AdMarking;
  /** Selected ORD provider for registration preview. */
  ordProvider: 'vk_ord' | 'yandex_ord';
};

/** Wall post vs VK clip publication (media kind=clip when clip). */
export type PublicationKind = 'post' | 'clip';

export type EditorDraft = {
  id: string;
  workspaceId: string;
  updatedAt: string;
  baseHtml: string;
  baseDoc: TiptapNode | null;
  selectedChannelIds: string[];
  variants: EditorVariant[];
  previewNetwork: 'vk' | 'tg' | 'ok' | 'max';
  activeVariant: 'base' | NetworkId;
  media: EditorMediaItem[];
  /** VK string attachments (photo-1_2, …) — only meaningful for VK channels. */
  stringAttachments: string[];
  /** Publication type: wall post or VK clip. */
  publicationKind: PublicationKind;
  options: EditorPostOptions;
};

export type EditorOptionKey =
  | 'signed'
  | 'mute'
  | 'closeComments'
  | 'pin'
  | 'unpinAt'
  | 'deleteAfter'
  | 'coOwnerIds'
  | 'donutDays'
  | 'primaryAttachmentsMode'
  | 'firstComment'
  | 'repost'
  | 'buttons'
  | 'actionButton'
  | 'poll'
  | 'geo'
  | 'copyrightUrl'
  | 'isAd';

export const EMPTY_DOC: TiptapNode = {
  type: 'doc',
  content: [{ type: 'paragraph' }],
};

export type { PollOptions };
