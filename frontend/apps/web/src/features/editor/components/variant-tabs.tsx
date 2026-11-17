'use client';

import type { NetworkId } from '@posts/domain';
import { Badge, Tabs, TabsList, TabsTrigger } from '@posts/ui';
import { useTranslations } from 'next-intl';

type VariantTabsProps = {
  networks: NetworkId[];
  active: 'base' | NetworkId;
  overridden: Partial<Record<NetworkId, boolean>>;
  onChange: (value: 'base' | NetworkId) => void;
};

const LABELS: Record<NetworkId, string> = {
  vk: 'VK',
  tg: 'Telegram',
  ok: 'OK',
  max: 'Max',
  rutube: 'Rutube',
};

export function VariantTabs({ networks, active, overridden, onChange }: VariantTabsProps) {
  const t = useTranslations('editor.variants');

  return (
    <Tabs
      value={active}
      onValueChange={(value) => onChange(value as 'base' | NetworkId)}
      className="w-full"
    >
      <TabsList className="flex h-auto w-full flex-wrap justify-start gap-1">
        <TabsTrigger value="base">{t('base')}</TabsTrigger>
        {networks.map((network) => (
          <TabsTrigger key={network} value={network} className="gap-1">
            {LABELS[network]}
            {overridden[network] ? (
              <Badge variant="scheduled" className="ml-1 px-1.5 py-0 text-[10px]">
                {t('overridden')}
              </Badge>
            ) : null}
          </TabsTrigger>
        ))}
      </TabsList>
    </Tabs>
  );
}
