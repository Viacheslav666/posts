'use client';

import { Button, Input, toast } from '@posts/ui';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useTranslations } from 'next-intl';

import { queryKeys } from '@/shared/api-client';
import {
  getErrorMessage,
  isInsufficientPermissions,
  isPlanLimitExceeded,
} from '@/shared/lib/errors';

import { confirmChannelConnect, startChannelConnect } from '../api/channels-api';
import type { ConnectableNetwork } from '../model/types';

type BotConnectWizardProps = {
  network: Extract<ConnectableNetwork, 'tg' | 'max'>;
  workspaceId: string;
  projectId?: string;
  onConnected?: () => void;
  onPlanLimit?: () => void;
};

export function BotConnectWizard({
  network,
  workspaceId,
  projectId,
  onConnected,
  onPlanLimit,
}: BotConnectWizardProps) {
  const t = useTranslations('channels');
  const queryClient = useQueryClient();
  const [username, setUsername] = useState('');
  const [botInviteUrl, setBotInviteUrl] = useState<string | null>(null);
  const [step, setStep] = useState<'intro' | 'confirm'>('intro');

  const startMutation = useMutation({
    mutationFn: () => startChannelConnect(network, workspaceId, projectId),
    onSuccess: (data) => {
      setBotInviteUrl(data.botInviteUrl ?? null);
      setStep('confirm');
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

  const confirmMutation = useMutation({
    mutationFn: () =>
      confirmChannelConnect(network, {
        workspaceId,
        projectId,
        username: username.trim(),
      }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: queryKeys.channels.all(workspaceId) });
      toast({ title: t('connect.successTitle') });
      onConnected?.();
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
        description: isInsufficientPermissions(error)
          ? t(`connect.${network}.permissionsHint`)
          : getErrorMessage(error),
      });
    },
  });

  return (
    <div className="flex flex-col gap-4" data-testid={`bot-wizard-${network}`}>
      <ol className="list-decimal space-y-2 pl-5 text-sm text-muted-foreground">
        <li>{t(`connect.${network}.stepAddBot`)}</li>
        <li>{t(`connect.${network}.stepRights`)}</li>
        <li>{t(`connect.${network}.stepUsername`)}</li>
      </ol>

      {step === 'intro' ? (
        <Button
          type="button"
          onClick={() => startMutation.mutate()}
          disabled={startMutation.isPending}
          data-testid={`bot-wizard-start-${network}`}
        >
          {startMutation.isPending ? t('connect.starting') : t('connect.start')}
        </Button>
      ) : (
        <div className="flex flex-col gap-3">
          {botInviteUrl ? (
            <p className="text-sm text-muted-foreground">
              {t('connect.botInviteHint')}{' '}
              <a
                href={botInviteUrl}
                className="font-medium text-primary underline-offset-4 hover:underline"
              >
                {t('connect.openBotInvite')}
              </a>
            </p>
          ) : null}
          <label className="text-sm font-medium" htmlFor={`bot-username-${network}`}>
            {t('connect.usernameLabel')}
          </label>
          <Input
            id={`bot-username-${network}`}
            value={username}
            onChange={(event) => setUsername(event.target.value)}
            placeholder={t('connect.usernamePlaceholder')}
            data-testid={`bot-username-${network}`}
            autoComplete="off"
          />
          <Button
            type="button"
            onClick={() => confirmMutation.mutate()}
            disabled={confirmMutation.isPending || username.trim().length < 2}
            data-testid={`bot-wizard-confirm-${network}`}
          >
            {confirmMutation.isPending ? t('connect.confirming') : t('connect.confirm')}
          </Button>
        </div>
      )}
    </div>
  );
}
