'use client';

import { EmptyState, Tabs, TabsContent, TabsList, TabsTrigger } from '@posts/ui';
import { ShieldOff } from 'lucide-react';
import { useTranslations } from 'next-intl';

import { useCan } from '@/shared/hooks/use-can';

import { OrdConnectionForm } from './ord-connection-form';
import { OrdContractorsPanel } from './ord-contractors-panel';
import { OrdContractsPanel } from './ord-contracts-panel';
import { OrdReportsPanel } from './ord-reports-panel';

type OrdSettingsPanelProps = {
  workspaceId: string;
};

export function OrdSettingsPanel({ workspaceId }: OrdSettingsPanelProps) {
  const t = useTranslations('ord');
  const canView = useCan('ord.view', workspaceId);

  if (!canView) {
    return (
      <EmptyState
        icon={<ShieldOff aria-hidden />}
        title={t('forbiddenTitle')}
        description={t('forbiddenBody')}
      />
    );
  }

  return (
    <div
      className="mx-auto flex w-full max-w-6xl flex-col gap-6"
      data-testid="ord-settings-page"
    >
      <div>
        <h1 className="text-2xl font-semibold text-foreground">{t('title')}</h1>
        <p className="mt-1 text-sm text-muted-foreground">{t('description')}</p>
      </div>

      <Tabs defaultValue="connection">
        <TabsList className="flex h-auto w-full flex-wrap justify-start gap-1">
          <TabsTrigger value="connection">{t('tabs.connection')}</TabsTrigger>
          <TabsTrigger value="contractors">{t('tabs.contractors')}</TabsTrigger>
          <TabsTrigger value="contracts">{t('tabs.contracts')}</TabsTrigger>
          <TabsTrigger value="reports" data-testid="ord-tab-reports">
            {t('tabs.reports')}
          </TabsTrigger>
        </TabsList>

        <TabsContent value="connection" className="pt-4">
          <OrdConnectionForm workspaceId={workspaceId} />
        </TabsContent>

        <TabsContent value="contractors" className="pt-4">
          <OrdContractorsPanel workspaceId={workspaceId} />
        </TabsContent>

        <TabsContent value="contracts" className="pt-4">
          <OrdContractsPanel workspaceId={workspaceId} />
        </TabsContent>

        <TabsContent value="reports" className="pt-4">
          <OrdReportsPanel workspaceId={workspaceId} />
        </TabsContent>
      </Tabs>
    </div>
  );
}