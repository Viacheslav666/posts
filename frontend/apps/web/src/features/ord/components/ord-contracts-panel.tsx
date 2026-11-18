'use client';

import { Badge, EmptyState, Skeleton } from '@posts/ui';
import { FileText } from 'lucide-react';
import { useTranslations } from 'next-intl';

import { useOrdContracts } from '../hooks/use-ord-queries';

type OrdContractsPanelProps = {
  workspaceId: string;
};

export function OrdContractsPanel({ workspaceId }: OrdContractsPanelProps) {
  const t = useTranslations('ord');
  const contractsQuery = useOrdContracts(workspaceId);

  if (contractsQuery.isLoading) {
    return <Skeleton className="h-24 w-full" data-testid="ord-contracts" />;
  }

  const items = contractsQuery.data?.items ?? [];

  if (items.length === 0) {
    return (
      <div data-testid="ord-contracts">
        <EmptyState
          icon={<FileText aria-hidden />}
          title={t('contracts.emptyTitle')}
          description={t('contracts.emptyBody')}
        />
      </div>
    );
  }

  return (
    <ul
      className="divide-y divide-border rounded-md border border-border"
      data-testid="ord-contracts"
    >
      {items.map((contract) => (
        <li key={contract.id} className="space-y-1 px-4 py-3">
          <div className="flex flex-wrap items-center gap-2">
            <p className="font-medium text-foreground">{contract.serial ?? contract.externalId}</p>
            {contract.provider ? (
              <Badge variant="secondary">{t(`providers.${contract.provider}`)}</Badge>
            ) : null}
            {contract.status ? <Badge variant="outline">{contract.status}</Badge> : null}
          </div>
          <p className="text-sm text-muted-foreground">
            {contract.dateStart} — {contract.dateEnd}
          </p>
        </li>
      ))}
    </ul>
  );
}