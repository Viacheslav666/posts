'use client';

import type { ChannelPage } from '@posts/api-client';
import { Button, EmptyState, Skeleton } from '@posts/ui';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { CheckCircle2, XCircle } from 'lucide-react';
import Link from 'next/link';
import { useRouter, useSearchParams } from 'next/navigation';
import { useEffect, useRef } from 'react';
import { useTranslations } from 'next-intl';

import { queryKeys } from '@/shared/api-client';
import {
  getErrorMessage,
  isPlanLimitExceeded,
} from '@/shared/lib/errors';

import { connectChannelCallback } from '../api/channels-api';
import { isConnectableNetwork, isOauthNetwork, type ConnectableNetwork } from '../model/types';

type OauthCallbackPageProps = {
  workspaceId?: string;
  network: string;
};

function resolveWorkspaceId(workspaceId: string | undefined, page?: ChannelPage): string | undefined {
  return workspaceId ?? page?.workspaceId ?? page?.items?.[0]?.workspaceId;
}

export function OauthCallbackPage({ workspaceId, network }: OauthCallbackPageProps) {
  const t = useTranslations('channels');
  const router = useRouter();
  const searchParams = useSearchParams();
  const queryClient = useQueryClient();
  const started = useRef(false);

  const code = searchParams.get('code') ?? '';
  const state = searchParams.get('state') ?? '';
  const deviceId = searchParams.get('device_id') ?? undefined;
  const projectId = searchParams.get('projectId') ?? undefined;
  const oauthError = searchParams.get('error');
  const oauthErrorDescription = searchParams.get('error_description');

  const validNetwork =
    isConnectableNetwork(network) && isOauthNetwork(network)
      ? (network as Extract<ConnectableNetwork, 'vk' | 'ok'>)
      : null;

  const mutation = useMutation({
    mutationFn: () => {
      if (!validNetwork) {
        throw new Error('Unsupported network');
      }
      return connectChannelCallback(validNetwork, {
        ...(workspaceId ? { workspaceId } : {}),
        projectId,
        code,
        state,
        deviceId,
      });
    },
    onSuccess: async (page) => {
      const resolvedWorkspace = resolveWorkspaceId(workspaceId, page);
      if (resolvedWorkspace) {
        await queryClient.invalidateQueries({ queryKey: queryKeys.channels.all(resolvedWorkspace) });
      }
    },
  });

  const resolvedWorkspaceId = resolveWorkspaceId(workspaceId, mutation.data);

  useEffect(() => {
    if (started.current || !validNetwork || !code || !state) {
      return;
    }
    started.current = true;
    mutation.mutate();
    // eslint-disable-next-line react-hooks/exhaustive-deps -- run once on mount with callback params
  }, [validNetwork, code, state]);

  const backHref = resolvedWorkspaceId ? `/app/${resolvedWorkspaceId}/channels` : '/login';

  if (oauthError) {
    return (
      <EmptyState
        icon={<XCircle className="h-6 w-6" aria-hidden />}
        title={t('callback.errorTitle')}
        description={
          oauthError === 'access_denied'
            ? t('callback.accessDenied')
            : oauthErrorDescription || oauthError
        }
        action={
          <Button asChild>
            <Link href={backHref}>{t('callback.back')}</Link>
          </Button>
        }
      />
    );
  }

  if (!validNetwork) {
    return (
      <EmptyState
        icon={<XCircle className="h-6 w-6" aria-hidden />}
        title={t('callback.invalidNetwork')}
        description={t('callback.invalidNetworkBody')}
        action={
          <Button asChild>
            <Link href={backHref}>{t('callback.back')}</Link>
          </Button>
        }
      />
    );
  }

  if (!code || !state) {
    return (
      <EmptyState
        icon={<XCircle className="h-6 w-6" aria-hidden />}
        title={t('callback.missingParams')}
        description={t('callback.missingParamsBody')}
        action={
          <Button asChild>
            <Link href={backHref}>{t('callback.back')}</Link>
          </Button>
        }
      />
    );
  }

  if (mutation.isPending || (!mutation.isSuccess && !mutation.isError)) {
    return (
      <div className="mx-auto flex max-w-lg flex-col gap-3" data-testid="oauth-callback-loading">
        <Skeleton className="h-8 w-64" />
        <Skeleton className="h-20 w-full" />
        <p className="text-sm text-muted-foreground">{t('callback.loading')}</p>
      </div>
    );
  }

  if (mutation.isError) {
    const planLimit = isPlanLimitExceeded(mutation.error);
    return (
      <EmptyState
        icon={<XCircle className="h-6 w-6" aria-hidden />}
        title={planLimit ? t('planLimit.title') : t('callback.errorTitle')}
        description={planLimit ? t('planLimit.body') : getErrorMessage(mutation.error)}
        action={
          <div className="flex flex-wrap gap-2">
            <Button asChild>
              <Link href={backHref}>{t('callback.back')}</Link>
            </Button>
            {planLimit && resolvedWorkspaceId ? (
              <Button asChild variant="outline">
                <Link href={`/app/${resolvedWorkspaceId}/billing`}>{t('planLimit.upgrade')}</Link>
              </Button>
            ) : null}
          </div>
        }
      />
    );
  }

  return (
    <EmptyState
      icon={<CheckCircle2 className="h-6 w-6" aria-hidden />}
      title={t('callback.successTitle')}
      description={t('callback.successBody')}
      action={
        <Button
          type="button"
          onClick={() => router.push(`/app/${resolvedWorkspaceId}/channels`)}
          data-testid="oauth-callback-done"
        >
          {t('callback.back')}
        </Button>
      }
    />
  );
}
