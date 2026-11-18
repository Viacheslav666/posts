import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { createDefaultAdMarking } from '@/features/editor/model/defaults';
import type { AdMarking } from '@posts/api-client';
import { PERSONAL_WORKSPACE_ID } from '@/mocks/workspace-store';
import { setAccessToken } from '@/shared/api-client';
import type { PermissionAction } from '@/shared/lib/permissions';
import { renderWithProviders } from '@/test/render';

const canMap: Partial<Record<PermissionAction, boolean>> = {
  'posts.ad.schedule': true,
  'ord.view': true,
};

vi.mock('@/shared/hooks/use-can', () => ({
  useCan: (action: PermissionAction) => canMap[action] ?? false,
  useWorkspaceRole: () => 'owner',
}));

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn(), replace: vi.fn(), prefetch: vi.fn() }),
  useParams: () => ({ workspace: PERSONAL_WORKSPACE_ID }),
}));

import { AdMarkingForm } from './ad-marking-form';

function StatefulAdForm({ hasOrdConnection }: { hasOrdConnection: boolean }) {
  const [ad, setAd] = useState<AdMarking>(createDefaultAdMarking());
  return (
    <AdMarkingForm
      workspaceId={PERSONAL_WORKSPACE_ID}
      enabled
      ad={ad}
      provider="vk_ord"
      hasOrdConnection={hasOrdConnection}
      eridStatus="missing"
      onEnabledChange={vi.fn()}
      onAdChange={setAd}
      onProviderChange={vi.fn()}
    />
  );
}

describe('AdMarkingForm', () => {
  beforeEach(() => {
    setAccessToken('msw.access.token');
  });

  it('shows ORD block warning and validates INN', async () => {
    const user = userEvent.setup();
    renderWithProviders(<StatefulAdForm hasOrdConnection={false} />);

    expect(screen.getByTestId('ad-ord-not-configured')).toBeInTheDocument();
    const inn = screen.getByTestId('ad-inn');
    await user.type(inn, '1234567890');
    await waitFor(() => {
      expect(screen.getByTestId('ad-inn-error')).toBeInTheDocument();
    });
  });

  it('loads KKTU dictionary options when ORD is connected', async () => {
    renderWithProviders(<StatefulAdForm hasOrdConnection />);

    await waitFor(() => {
      expect(screen.getByTestId('ad-kktu')).toBeInTheDocument();
    });
    expect(await screen.findByRole('option', { name: /1\.1\.1/ })).toBeInTheDocument();
  });

  it('exposes six placement modes and vk_menu warning', async () => {
    const user = userEvent.setup();
    renderWithProviders(<StatefulAdForm hasOrdConnection />);

    const placement = await screen.findByTestId('ad-placement');
    for (const value of [
      'vk_menu',
      'text_start',
      'text_end',
      'vk_menu_and_text_end',
      'click_link_start',
      'click_link_end',
    ]) {
      expect(placement.querySelector(`option[value="${value}"]`)).not.toBeNull();
    }

    await user.selectOptions(placement, 'vk_menu');
    expect(await screen.findByTestId('ad-vk-menu-unsupported')).toBeInTheDocument();
    expect(screen.getByTestId('ad-placement-preview')).toBeInTheDocument();
  });

  it('toggles social and foreign flags and shows foreign fields', async () => {
    const user = userEvent.setup();
    renderWithProviders(<StatefulAdForm hasOrdConnection />);

    await user.click(screen.getByTestId('ad-social-ad'));
    await user.click(screen.getByTestId('ad-social-ad-quota'));
    await user.click(screen.getByTestId('ad-is-foreign'));
    expect(await screen.findByTestId('ad-foreign-inn')).toBeInTheDocument();
    expect(screen.getByTestId('ad-arir-link')).toBeInTheDocument();
  });
});
