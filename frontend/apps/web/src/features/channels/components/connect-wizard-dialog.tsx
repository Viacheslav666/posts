'use client';

import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
  NetworkIcon,
} from '@posts/ui';
import { useTranslations } from 'next-intl';

import { toSocialNetwork } from '../model/network-map';
import type { ConnectableNetwork } from '../model/types';
import { BotConnectWizard } from './bot-connect-wizard';
import { OauthConnectWizard } from './oauth-connect-wizard';

type ConnectWizardDialogProps = {
  network: ConnectableNetwork | null;
  workspaceId: string;
  projectId?: string;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onConnected?: () => void;
  onPlanLimit?: () => void;
};

export function ConnectWizardDialog({
  network,
  workspaceId,
  projectId,
  open,
  onOpenChange,
  onConnected,
  onPlanLimit,
}: ConnectWizardDialogProps) {
  const t = useTranslations('channels');
  const icon = network ? toSocialNetwork(network) : null;

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-lg" data-testid="connect-wizard-dialog">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            {icon ? <NetworkIcon network={icon} size="sm" /> : null}
            {network ? t(`networks.${network}`) : t('connect.title')}
          </DialogTitle>
          <DialogDescription>
            {network ? t(`connect.${network}.description`) : t('connect.description')}
          </DialogDescription>
        </DialogHeader>

        {network === 'vk' || network === 'ok' ? (
          <OauthConnectWizard
            network={network}
            workspaceId={workspaceId}
            projectId={projectId}
            onPlanLimit={onPlanLimit}
          />
        ) : null}

        {network === 'tg' || network === 'max' ? (
          <BotConnectWizard
            network={network}
            workspaceId={workspaceId}
            projectId={projectId}
            onConnected={() => {
              onConnected?.();
              onOpenChange(false);
            }}
            onPlanLimit={onPlanLimit}
          />
        ) : null}
      </DialogContent>
    </Dialog>
  );
}
