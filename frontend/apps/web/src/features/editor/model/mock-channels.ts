import type { MockChannel } from './types';

/** FE-4 prototype channel list (until FE-6 connects real channels API). */
export const MOCK_CHANNELS: MockChannel[] = [
  {
    id: '019a0000-chan-7000-8000-000000000001',
    name: 'Posts — новости',
    network: 'vk',
    projectName: 'Личный бренд',
  },
  {
    id: '019a0000-chan-7000-8000-000000000002',
    name: '@posts_news',
    network: 'tg',
    projectName: 'Личный бренд',
  },
  {
    id: '019a0000-chan-7000-8000-000000000003',
    name: 'Posts OK',
    network: 'ok',
    projectName: 'Клиент А',
  },
  {
    id: '019a0000-chan-7000-8000-000000000004',
    name: 'Posts Max',
    network: 'max',
    projectName: 'Клиент А',
  },
  {
    id: '019a0000-chan-7000-8000-000000000005',
    name: 'VK Витрина',
    network: 'vk',
    projectName: 'Клиент А',
  },
];
