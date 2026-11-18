'use client';

import { Badge } from '@posts/ui';
import { useTranslations } from 'next-intl';

import { ORD_REPORT_STATUS_LABELS_RU } from '../model/report-status-labels';

const VARIANT: Record<string, 'default' | 'scheduled' | 'draft' | 'failed' | 'published'> = {
  draft: 'draft',
  submitted: 'published',
  error: 'failed',
  running: 'scheduled',
  succeeded: 'published',
};

const KNOWN = Object.keys(ORD_REPORT_STATUS_LABELS_RU);

type OrdReportStatusBadgeProps = {
  status?: string | null;
};

export function OrdReportStatusBadge({ status }: OrdReportStatusBadgeProps) {
  const t = useTranslations('ord.reports.status');
  const key = status && KNOWN.includes(status) ? status : 'draft';
  return (
    <Badge variant={VARIANT[key] ?? 'default'} data-testid="ord-report-status">
      {t(key)}
    </Badge>
  );
}
