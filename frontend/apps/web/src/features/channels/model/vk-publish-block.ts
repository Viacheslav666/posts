export const VK_PUBLISH_BLOCKED_MESSAGE =
  'VK не разрешает публикацию с этим типом подключения. Переподключите канал.';

type VkChannelLike = {
  id: string;
  network: string;
  status?: string;
  statusReason?: string;
};

/** Reason to show before scheduling, when a selected VK channel cannot post. */
export function vkPublishBlockReason(
  channels: VkChannelLike[],
  selectedIds: string[],
): string | null {
  const blocked = channels.find(
    (channel) =>
      selectedIds.includes(channel.id) &&
      channel.network === 'vk' &&
      channel.status === 'no_permissions',
  );
  if (!blocked) return null;
  const reason = blocked.statusReason?.trim();
  return reason ? reason : VK_PUBLISH_BLOCKED_MESSAGE;
}
