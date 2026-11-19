import type { Channel } from '@posts/api-client';

/**
 * OpenAPI `Channel.capabilities` is an untyped map; BE-34 uses `publishSuggested`.
 */
export function channelHasPublishSuggested(channel: Channel | undefined | null): boolean {
  if (!channel) return false;
  const caps = channel.capabilities as Record<string, unknown> | undefined;
  if (!caps) return false;
  return caps.publishSuggested === true;
}
