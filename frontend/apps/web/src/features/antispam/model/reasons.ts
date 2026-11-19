import { ATTACHMENT_KEYS, type AttachmentKey } from './types';

const ATTACHMENT_SET = new Set<string>(ATTACHMENT_KEYS);

/** Engine codes (RuleConfig / AntispamEngine) → i18n keys under automations.antispam.reasons. */
const REASON_KEYS: Record<string, string> = {
  link: 'links',
  links: 'links',
  vk_link: 'links_vk',
  links_vk: 'links_vk',
  caps: 'caps',
  repeat: 'repeat',
  from_community: 'authors_communities',
  authors_communities: 'authors_communities',
  non_member: 'authors_non_members',
  authors_non_members: 'authors_non_members',
  sender_chat: 'authors_sender_chat',
  authors_sender_chat: 'authors_sender_chat',
  under_ads: 'context_under_ads',
  context_under_ads: 'context_under_ads',
  under_pinned: 'context_under_pinned',
  context_under_pinned: 'context_under_pinned',
  keyword_black: 'keyword_black',
  keyword_white: 'keyword_white',
  attachment: 'attachment',
};

export type AntispamReasonView =
  | { kind: 'key'; key: string }
  | { kind: 'keyword'; word: string }
  | { kind: 'attachment'; attachment: AttachmentKey }
  | { kind: 'raw'; text: string };

/** Map a journal reason code to something the UI can translate. Unknown codes stay raw. */
export function parseAntispamReason(code: string): AntispamReasonView {
  if (code.startsWith('keyword:')) {
    const word = code.slice('keyword:'.length).trim();
    return word ? { kind: 'keyword', word } : { kind: 'key', key: 'keyword_black' };
  }
  if (code.startsWith('attachment:')) {
    const attachment = code.slice('attachment:'.length);
    if (ATTACHMENT_SET.has(attachment)) {
      return { kind: 'attachment', attachment: attachment as AttachmentKey };
    }
    return { kind: 'raw', text: code };
  }
  const key = REASON_KEYS[code];
  if (key) return { kind: 'key', key };
  return { kind: 'raw', text: code };
}

export const ANTISPAM_LOG_STATE_LABELS = [
  'pending',
  'done',
  'failed',
  'restored',
  'restore_failed',
] as const;

export type KnownLogState = (typeof ANTISPAM_LOG_STATE_LABELS)[number];

export function isKnownLogState(state: string | undefined | null): state is KnownLogState {
  return ANTISPAM_LOG_STATE_LABELS.includes(state as KnownLogState);
}

export const ANTISPAM_SCAN_STATUSES = ['queued', 'done', 'failed'] as const;

export type KnownScanStatus = (typeof ANTISPAM_SCAN_STATUSES)[number];

export function isKnownScanStatus(status: string | undefined | null): status is KnownScanStatus {
  return ANTISPAM_SCAN_STATUSES.includes(status as KnownScanStatus);
}
