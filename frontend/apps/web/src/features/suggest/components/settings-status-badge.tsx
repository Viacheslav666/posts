'use client';

import { Badge } from '@posts/ui';
import { useTranslations } from 'next-intl';

import type { SettingsStatus } from '../model/types';

const VARIANT: Record<SettingsStatus, 'default' | 'scheduled' | 'draft' | 'failed' | 'published'> = {
  draft: 'draft',
  active: 'published',
  paused: 'scheduled',
  auto_paused: 'failed',
};

const KNOWN: SettingsStatus[] = ['draft', 'active', 'paused', 'auto_paused'];

type SettingsStatusBadgeProps = {
  status: SettingsStatus | string | undefined;
};

export function SettingsStatusBadge({ status }: SettingsStatusBadgeProps) {
  const t = useTranslations('automations.suggest.status');
  const key = (KNOWN.includes(status as SettingsStatus) ? status : 'draft') as SettingsStatus;
  return <Badge variant={VARIANT[key]}>{t(key)}</Badge>;
}
