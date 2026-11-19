import { screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { renderWithProviders } from '@/test/render';

import { CapabilityNotice } from './capability-notice';

describe('CapabilityNotice', () => {
  it('shows VK user wall notice', () => {
    renderWithProviders(<CapabilityNotice variant="vk_user_wall" />);
    expect(screen.getByTestId('capability-notice')).toHaveAttribute(
      'data-variant',
      'vk_user_wall',
    );
    expect(screen.getByText('Нужен ключ пользователя VK')).toBeInTheDocument();
    expect(screen.getByText(/правом wall/)).toBeInTheDocument();
  });

  it('allows custom title/body override', () => {
    renderWithProviders(
      <CapabilityNotice variant="generic" title="Заголовок" body="Текст" />,
    );
    expect(screen.getByText('Заголовок')).toBeInTheDocument();
    expect(screen.getByText('Текст')).toBeInTheDocument();
  });
});
