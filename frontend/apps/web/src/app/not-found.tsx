import { Button } from '@posts/ui';
import { getTranslations } from 'next-intl/server';
import Link from 'next/link';

export default async function NotFoundPage() {
  const t = await getTranslations('errors');

  return (
    <main id="main-content" className="mx-auto flex min-h-[60vh] max-w-lg flex-col justify-center gap-4 px-6 py-16">
      <h1 className="font-display text-3xl font-bold text-foreground">{t('notFoundTitle')}</h1>
      <p className="text-muted-foreground">{t('notFoundDescription')}</p>
      <div>
        <Button asChild>
          <Link href="/">Posts</Link>
        </Button>
      </div>
    </main>
  );
}
