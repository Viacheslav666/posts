export type PermalinkChannelHint = {
  network?: string | null;
  username?: string | null;
  externalId?: string | null;
};

/**
 * Construct a public permalink for a crosspost journal source post.
 * CrosspostItem has no permalink field — UI builds it from network + remote id.
 */
export function buildSourcePermalink(
  network: string | null | undefined,
  sourceRemoteId: string | null | undefined,
  channel?: PermalinkChannelHint | null,
): string | null {
  const remote = (sourceRemoteId ?? '').trim();
  if (!remote) return null;
  const net = (network ?? channel?.network ?? '').toLowerCase();

  if (net === 'vk') {
    if (/^wall-?\d+_\d+$/i.test(remote)) {
      return `https://vk.com/${remote}`;
    }
    const ext = (channel?.externalId ?? '').replace(/^-/, '');
    if (ext && /^\d+$/.test(remote)) {
      return `https://vk.com/wall-${ext}_${remote}`;
    }
    if (/^-?\d+_\d+$/.test(remote)) {
      return `https://vk.com/wall${remote.startsWith('-') ? remote : `-${remote}`}`;
    }
    return null;
  }

  if (net === 'tg') {
    const username = (channel?.username ?? '').replace(/^@/, '');
    if (username && /^\d+$/.test(remote)) {
      return `https://t.me/${username}/${remote}`;
    }
    if (/^https?:\/\//i.test(remote)) return remote;
    return null;
  }

  if (net === 'max') {
    if (/^https?:\/\//i.test(remote)) return remote;
    return null;
  }

  if (/^https?:\/\//i.test(remote)) return remote;
  return null;
}
