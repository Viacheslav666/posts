export type Network = 'vk' | 'tg' | 'ok' | 'max' | 'rutube';

export function isSupportedNetwork(value: string): value is Network {
  return ['vk', 'tg', 'ok', 'max', 'rutube'].includes(value);
}
