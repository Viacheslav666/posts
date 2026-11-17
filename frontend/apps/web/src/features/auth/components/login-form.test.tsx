import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { clearPendingLogin, readPendingLogin } from '@/shared/api-client';
import { renderWithProviders } from '@/test/render';

import { LoginForm } from './login-form';

const push = vi.fn();

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push, replace: vi.fn(), prefetch: vi.fn() }),
}));

describe('LoginForm', () => {
  beforeEach(() => {
    push.mockReset();
    clearPendingLogin();
  });

  it('routes to app after successful login', async () => {
    const user = userEvent.setup();
    renderWithProviders(<LoginForm />);

    await user.type(screen.getByLabelText('Email'), 'demo@posts.app');
    await user.type(screen.getByLabelText('Пароль'), 'Secret12ab');
    await user.click(screen.getByTestId('login-submit'));

    await waitFor(() => {
      expect(push).toHaveBeenCalledWith('/app/019a0000-0002-7000-8000-000000000002');
    });
  });

  it('routes to two-factor when API requires TOTP', async () => {
    const user = userEvent.setup();
    renderWithProviders(<LoginForm />);

    await user.type(screen.getByLabelText('Email'), 'twofa@posts.app');
    await user.type(screen.getByLabelText('Пароль'), 'Secret12ab');
    await user.click(screen.getByTestId('login-submit'));

    await waitFor(() => {
      expect(push).toHaveBeenCalledWith('/two-factor');
    });
    expect(readPendingLogin()?.email).toBe('twofa@posts.app');
  });

  it('shows ProblemDetail detail on invalid credentials', async () => {
    const user = userEvent.setup();
    renderWithProviders(<LoginForm />);

    await user.type(screen.getByLabelText('Email'), 'demo@posts.app');
    await user.type(screen.getByLabelText('Пароль'), 'wrong-password');
    await user.click(screen.getByTestId('login-submit'));

    expect(await screen.findByText('Invalid email or password')).toBeInTheDocument();
    expect(push).not.toHaveBeenCalled();
  });
});
