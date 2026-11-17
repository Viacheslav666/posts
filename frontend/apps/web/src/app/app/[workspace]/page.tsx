import { Badge, Button, EmptyState, NetworkIcon } from '@posts/ui';
import { CalendarDays } from 'lucide-react';
import { getTranslations } from 'next-intl/server';

type WorkspacePageProps = {
  params: Promise<{ workspace: string }>;
};

export default async function WorkspaceDashboardPage({ params }: WorkspacePageProps) {
  const { workspace } = await params;
  const t = await getTranslations('app');
  const common = await getTranslations('common');

  return (
    <div className="mx-auto flex w-full max-w-5xl flex-col gap-6">
      <div className="space-y-2">
        <div className="flex flex-wrap items-center gap-2">
          <h1 className="font-display text-3xl font-bold tracking-tight text-foreground">{t('welcomeTitle')}</h1>
          <Badge variant="scheduled">{workspace}</Badge>
        </div>
        <p className="max-w-2xl text-muted-foreground">{t('welcomeBody')}</p>
      </div>

      <div className="flex flex-wrap items-center gap-2" aria-label="Networks">
        <NetworkIcon network="vk" />
        <NetworkIcon network="telegram" />
        <NetworkIcon network="ok" />
        <NetworkIcon network="max" />
        <NetworkIcon network="rutube" />
      </div>

      <EmptyState
        icon={<CalendarDays aria-hidden />}
        title={t('emptyTitle')}
        description={t('emptyDescription')}
        action={<Button>{common('createPost')}</Button>}
      />
    </div>
  );
}
