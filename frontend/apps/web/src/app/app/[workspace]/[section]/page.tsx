import { Badge, EmptyState } from '@posts/ui';
import { Construction } from 'lucide-react';
import { getTranslations } from 'next-intl/server';

type SectionPageProps = {
  params: Promise<{ workspace: string; section: string }>;
};

export default async function WorkspaceSectionPage({ params }: SectionPageProps) {
  const { workspace, section } = await params;
  const t = await getTranslations('app');

  return (
    <div className="mx-auto flex w-full max-w-3xl flex-col gap-4">
      <div className="flex flex-wrap items-center gap-2">
        <h1 className="text-2xl font-semibold capitalize text-foreground">{section}</h1>
        <Badge variant="draft">{workspace}</Badge>
      </div>
      <EmptyState
        icon={<Construction aria-hidden />}
        title={t('emptyTitle')}
        description={t('emptyDescription')}
      />
    </div>
  );
}
