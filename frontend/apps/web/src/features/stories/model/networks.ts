/** Networks supported for content_kind=story (E11 / Q-STORY-05). */
export const STORY_SUPPORTED_NETWORKS = ['vk', 'ok'] as const;
export type StorySupportedNetwork = (typeof STORY_SUPPORTED_NETWORKS)[number];

/** Shown in the picker as unavailable with an explanation (not Instagram). */
export const STORY_UNSUPPORTED_VISIBLE_NETWORKS = ['tg', 'max'] as const;
export type StoryUnsupportedVisibleNetwork =
  (typeof STORY_UNSUPPORTED_VISIBLE_NETWORKS)[number];

/** Never render Instagram in story UI (Q-STORY-05). */
export const STORY_HIDDEN_NETWORKS = ['instagram', 'ig', 'ig_business', 'facebook'] as const;

export function isStorySupportedNetwork(
  network: string | undefined | null,
): network is StorySupportedNetwork {
  return network === 'vk' || network === 'ok';
}

export function isStoryUnsupportedVisibleNetwork(
  network: string | undefined | null,
): network is StoryUnsupportedVisibleNetwork {
  return network === 'tg' || network === 'max';
}

export function isVkStoryReady(
  network: string | undefined | null,
  storyConnectionStatus: string | undefined | null,
): boolean {
  if (network === 'ok') return true;
  if (network !== 'vk') return false;
  return storyConnectionStatus === 'ready';
}
