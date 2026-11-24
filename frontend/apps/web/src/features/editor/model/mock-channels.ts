import { CHANNEL_IDS } from '@/mocks/channel-store';

import type { EditorChannel } from './types';

/**
 * @deprecated FE-6: editor loads channels from GET /channels (MSW/API).
 * Kept as seed reference for tests that assert known channel ids.
 */
export const MOCK_CHANNELS: EditorChannel[] = [
  {
    id: CHANNEL_IDS.vkNews,
    name: 'Posts - новости',
    network: 'vk',
    projectName: 'Основной проект',
  },
  {
    id: CHANNEL_IDS.tgNews,
    name: '@posts_news',
    network: 'tg',
    projectName: 'Основной проект',
  },
  {
    id: CHANNEL_IDS.okBrand,
    name: 'Posts OK',
    network: 'ok',
    projectName: 'Основной проект',
  },
  {
    id: CHANNEL_IDS.maxBrand,
    name: 'Posts Max',
    network: 'max',
    projectName: 'Основной проект',
  },
  {
    id: CHANNEL_IDS.vkShop,
    name: 'VK Витрина',
    network: 'vk',
    projectName: 'Основной проект',
  },
];
