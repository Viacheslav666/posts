'use client';

import { Button, toast } from '@posts/ui';
import { useMutation } from '@tanstack/react-query';
import { useTranslations } from 'next-intl';

import {
  getErrorMessage,
  isPlanLimitExceeded,
} from '@/shared/lib/errors';

import { startChannelConnect } from '../api/channels-api';
import type { ConnectableNetwork } from '../model/types';

type OauthConnectWizardProps = {
  network: Extract<ConnectableNetwork, 'vk' | 'ok'>;
  workspaceId: string;
  projectId?: string;
  onPlanLimit?: () => void;
};

export function OauthConnectWizard({
  network,
  workspaceId,
  projectId,
  onPlanLimit,
}: OauthConnectWizardProps) {
  const t = useTranslations('channels');

  const startMutation = useMutation({
    mutationFn: () => startChannelConnect(network, workspaceId, projectId),
    onSuccess: (data) => {
      if (!data.authorizationUrl) {
        toast({
          variant: 'destructive',
          title: t('connect.errorTitle'),
          description: t('connect.missingAuthUrl'),
        });
        return;
      }
      // Same-origin callback path from MSW — no external OAuth host.
      window.location.assign(data.authorizationUrl);
    },
    onError: (error) => {
      if (isPlanLimitExceeded(error)) {
        onPlanLimit?.();
        toast({
          variant: 'destructive',
          title: t('planLimit.title'),
          description: t('planLimit.body'),
        });
        return;
      }
      toast({
        variant: 'destructive',
        title: t('connect.errorTitle'),
        description: getErrorMessage(error),
      });
    },
  });

  return (
    <div className="flex flex-col gap-4" data-testid={`oauth-wizard-${network}`}>
      <ol className="list-decimal space-y-2 pl-5 text-sm text-muted-foreground">
        <li>{t(`connect.${network}.stepOauth`)}</li>
        <li>{t(`connect.${network}.stepPick`)}</li>
        <li>{t(`connect.${network}.stepDone`)}</li>
      </ol>
      <Button
        type="button"
        onClick={() => startMutation.mutate()}
        disabled={startMutation.isPending}
        data-testid={`oauth-wizard-start-${network}`}
      >
        {startMutation.isPending ? t('connect.starting') : t(`connect.${network}.cta`)}
      </Button>
    </div>
  );
}
