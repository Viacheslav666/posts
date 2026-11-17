export type ConnectableNetwork = 'vk' | 'tg' | 'ok' | 'max';

export type ChannelStatus = 'active' | 'reauth_required' | 'no_permissions' | 'disabled';

export const CONNECTABLE_NETWORKS: ConnectableNetwork[] = ['vk', 'tg', 'ok', 'max'];

export const OAUTH_NETWORKS: ConnectableNetwork[] = ['vk', 'ok'];

export const BOT_NETWORKS: ConnectableNetwork[] = ['tg', 'max'];

export function isConnectableNetwork(value: string): value is ConnectableNetwork {
  return (CONNECTABLE_NETWORKS as string[]).includes(value);
}

export function isOauthNetwork(network: ConnectableNetwork): boolean {
  return OAUTH_NETWORKS.includes(network);
}

export function isBotNetwork(network: ConnectableNetwork): boolean {
  return BOT_NETWORKS.includes(network);
}
