import { screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { renderWithProviders } from '@/test/render';

import { RuleStatusBadge } from './rule-status-badge';

describe('RuleStatusBadge (FE-23)', () => {
  it('renders known statuses', () => {
    renderWithProviders(<RuleStatusBadge status="active" />);
    expect(screen.getByTestId('antispam-rule-status')).toHaveTextContent(/Активно/i);
  });

  it('falls back to draft for unknown status', () => {
    renderWithProviders(<RuleStatusBadge status="weird" />);
    expect(screen.getByTestId('antispam-rule-status')).toHaveTextContent(/Черновик/i);
  });
});
