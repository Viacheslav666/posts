import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { ChannelStatusBadge } from '@posts/ui';

describe('ChannelStatusBadge statuses', () => {
  it('renders active status', () => {
    render(<ChannelStatusBadge status="active" label="Активен" />);
    const badge = screen.getByText('Активен');
    expect(badge).toHaveAttribute('data-channel-status', 'active');
  });

  it('renders reauth_required as reconnect label', () => {
    render(<ChannelStatusBadge status="reauth_required" label="Переподключить" />);
    expect(screen.getByText('Переподключить')).toHaveAttribute(
      'data-channel-status',
      'reauth_required',
    );
  });

  it('renders no_permissions', () => {
    render(<ChannelStatusBadge status="no_permissions" label="Нет прав" />);
    expect(screen.getByText('Нет прав')).toHaveAttribute('data-channel-status', 'no_permissions');
  });
});
