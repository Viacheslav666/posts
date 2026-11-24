'use client';

import { ImpersonationBanner } from '@posts/ui';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useTranslations } from 'next-intl';
import { useRouter } from 'next/navigation';

import { fetchMe } from '@/features/auth/api/auth-api';
import { getAccessToken, getImpersonationTarget, queryKeys } from '@/shared/api-client';

import { useExitImpersonationMutation } from '../hooks/use-admin-queries';

/**
 * Global sticky banner for impersonation sessions. Mounted in AppProviders so it
 * appears on every route (app shell, admin, public after login).
 */
export function ImpersonationBannerHost() {
  const t = useTranslations('admin.impersonation');
  const accessToken = getAccessToken();
  const storedTarget = getImpersonationTarget();
  const { data: me } = useQuery({
    queryKey: queryKeys.me.profile(),
    queryFn: fetchMe,
    enabled: Boolean(accessToken),
    retry: false,
  });
  const exit = useExitImpersonationMutation();
  const router = useRouter();
  const queryClient = useQueryClient();

  const active = Boolean(me?.impersonation?.active) || Boolean(storedTarget);
  if (!active) {
    return null;
  }

  const targetLabel = storedTarget?.email || me?.email || me?.name || '-';
  const reason = storedTarget?.reason || me?.impersonation?.reason;

  return (
    <ImpersonationBanner
      title={t('title', { name: targetLabel })}
      targetLabel={targetLabel}
      reason={reason ? t('reason', { reason }) : undefined}
      exitLabel={exit.isPending ? t('exiting') : t('exit')}
      exiting={exit.isPending}
      onExit={() => {
        exit.mutate(undefined, {
          onSuccess: async () => {
            await queryClient.invalidateQueries();
            router.push('/admin/users');
          },
        });
      }}
    />
  );
}
