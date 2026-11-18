import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { setAccessToken } from '@/shared/api-client';
import { renderWithProviders } from '@/test/render';

import { CreateWorkspaceForm } from './create-workspace-form';

const push = vi.fn();

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push, replace: vi.fn(), prefetch: vi.fn() }),
  useParams: () => ({}),
}));

describe('CreateWorkspaceForm', () => {
  beforeEach(() => {
    push.mockReset();
    setAccessToken('msw.access.token');
  });

  it('validates required name', async () => {
    const user = userEvent.setup();
    renderWithProviders(<CreateWorkspaceForm />);

    await user.click(screen.getByTestId('create-workspace-submit'));
    expect(await screen.findByText('Укажите название')).toBeInTheDocument();
  });

  it('creates a workspace and navigates', async () => {
    const user = userEvent.setup();
    renderWithProviders(<CreateWorkspaceForm />);

    await user.type(screen.getByLabelText('Название'), 'Новый клиент');
    await user.click(screen.getByTestId('create-workspace-submit'));

    await waitFor(() => {
      expect(screen.getByText('Пространство создано')).toBeInTheDocument();
    });
    await waitFor(() => {
      expect(push).toHaveBeenCalled();
      const arg = push.mock.calls[0]?.[0] as string;
      expect(arg).toMatch(/^\/app\/019a0000-0002-7000-8000-/);
    });
  });
});
