import { Button } from '@posts/ui';
import { getTranslations } from 'next-intl/server';
import Link from 'next/link';

export default async function HomePage() {
  const t = await getTranslations('home');
  const common = await getTranslations('common');

  return (
    <main id="main-content" className="mx-auto flex min-h-screen w-full max-w-5xl flex-col justify-center px-6 py-16">
      <p className="font-display text-5xl font-bold tracking-tight text-foreground md:text-7xl">{common('brand')}</p>
      <h1 className="mt-6 max-w-2xl text-2xl font-medium text-foreground md:text-3xl">{t('headline')}</h1>
      <p className="mt-4 max-w-xl text-base text-muted-foreground md:text-lg">{t('subtitle')}</p>
      <div className="mt-8 flex flex-wrap gap-3">
        <Button asChild>
          <Link href="/app/demo">{t('cta')}</Link>
        </Button>
        <Link
          className="inline-flex items-center text-sm font-medium text-primary underline-offset-4 hover:underline"
          href="/health"
        >
          {t('health')}
        </Link>
      </div>
    </main>
  );
}
