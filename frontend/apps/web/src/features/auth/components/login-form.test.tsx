import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { renderWithProviders } from '@/test/render';

import { LoginForm } from './login-form';

const push = vi.fn();

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push, replace: vi.fn(), prefetch: vi.fn() }),
}));

describe('LoginForm', () => {
  beforeEach(() => {
    push.mockReset();
  });

  it('routes to app after successful login', async () => {
    const user = userEvent.setup();
    renderWithProviders(<LoginForm />);

    await user.type(screen.getByLabelText('Email'), 'demo@posts.app');
    await user.type(screen.getByLabelText('Пароль'), 'Secret12');
    await user.click(screen.getByTestId('login-submit'));

    await waitFor(() => {
      expect(push).toHaveBeenCalledWith('/app/demo');
    });
  });

  it('routes to two-factor when MSW returns challenge', async () => {
    const user = userEvent.setup();
    renderWithProviders(<LoginForm />);

    await user.type(screen.getByLabelText('Email'), 'twofa@posts.app');
    await user.type(screen.getByLabelText('Пароль'), 'Secret12');
    await user.click(screen.getByTestId('login-submit'));

    await waitFor(() => {
      expect(push).toHaveBeenCalledWith('/two-factor?challengeId=chal_demo_2fa');
    });
  });
});
