import { describe, expect, it } from 'vitest';

import { ChannelStatusBadge } from './channel-status-badge';

describe('ChannelStatusBadge', () => {
  it('exports a function component', () => {
    expect(typeof ChannelStatusBadge).toBe('function');
  });
});
