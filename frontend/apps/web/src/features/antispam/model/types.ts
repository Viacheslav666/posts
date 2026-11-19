export type RuleStatus = 'draft' | 'active' | 'paused' | 'archived';

export type AntispamNetwork = 'vk' | 'tg' | 'max';

export type AntispamSection =
  | 'wall_comment'
  | 'photo_comment'
  | 'video_comment'
  | 'board_comment'
  | 'market_comment'
  | 'wall_post'
  | 'suggested_post'
  | 'tg_discussion'
  | 'tg_group'
  | 'max_comment'
  | 'max_chat';

export type AntispamAction = 'flagged' | 'deleted' | 'deleted_banned' | 'replied';

export type AntispamLogState =
  | 'pending'
  | 'done'
  | 'failed'
  | 'restored'
  | 'restore_failed';

export type WordListMode = 'black' | 'white';

export const ANTISPAM_NETWORKS: AntispamNetwork[] = ['vk', 'tg', 'max'];

export const VK_SECTIONS: AntispamSection[] = [
  'wall_comment',
  'photo_comment',
  'video_comment',
  'board_comment',
  'market_comment',
  'wall_post',
  'suggested_post',
];

export const TG_SECTIONS: AntispamSection[] = ['tg_discussion', 'tg_group'];

export const MAX_SECTIONS: AntispamSection[] = ['max_comment', 'max_chat'];

export const ALL_SECTIONS: AntispamSection[] = [
  ...VK_SECTIONS,
  ...TG_SECTIONS,
  ...MAX_SECTIONS,
];

export const ANTISPAM_ACTIONS: AntispamAction[] = [
  'flagged',
  'deleted',
  'deleted_banned',
  'replied',
];

export const ANTISPAM_LOG_STATES: AntispamLogState[] = [
  'pending',
  'done',
  'failed',
  'restored',
  'restore_failed',
];

export const ATTACHMENT_KEYS = [
  'photo',
  'photo_without_text',
  'video',
  'audio',
  'doc',
  'gif',
  'sticker',
] as const;

export type AttachmentKey = (typeof ATTACHMENT_KEYS)[number];

export function sectionsForNetwork(network: string | undefined | null): AntispamSection[] {
  switch (network) {
    case 'vk':
      return [...VK_SECTIONS];
    case 'tg':
      return [...TG_SECTIONS];
    case 'max':
      return [...MAX_SECTIONS];
    default:
      return [];
  }
}

export function isAntispamNetwork(value: string | undefined | null): value is AntispamNetwork {
  return value === 'vk' || value === 'tg' || value === 'max';
}
