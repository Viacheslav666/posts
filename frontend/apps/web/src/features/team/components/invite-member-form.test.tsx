import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it } from 'vitest';

import { PERSONAL_WORKSPACE_ID, resetWorkspaceStore } from '@/mocks/workspace-store';
import { setAccessToken } from '@/shared/api-client';
import { renderWithProviders } from '@/test/render';

import { InviteMemberForm } from './invite-member-form';

describe('InviteMemberForm', () => {
  beforeEach(() => {
    resetWorkspaceStore();
    setAccessToken('msw.access.token');
  });

  it('validates email before submit', async () => {
    const user = userEvent.setup();
    renderWithProviders(<InviteMemberForm workspaceId={PERSONAL_WORKSPACE_ID} />);

    await user.click(screen.getByTestId('invite-submit'));
    expect(await screen.findByText('Укажите email')).toBeInTheDocument();
  });

  it('shows insufficient_permissions toast for editor token', async () => {
    const user = userEvent.setup();
    setAccessToken('msw.access.editor');
    renderWithProviders(<InviteMemberForm workspaceId={PERSONAL_WORKSPACE_ID} />);

    await user.type(screen.getByLabelText('Эл. почта'), 'new@posts.app');
    await user.click(screen.getByTestId('invite-submit'));

    expect(await screen.findByText('Недостаточно прав для этого действия')).toBeInTheDocument();
  });

  it('succeeds for owner when seats available', async () => {
    const user = userEvent.setup();
    // Cancel pending invite in store via API path: personal WS is at seat limit (2 members + 1 invite).
    // Use Acme workspace which has 1 member and 0 invites.
    const ACME = '019a0000-0002-7000-8000-000000000003';
    renderWithProviders(<InviteMemberForm workspaceId={ACME} />);

    await user.type(screen.getByLabelText('Эл. почта'), 'colleague@posts.app');
    await user.click(screen.getByTestId('invite-submit'));

    await waitFor(() => {
      expect(screen.getByText('Приглашение отправлено')).toBeInTheDocument();
    });
  });
});
