import { screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { renderWithProviders } from '@/test/render';

import { ReasonChips } from './reason-chips';

describe('ReasonChips', () => {
  it('renders translated reason codes', () => {
    renderWithProviders(<ReasonChips codes={['keyword_match', 'loop_detected']} />);
    expect(screen.getByTestId('reason-chips')).toBeInTheDocument();
    expect(screen.getByText('Ключевое слово')).toBeInTheDocument();
    expect(screen.getByText('Обнаружена петля')).toBeInTheDocument();
  });

  it('returns null for empty list', () => {
    const { container } = renderWithProviders(<ReasonChips codes={[]} />);
    expect(container.querySelector('[data-testid="reason-chips"]')).toBeNull();
  });
});
