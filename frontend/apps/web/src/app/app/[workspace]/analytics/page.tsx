import { Suspense } from 'react';

import { AnalyticsPage } from '@/features/analytics/components/analytics-page';

type AnalyticsRouteProps = {
  params: Promise<{ workspace: string }>;
};

export default async function AnalyticsRoute({ params }: AnalyticsRouteProps) {
  const { workspace } = await params;
  return (
    <Suspense fallback={<div className="p-6 text-sm text-muted-foreground">Загрузка…</div>}>
      <AnalyticsPage workspaceId={workspace} />
    </Suspense>
  );
}
