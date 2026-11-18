import type { EditorChannelOption } from '@/features/channels/hooks/use-workspace-channels';

import { isStorySupportedNetwork, isVkStoryReady } from './networks';
import type { StoryDraft } from './types';

const VK_INTERNAL_URL =
  /^https:\/\/(vk\.com|vk\.ru|m\.vk\.com|m\.vk\.ru)(\/|$)/i;

export function isValidVkStoryLinkUrl(url: string): boolean {
  const trimmed = url.trim();
  if (!trimmed) return false;
  if (trimmed.length > 2048) return false;
  try {
    const parsed = new URL(trimmed);
    if (parsed.protocol !== 'https:') return false;
    return VK_INTERNAL_URL.test(trimmed);
  } catch {
    return false;
  }
}

export type StoryValidationIssue = {
  code: string;
  messageKey: string;
};

export function validateStoryDraft(
  draft: StoryDraft,
  channels: EditorChannelOption[],
): StoryValidationIssue[] {
  const issues: StoryValidationIssue[] = [];

  if (!draft.media) {
    issues.push({ code: 'story_media_invalid', messageKey: 'validation.mediaRequired' });
  }

  if (draft.selectedChannelIds.length === 0) {
    issues.push({ code: 'channels', messageKey: 'validation.channelsRequired' });
  }

  const selected = draft.selectedChannelIds
    .map((id) => channels.find((channel) => channel.id === id))
    .filter((channel): channel is EditorChannelOption => Boolean(channel));

  for (const channel of selected) {
    if (!isStorySupportedNetwork(channel.network)) {
      issues.push({
        code: 'story_unsupported_network',
        messageKey: 'validation.unsupportedNetwork',
      });
    }
    if (!isVkStoryReady(channel.network, channel.storyConnectionStatus)) {
      issues.push({
        code: 'story_connection_required',
        messageKey: 'validation.storyConnectRequired',
      });
    }
  }

  const hasVk = selected.some((channel) => channel.network === 'vk');
  const hasLink = Boolean(draft.linkText.trim() || draft.linkUrl.trim());
  if (hasLink) {
    if (!draft.linkText.trim() || !draft.linkUrl.trim()) {
      issues.push({ code: 'story_link_invalid', messageKey: 'validation.linkPair' });
    } else if (hasVk && !isValidVkStoryLinkUrl(draft.linkUrl)) {
      issues.push({ code: 'story_link_invalid', messageKey: 'validation.vkLinkOnly' });
    }
  }

  if (draft.isAd) {
    const adsAllowed = selected.every((channel) => isStorySupportedNetwork(channel.network));
    if (!adsAllowed || selected.length === 0) {
      issues.push({ code: 'ads_not_allowed_for_network', messageKey: 'validation.adsNetworks' });
    }
  }

  if (
    draft.autoDeleteAt &&
    draft.scheduledAt &&
    new Date(draft.autoDeleteAt).getTime() <= new Date(draft.scheduledAt).getTime()
  ) {
    issues.push({ code: 'validation_failed', messageKey: 'validation.autoDeleteOrder' });
  }

  return issues;
}

export function selectedNetworksIncludeOk(
  channelIds: string[],
  channels: EditorChannelOption[],
): boolean {
  return channelIds.some((id) => channels.find((channel) => channel.id === id)?.network === 'ok');
}

export function selectedNetworksSupportAds(
  channelIds: string[],
  channels: EditorChannelOption[],
): boolean {
  if (channelIds.length === 0) return false;
  return channelIds.every((id) => {
    const channel = channels.find((item) => item.id === id);
    return channel ? isStorySupportedNetwork(channel.network) : false;
  });
}
