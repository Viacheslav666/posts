'use client';

import type { Channel } from '@posts/api-client';
import { Button, toast } from '@posts/ui';
import { useMutation } from '@tanstack/react-query';
import { useTranslations } from 'next-intl';
import { useState } from 'react';

import { CapabilityNotice } from '@/features/automations-common';
import { getErrorMessage } from '@/shared/lib/errors';

import { scanAntispamRule } from '../api/antispam-api';
import { isKnownScanStatus } from '../model/reasons';

type ScanPanelProps = {
  ruleId: string;
  channel?: Channel;
  canModerate: boolean;
};

export function ScanPanel({ ruleId, channel, canModerate }: ScanPanelProps) {
  const t = useTranslations('automations.antispam.scan');
  const [result, setResult] = useState<{ status?: string; message?: string } | null>(null);
  const network = channel?.network;
  const isVk = network === 'vk';
  const isTgOrMax = network === 'tg' || network === 'max';

  const scanMutation = useMutation({
    mutationFn: () => scanAntispamRule(ruleId),
    onSuccess: (data) => {
      setResult({ status: data.status, message: data.message });
      const status = data.status;
      toast({
        title:
          status === 'done'
            ? t('statuses.done')
            : status === 'failed'
              ? t('statuses.failed')
              : t('statuses.queued'),
      });
    },
    onError: (error) => {
      toast({
        variant: 'destructive',
        title: t('errorTitle'),
        description: getErrorMessage(error),
      });
    },
  });

  return (
    <div className="space-y-4" data-testid="antispam-scan-panel">
      <div>
        <h3 className="text-base font-semibold">{t('title')}</h3>
        <p className="mt-1 text-sm text-muted-foreground">{t('description')}</p>
      </div>

      {isVk ? (
        <CapabilityNotice variant="vk_user_wall" body={t('vkCapabilityBody')} />
      ) : null}

      {!isVk && !isTgOrMax ? (
        <p className="text-sm text-muted-foreground" data-testid="antispam-scan-unavailable">
          {t('tgDisabledBody')}
        </p>
      ) : null}

      {isTgOrMax ? (
        <div
          role="status"
          className="rounded-md border border-border bg-muted/40 px-3 py-2 text-sm"
          data-testid="antispam-scan-tg-disabled"
        >
          <p className="font-medium">{t('tgDisabledTitle')}</p>
          <p className="mt-1 text-muted-foreground">{t('tgDisabledBody')}</p>
        </div>
      ) : null}

      {canModerate && isVk ? (
        <Button
          type="button"
          disabled={scanMutation.isPending}
          onClick={() => scanMutation.mutate()}
          data-testid="antispam-scan-submit"
        >
          {scanMutation.isPending ? t('running') : t('submit')}
        </Button>
      ) : null}

      {!canModerate ? (
        <p className="text-sm text-muted-foreground">{t('forbidden')}</p>
      ) : null}

      {result ? (
        <div
          role="status"
          className="rounded-md border border-border px-3 py-2 text-sm"
          data-testid="antispam-scan-result"
        >
          <p data-testid="antispam-scan-status">
            {isKnownScanStatus(result.status) ? t(`statuses.${result.status}`) : (result.status ?? t('statuses.queued'))}
          </p>
          {result.message ? <p className="mt-1 text-muted-foreground">{result.message}</p> : null}
        </div>
      ) : null}
    </div>
  );
}
