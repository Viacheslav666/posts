'use client';

import { Button, NetworkIcon } from '@posts/ui';
import { useState } from 'react';
import { useTranslations } from 'next-intl';

import { toSocialNetwork } from '../model/network-map';
import { CONNECTABLE_NETWORKS, type ConnectableNetwork } from '../model/types';
import { ConnectWizardDialog } from './connect-wizard-dialog';

export type ConnectFirstChannelProps = {
  workspaceId: string;
  projectId?: string;
  /** Compact card grid for onboarding step 2. */
  variant?: 'page' | 'onboarding';
  onConnected?: () => void;
  onPlanLimit?: () => void;
};

/**
 * Reusable «подключить первый канал» step for onboarding (A1) and empty Channels state.
 */
export function ConnectFirstChannel({
  workspaceId,
  projectId,
  variant = 'page',
  onConnected,
  onPlanLimit,
}: ConnectFirstChannelProps) {
  const t = useTranslations('channels');
  const [network, setNetwork] = useState<ConnectableNetwork | null>(null);

  return (
    <section
      className={variant === 'onboarding' ? 'space-y-4' : 'space-y-6'}
      data-testid="connect-first-channel"
      aria-label={t('onboarding.label')}
    >
      <div className="space-y-1">
        <h2 className="text-lg font-semibold text-foreground">{t('onboarding.title')}</h2>
        <p className="text-sm text-muted-foreground">{t('onboarding.body')}</p>
      </div>

      <ul className="grid gap-3 sm:grid-cols-2">
        {CONNECTABLE_NETWORKS.map((item) => {
          const icon = toSocialNetwork(item);
          return (
            <li key={item}>
              <Button
                type="button"
                variant="outline"
                className="h-auto w-full justify-start gap-3 px-4 py-3"
                onClick={() => setNetwork(item)}
                data-testid={`connect-network-${item}`}
              >
                {icon ? <NetworkIcon network={icon} /> : null}
                <span className="flex flex-col items-start gap-0.5 text-left">
                  <span className="font-medium">{t(`networks.${item}`)}</span>
                  <span className="text-xs font-normal text-muted-foreground">
                    {t(`connect.${item}.short`)}
                  </span>
                </span>
              </Button>
            </li>
          );
        })}
      </ul>

      <ConnectWizardDialog
        network={network}
        workspaceId={workspaceId}
        projectId={projectId}
        open={network !== null}
        onOpenChange={(open) => {
          if (!open) setNetwork(null);
        }}
        onConnected={onConnected}
        onPlanLimit={onPlanLimit}
      />
    </section>
  );
}
