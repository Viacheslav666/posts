import { describe, expect, it } from 'vitest';

import { VK_PUBLISH_BLOCKED_MESSAGE, vkPublishBlockReason } from './vk-publish-block';

describe('vkPublishBlockReason', () => {
  it('returns the Russian reason for a VK channel that cannot post', () => {
    const reason = vkPublishBlockReason(
      [
        {
          id: 'ch-1',
          network: 'vk',
          status: 'no_permissions',
          statusReason: VK_PUBLISH_BLOCKED_MESSAGE,
        },
      ],
      ['ch-1'],
    );
    expect(reason).toBe(VK_PUBLISH_BLOCKED_MESSAGE);
    expect(reason).not.toContain('—');
  });

  it('does not block a classic or community VK channel', () => {
    expect(
      vkPublishBlockReason(
        [{ id: 'group', network: 'vk', status: 'active', statusReason: undefined }],
        ['group'],
      ),
    ).toBeNull();
  });
});
