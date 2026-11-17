import type { SocialNetwork } from '@posts/ui';

import type { ConnectableNetwork } from './types';

export function toSocialNetwork(network: string): SocialNetwork | null {
  switch (network) {
    case 'vk':
      return 'vk';
    case 'tg':
      return 'telegram';
    case 'ok':
      return 'ok';
    case 'max':
      return 'max';
    case 'rutube':
      return 'rutube';
    default:
      return null;
  }
}

export function networkLabelKey(network: ConnectableNetwork): string {
  return network;
}
