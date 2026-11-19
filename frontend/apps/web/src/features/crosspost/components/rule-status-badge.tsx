'use client';

import { Badge } from '@posts/ui';
import { useTranslations } from 'next-intl';

import type { RuleStatus } from '../model/types';

const VARIANT: Record<RuleStatus, 'default' | 'scheduled' | 'draft' | 'failed' | 'published'> = {
  draft: 'draft',
  active: 'published',
  paused: 'scheduled',
  auto_paused: 'failed',
};

type RuleStatusBadgeProps = {
  status: RuleStatus | string | undefined;
};

const KNOWN: RuleStatus[] = ['draft', 'active', 'paused', 'auto_paused'];

export function RuleStatusBadge({ status }: RuleStatusBadgeProps) {
  const t = useTranslations('automations.crosspost.status');
  const key = (KNOWN.includes(status as RuleStatus) ? status : 'draft') as RuleStatus;
  return (
    <Badge variant={VARIANT[key]} data-testid="crosspost-rule-status">
      {t(key)}
    </Badge>
  );
}
