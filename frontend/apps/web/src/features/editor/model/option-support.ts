import type { NetworkId } from '@posts/domain';

import type { EditorOptionKey } from './types';

/**
 * Network support matrix from scheduled-posts-and-ad-marking.md §3.3 / §4.
 * Instagram is intentionally absent.
 */
const SUPPORT: Record<EditorOptionKey, ReadonlySet<NetworkId>> = {
  signed: new Set(['vk']),
  mute: new Set(['vk', 'ok', 'tg', 'max']),
  closeComments: new Set(['vk', 'ok']),
  pin: new Set(['vk', 'tg', 'max']),
  unpinAt: new Set(['vk', 'tg', 'max']),
  deleteAfter: new Set(['vk', 'tg', 'ok', 'max', 'rutube']),
  coOwnerIds: new Set(['vk']),
  donutDays: new Set(['vk']),
  primaryAttachmentsMode: new Set(['vk']),
  firstComment: new Set(['vk', 'tg']),
  repost: new Set(['vk', 'tg', 'ok']),
  buttons: new Set(['tg', 'max']),
  actionButton: new Set(['vk']),
  poll: new Set(['vk', 'ok', 'tg']),
  geo: new Set(['vk', 'ok']),
  copyrightUrl: new Set(['vk']),
  isAd: new Set(['vk', 'ok', 'tg', 'max', 'rutube']),
};

export function isOptionSupported(option: EditorOptionKey, networks: NetworkId[]): boolean {
  if (networks.length === 0) {
    return true;
  }
  const allowed = SUPPORT[option];
  return networks.some((network) => allowed.has(network));
}

export function supportedNetworksFor(option: EditorOptionKey): NetworkId[] {
  return [...SUPPORT[option]];
}
