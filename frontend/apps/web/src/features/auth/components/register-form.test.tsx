import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { renderWithProviders } from '@/test/render';

import { RegisterForm } from './register-form';

const push = vi.fn();

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push, replace: vi.fn(), prefetch: vi.fn() }),
}));

describe('RegisterForm', () => {
  beforeEach(() => {
    push.mockReset();
  });

  it('shows validation errors for empty submit', async () => {
    const user = userEvent.setup();
    renderWithProviders(<RegisterForm />);

    await user.click(screen.getByTestId('register-submit'));

    expect(await screen.findAllByRole('alert')).not.toHaveLength(0);
    expect(push).not.toHaveBeenCalled();
  });

  it('submits valid data through MSW and routes to verify-email', async () => {
    const user = userEvent.setup();
    renderWithProviders(<RegisterForm />);

    await user.type(screen.getByLabelText('Имя'), 'Anna Editor');
    await user.type(screen.getByLabelText('Email'), 'anna@posts.app');
    await user.type(screen.getByLabelText('Пароль'), 'Secret12ab');
    await user.type(screen.getByLabelText('Повтор пароля'), 'Secret12ab');
    await user.click(screen.getByLabelText(/Принимаю условия/));
    await user.click(screen.getByTestId('register-submit'));

    await waitFor(() => {
      expect(push).toHaveBeenCalledWith('/verify-email?email=anna%40posts.app');
    });
  });

  it('shows ProblemDetail on conflict email', async () => {
    const user = userEvent.setup();
    renderWithProviders(<RegisterForm />);

    await user.type(screen.getByLabelText('Имя'), 'Taken User');
    await user.type(screen.getByLabelText('Email'), 'taken@posts.app');
    await user.type(screen.getByLabelText('Пароль'), 'Secret12ab');
    await user.type(screen.getByLabelText('Повтор пароля'), 'Secret12ab');
    await user.click(screen.getByLabelText(/Принимаю условия/));
    await user.click(screen.getByTestId('register-submit'));

    expect(await screen.findByText('Email already registered')).toBeInTheDocument();
    expect(push).not.toHaveBeenCalled();
  });
});
