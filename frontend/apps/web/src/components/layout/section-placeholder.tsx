import { EmptyState } from '@posts/ui';
import { Construction } from 'lucide-react';

type SectionPlaceholderProps = {
  title: string;
};

export function SectionPlaceholder({ title }: SectionPlaceholderProps) {
  return (
    <div className="mx-auto flex w-full max-w-3xl flex-col gap-4">
      <h1 className="text-2xl font-semibold text-foreground">{title}</h1>
      <EmptyState
        icon={<Construction aria-hidden />}
        title="Раздел в разработке"
        description="Этот раздел скоро появится. Данных здесь пока нет."
      />
    </div>
  );
}
